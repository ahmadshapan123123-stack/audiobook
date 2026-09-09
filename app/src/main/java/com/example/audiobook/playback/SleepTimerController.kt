package com.example.audiobook.playback

import android.util.Log
import com.example.audiobook.data.room.dao.ListeningSessionDao
import com.example.audiobook.data.room.entity.ListeningSessionEntity
import com.example.audiobook.data.room.entity.SessionEndReason
import com.example.audiobook.data.room.entity.SessionState
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** طول نافذة التحذير: آخر 3 دقائق من المؤقت. */
const val SLEEP_WARNING_WINDOW_MS = 3 * 60_000L

/** نقاط النبضات الست بالضبط (بالثواني المتبقية): 3:00، 2:30، 2:00، 1:30، 1:00، 0:30. */
val SLEEP_BEEP_REMAINING_MS = longArrayOf(180_000L, 150_000L, 120_000L, 90_000L, 60_000L, 30_000L)

/** مستوى النبضة الهادئة: خفض الصوت مؤقتًا إلى 25% ثم إعادته (Duck وليس Interrupt). */
const val SLEEP_DUCK_VOLUME_RATIO = 0.25f

/** مدة إبقاء الصوت منخفضًا في كل نبضة قبل إعادته. */
const val SLEEP_DUCK_RESTORE_MS = 300L

/** آخر 2.5 ثانية: تدرّج هبوطي حقيقي (Fade Out) لصوت player.volume. */
const val SLEEP_FADE_OUT_MS = 2_500L

/** قاعدة التمديد التلقائي: +15 دقيقة بالضبط (وليس Reset وليس +30). */
const val SLEEP_AUTO_EXTEND_MINUTES = 15

/** رسالة التمديد التلقائي. */
const val SLEEP_AUTO_EXTEND_MESSAGE = "تم تمديد مؤقت النوم تلقائيًا"

enum class SleepTimerPhase { IDLE, RUNNING, WARNING_WINDOW, FADING_OUT, STOPPED }

/**
 * الحالة المكشوفة للـUI مع رابط صريح ومنفصل [isExtendWindowVisible]:
 * false قبل دخول نافذة الـ3 دقائق، true داخلها (وحتى اللحظات الأخيرة المتلاشية).
 */
data class SleepTimerUiState(
    val phase: SleepTimerPhase = SleepTimerPhase.IDLE,
    val remainingMs: Long? = null,
    val isExtendWindowVisible: Boolean = false
)

/**
 * "Active Interaction" الرسمي — قائمتان نهائيتان تطابقان حرفيًا الـSpec:
 * التفاعل الفعلي (يُفعّل Auto-Extend): Seek، Play/Pause صريح، ±15s،
 * Previous/Next Chapter، إضافة Bookmark/Note، إنشاء Chapter، تغيير السرعة،
 * التمديد اليدوي. غير الفعلي: فتح الشاشة، بقاء الشاشة مضاءة، حركة الجهاز،
 * وصول إشعار آخر، مجرد Foreground.
 */
sealed class ActiveInteraction {
    object Seek : ActiveInteraction()
    object PlayPause : ActiveInteraction()
    object SkipForward15 : ActiveInteraction()
    object SkipBackward15 : ActiveInteraction()
    object PreviousChapter : ActiveInteraction()
    object NextChapter : ActiveInteraction()
    object AddBookmark : ActiveInteraction()
    object AddNote : ActiveInteraction()
    object CreateChapter : ActiveInteraction()
    object ChangeSpeed : ActiveInteraction()
    object ManualExtend : ActiveInteraction()

    object ScreenOpen : ActiveInteraction()
    object ScreenStaysOn : ActiveInteraction()
    object DeviceMoved : ActiveInteraction()
    object OtherNotification : ActiveInteraction()
    object AppInForeground : ActiveInteraction()

    val isInteraction: Boolean get() = this in ACTIVE_INTERACTIONS

    companion object {
        val ACTIVE_INTERACTIONS: Set<ActiveInteraction> = setOf(
            Seek, PlayPause, SkipForward15, SkipBackward15,
            PreviousChapter, NextChapter,
            AddBookmark, AddNote, CreateChapter, ChangeSpeed, ManualExtend
        )
    }
}

/**
 * [R3] مؤقت النوم الذكي — طبقة مستقلة تتفاعل مع الصوت عبر [PlaybackController]
 * فقط (وليس ExoPlayer مباشرة). آلة حالات صريحة:
 * IDLE → RUNNING → WARNING_WINDOW(≤3min) → FADING_OUT → STOPPED.
 *
 * كل نقاط التوقيت تُشتق من [SleepTimerClock] القابل للحقن، فلا توجد أي
 * كمون real-time داخل القرارات؛ حلقة الجدولة تستيقظ فقط عند اللحظة التالية.
 */
class SleepTimerController @Inject constructor(
    private val clock: SleepTimerClock,
    private val playback: PlaybackController,
    private val sessionDao: ListeningSessionDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private var phase = SleepTimerPhase.IDLE
    private var deadlineMs = 0L
    private var timerStartedMs = 0L
    private var originalDurationMs = 0L
    private var beepThresholds = longArrayOf()
    private val firedBeeps = mutableSetOf<Long>()
    private var duckBaseVolume = 1f
    private var pendingDuckRestoreAtMs = 0L
    private val stopHandled = AtomicBoolean(false)

    private val _uiState = MutableStateFlow(SleepTimerUiState())
    val uiState: StateFlow<SleepTimerUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun start(minutes: Int) {
        if (minutes <= 0) {
            cancel()
            return
        }
        job?.cancel()
        val now = clock.nowMillis()
        deadlineMs = now + minutes * 60_000L
        timerStartedMs = now
        originalDurationMs = minutes * 60_000L
        firedBeeps.clear()
        pendingDuckRestoreAtMs = 0L
        armBeeps()
        stopHandled.set(false)
        setPhase(SleepTimerPhase.RUNNING)
        job = scope.launch { runLoop() }
    }

    fun cancel() {
        job?.cancel()
        if (phase == SleepTimerPhase.FADING_OUT) playback.setVolume(duckBaseVolume)
        deadlineMs = 0L
        firedBeeps.clear()
        pendingDuckRestoreAtMs = 0L
        stopHandled.set(false)
        setPhase(SleepTimerPhase.IDLE)
    }

    /** تمديد يدوي (+15/+30/+60) من الإشعار/شاشة القفل أو من النافذة داخل التطبيق. */
    fun extendBy(minutes: Int) {
        if (minutes <= 0 || deadlineMs == 0L) return
        deadlineMs += minutes * 60_000L
        firedBeeps.clear()
        armBeeps()
        val remaining = (deadlineMs - clock.nowMillis()).coerceAtLeast(0L)
        setPhase(if (remaining > SLEEP_WARNING_WINDOW_MS) SleepTimerPhase.RUNNING else phase)
    }

    /**
     * قاعدة Active Interaction: أي تفاعل فعلي داخل نافذة التحذير يضيف
     * +15 دقيقة بالضبط ويصدر رسالة التمديد. غير الفعلي لا يفعل شيئًا.
     */
    fun onActiveInteraction(interaction: ActiveInteraction) {
        if (!interaction.isInteraction) return
        if (phase != SleepTimerPhase.WARNING_WINDOW && phase != SleepTimerPhase.FADING_OUT) return
        if (deadlineMs == 0L) return
        extendBy(SLEEP_AUTO_EXTEND_MINUTES)
        _messages.tryEmit(SLEEP_AUTO_EXTEND_MESSAGE)
    }

    private suspend fun runLoop() {
        while (true) {
            tickClock()
            val nextWake = nextEventMs() ?: return
            val gap = nextWake - clock.nowMillis()
            if (gap <= 0L) continue
            delay(gap.coerceAtMost(1_000L))
        }
    }

    private fun nextEventMs(): Long? {
        if (deadlineMs == 0L) return null
        val now = clock.nowMillis()
        val beep = beepThresholds.firstOrNull { it > now }
        val restore = if (pendingDuckRestoreAtMs > 0L) pendingDuckRestoreAtMs else deadlineMs
        return minOf(deadlineMs, beep ?: deadlineMs, restore, now + 1_000L)
    }

    private fun armBeeps() {
        beepThresholds = SLEEP_BEEP_REMAINING_MS.map { deadlineMs - it }.toLongArray()
    }

    /**
     * معالجة لقطة من الزمن (تستدعيها الحلقة دوريًا، ويستدعيها الاختبار بالـFake Clock):
     * نبضات الـ3 دقائق، استعادة الصوت بعد كل نبضة، انتقالات الحالة، تدرّج Fade، والتوقف.
     */
    internal suspend fun tickClock() {
        val now = clock.nowMillis()
        if (deadlineMs == 0L) return
        val remainingMs = deadlineMs - now

        if (remainingMs <= SLEEP_WARNING_WINDOW_MS) {
            beepThresholds.forEach { instant ->
                if (instant >= timerStartedMs && now >= instant && !firedBeeps.contains(instant)) {
                    firedBeeps += instant
                    duckOnce()
                }
            }
        }
        if (pendingDuckRestoreAtMs > 0L && now >= pendingDuckRestoreAtMs) {
            playback.setVolume(duckBaseVolume)
            pendingDuckRestoreAtMs = 0L
        }

        val nextPhase = when {
            remainingMs <= 0L -> SleepTimerPhase.STOPPED
            remainingMs <= SLEEP_FADE_OUT_MS -> SleepTimerPhase.FADING_OUT
            remainingMs <= SLEEP_WARNING_WINDOW_MS -> SleepTimerPhase.WARNING_WINDOW
            else -> SleepTimerPhase.RUNNING
        }
        if (nextPhase != phase) setPhase(nextPhase)
        if (phase == SleepTimerPhase.FADING_OUT) {
            val ratio = (remainingMs.toFloat() / SLEEP_FADE_OUT_MS).coerceIn(0f, 1f)
            playback.setVolume(duckBaseVolume * ratio)
        }
        if (nextPhase == SleepTimerPhase.STOPPED && remainingMs <= 0L) {
            finishStop()
        } else {
            publishState()
        }
    }

    private fun duckOnce() {
        val base = playback.getVolume()
        duckBaseVolume = if (base > 0f) base else 1f
        playback.setVolume(duckBaseVolume * SLEEP_DUCK_VOLUME_RATIO)
        pendingDuckRestoreAtMs = clock.nowMillis() + SLEEP_DUCK_RESTORE_MS
    }

    /** توقف كامل عند الصفر + حفظ الموضع فورًا (عبر pause الحالية التي تحفظ) + تسجيل الجلسة. */
    private suspend fun finishStop() {
        if (!stopHandled.compareAndSet(false, true)) return
        playback.setVolume(0f)
        playback.pause()
        recordSession(endedAtMs = deadlineMs)
        playback.setVolume(duckBaseVolume)
        deadlineMs = 0L
        firedBeeps.clear()
        pendingDuckRestoreAtMs = 0L
        phase = SleepTimerPhase.STOPPED
        _uiState.value = SleepTimerUiState(phase = SleepTimerPhase.STOPPED, remainingMs = 0L, isExtendWindowVisible = false)
    }

    private suspend fun recordSession(endedAtMs: Long) {
        val editionId = playback.state.value.editionId ?: return
        runCatching {
            sessionDao.insert(
                ListeningSessionEntity(
                    id = UUID.randomUUID(),
                    editionId = editionId,
                    startedAt = endedAtMs - originalDurationMs,
                    endedAt = endedAtMs,
                    durationListenedMs = playback.state.value.positionMs,
                    endReason = SessionEndReason.SLEEP_TIMER,
                    sessionState = SessionState.COMPLETED
                )
            )
        }.onFailure { Log.w("SleepTimer", "تعذر تسجيل جلسة انتهاء المؤقت", it) }
    }

    private fun setPhase(next: SleepTimerPhase) {
        phase = next
        publishState()
    }

    private fun publishState() {
        val remaining = if (deadlineMs == 0L) null else (deadlineMs - clock.nowMillis()).coerceAtLeast(0L)
        _uiState.value = SleepTimerUiState(
            phase = phase,
            remainingMs = remaining,
            isExtendWindowVisible = phase == SleepTimerPhase.WARNING_WINDOW || phase == SleepTimerPhase.FADING_OUT
        )
    }
}
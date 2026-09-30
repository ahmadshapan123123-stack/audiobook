package com.example.audiobook.playback

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.dao.ListeningSessionDao
import com.example.audiobook.data.room.entity.ListeningSessionEntity
import com.example.audiobook.data.room.entity.SessionEndReason
import com.example.audiobook.data.room.entity.SessionState
import dagger.hilt.android.qualifiers.ApplicationContext
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

/**
 * FIX 4.5 — آخر 30 ثانية: تدرّج هبوطي حقيقي (Fade Out) لصوت player.volume
 * من 100% إلى 0%. كان 2.5 ثانية فقط فينقطع الصوت فجأةً فيُقرأ «توقفًا».
 */
const val SLEEP_FADE_OUT_MS = 30_000L

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
    val isExtendWindowVisible: Boolean = false,
    val totalDurationMs: Long? = null
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
    private val sessionDao: ListeningSessionDao,
    private val appSettings: AppSettings,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** يحمي حالة القرار من تنافس خيطي (حلقة الخلفية + استدعاءات الاختبار/الواجهة). */
    private val tickLock = Any()

    private var phase = SleepTimerPhase.IDLE
    private var deadlineMs = 0L
    private var timerStartedMs = 0L
    private var originalDurationMs = 0L
    /**
     * FIX 4.5/4.6: مستوى الصوت المرجعي للخبو — يُلتقط عند دخول FADING_OUT
     * من الصوت الفعلي (لا يُفترض 1f). نبضات الـDuck الست حُذفت نهائيًا:
     * كانت تُقرأ «صوتًا غريبًا» عند التوقف، والمواصفة تمنع أي إشارة صوتية
     * قبل الخبو — لا ToneGenerator ولا MediaPlayer ولا خفض مؤقت في هذا المسار.
     */
    private var fadeBaseVolume = 1f
    /**
     * DOZE-HARDENING: partial wake lock held ONLY during the 30s fade so
     * Doze cannot stretch the 1s ticks into 2–3 visible steps (= "abrupt").
     * Non-reference-counted + 60s timeout safety net; released on stop/cancel.
     */
    private var fadeWakeLock: PowerManager.WakeLock? = null
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
        releaseFadeLock()
        stopHandled.set(false)
        setPhase(SleepTimerPhase.RUNNING)
        job = scope.launch { runLoop() }
    }

    fun cancel() {
        job?.cancel()
        if (phase == SleepTimerPhase.FADING_OUT) playback.setVolume(fadeBaseVolume)
        releaseFadeLock()
        deadlineMs = 0L
        stopHandled.set(false)
        setPhase(SleepTimerPhase.IDLE)
    }

    /** تمديد يدوي (+5/+10/+15/+30) من الإشعار/شاشة القفل أو من النافذة داخل التطبيق. */
    fun extendBy(minutes: Int) {
        if (minutes <= 0 || deadlineMs == 0L) return
        deadlineMs += minutes * 60_000L
        val remaining = (deadlineMs - clock.nowMillis()).coerceAtLeast(0L)
        setPhase(if (remaining > SLEEP_WARNING_WINDOW_MS) SleepTimerPhase.RUNNING else phase)
    }

    /**
     * إنقاص يدوي (−5/−10/−15) من النافذة داخل التطبيق أو من الإشعار/شاشة القفل.
     * لا ينزل أبدًا تحت الصفر: إذا بلغ الصفر أو دونه انتهى المؤقت وعادت النافذة
     * إلى الحالة الأولية مع استمرار التشغيل كالمعتاد.
     */
    fun decreaseBy(minutes: Int) {
        if (minutes <= 0 || deadlineMs == 0L) return
        val remaining = deadlineMs - clock.nowMillis()
        val newRemaining = remaining - minutes * 60_000L
        if (newRemaining <= 0L) {
            cancel()
            return
        }
        deadlineMs -= minutes * 60_000L
        setPhase(if (newRemaining > SLEEP_WARNING_WINDOW_MS) SleepTimerPhase.RUNNING else phase)
    }

    /**
     * قاعدة Active Interaction: أي تفاعل فعلي داخل نافذة التحذير يضيف
     * +15 دقيقة بالضبط ويصدر رسالة التمديد. غير الفعلي لا يفعل شيئًا.
     */
    fun onActiveInteraction(interaction: ActiveInteraction) {
        if (!interaction.isInteraction) return
        if (phase != SleepTimerPhase.WARNING_WINDOW && phase != SleepTimerPhase.FADING_OUT) return
        if (deadlineMs == 0L) return
        if (!appSettings.autoExtendSleep.value) return
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
        return minOf(deadlineMs, now + 1_000L)
    }

    /**
     * معالجة لقطة من الزمن (تستدعيها الحلقة دوريًا، ويستدعيها الاختبار بالـFake Clock):
     * انتقالات الحالة، تدرّج Fade، والتوقف. بلا أي إشارة صوتية (FIX 4.6).
     */
    internal suspend fun tickClock() {
        val now = clock.nowMillis()
        if (deadlineMs == 0L) return
        var stopNow = false
        synchronized(tickLock) {
            val remainingMs = deadlineMs - now

            val nextPhase = when {
                remainingMs <= 0L -> SleepTimerPhase.STOPPED
                remainingMs <= SLEEP_FADE_OUT_MS -> SleepTimerPhase.FADING_OUT
                remainingMs <= SLEEP_WARNING_WINDOW_MS -> SleepTimerPhase.WARNING_WINDOW
                else -> SleepTimerPhase.RUNNING
            }
            // FIX 4.5: التقاط المرجع عند دخول الخبو — لا يُفترض مستوى الصوت.
            if (nextPhase == SleepTimerPhase.FADING_OUT && phase != SleepTimerPhase.FADING_OUT) {
                fadeBaseVolume = playback.getVolume().takeIf { it > 0f } ?: 1f
                acquireFadeLock()
            }
            if (nextPhase != phase) setPhase(nextPhase)
            if (phase == SleepTimerPhase.FADING_OUT) {
                val ratio = (remainingMs.toFloat() / SLEEP_FADE_OUT_MS).coerceIn(0f, 1f)
                playback.setVolume(fadeBaseVolume * ratio)
            }
            stopNow = nextPhase == SleepTimerPhase.STOPPED && remainingMs <= 0L
            if (!stopNow) publishState()
        }
        if (stopNow) finishStop()
    }

    /** توقف كامل عند الصفر + حفظ الموضع فورًا (عبر pause الحالية التي تحفظ) + تسجيل الجلسة. */
    private suspend fun finishStop() {
        if (!stopHandled.compareAndSet(false, true)) return
        try {
            playback.setVolume(0f)
            // FIX 4.5: إيقاف مؤقت فقط — لا إغلاق للتطبيق ولا إيقاف للخدمة ولا صوت.
            playback.pause()
            recordSession(endedAtMs = deadlineMs)
            playback.setVolume(fadeBaseVolume)
        } finally {
            releaseFadeLock()
        }
        deadlineMs = 0L
        phase = SleepTimerPhase.STOPPED
        _uiState.value = SleepTimerUiState(phase = SleepTimerPhase.STOPPED, remainingMs = 0L, isExtendWindowVisible = false)
    }

    private fun acquireFadeLock() {
        if (fadeWakeLock?.isHeld == true) return
        fadeWakeLock = runCatching {
            val pm = context.getSystemService(PowerManager::class.java) ?: return@runCatching null
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ather:sleep_fade").apply {
                setReferenceCounted(false)
                acquire(60_000L)
            }
        }.getOrNull()
    }

    private fun releaseFadeLock() {
        runCatching {
            if (fadeWakeLock?.isHeld == true) fadeWakeLock?.release()
        }
        fadeWakeLock = null
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
            isExtendWindowVisible = phase == SleepTimerPhase.WARNING_WINDOW || phase == SleepTimerPhase.FADING_OUT,
            totalDurationMs = if (deadlineMs == 0L) null
            else (deadlineMs - timerStartedMs).coerceAtLeast(1L)
        )
    }
}
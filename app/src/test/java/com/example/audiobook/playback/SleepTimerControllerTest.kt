package com.example.audiobook.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SessionEndReason
import com.example.audiobook.data.room.entity.SessionState
import com.example.audiobook.data.room.entity.SyncStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import java.util.concurrent.Executors

/**
 * [R3] اختبارات مؤقت النوم الذكي كاملة بالـFake Clock — لا انتظار وقت حقيقي إطلاقًا.
 * كل توقيت (3:00، 2:30، …) يقفز إليه الاختبار فورًا عبر FakeClock ثم tickClock().
 */
@RunWith(RobolectricTestRunner::class)
class SleepTimerControllerTest {

    private lateinit var database: AppDatabase
    private lateinit var dbExecutor: java.util.concurrent.ExecutorService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // SQLite في الذاكرة: اتصال واحد لكل خيط، ولأن runLoop ينفّذ الإدراج من خيط خلفي
        // أحيانًا، منفّذ واحد وحيد الخيط يضمن اتصالًا واحدًا — قراءة الكتابة بعد التوقف دائمًا تراها.
        dbExecutor = Executors.newSingleThreadExecutor()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(dbExecutor)
            .setTransactionExecutor(dbExecutor)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        dbExecutor.shutdown()
    }

    private fun controller(clock: FakeClock, playback: FakePlayback) =
        SleepTimerController(clock, playback, database.listeningSessionDao())

    // ---- النقطة 1: آلة الحالات الصريحة IDLE → RUNNING → WARNING_WINDOW → FADING_OUT → STOPPED ----

    @Test
    fun stateMachineAdvancesThroughExactPhasesAndStopsWithPause() = runBlocking {
        val editionId = UUID.randomUUID()
        seedEditionChain(editionId)
        val clock = FakeClock()
        val playback = FakePlayback(editionId = editionId, clock = clock)
        val controller = controller(clock, playback)

        assertEquals(SleepTimerPhase.IDLE, controller.uiState.value.phase)

        controller.start(15)
        assertEquals(SleepTimerPhase.RUNNING, controller.uiState.value.phase)

        val deadline = FAKE_EPOCH + 15 * 60_000L
        clock.set(deadline - SLEEP_WARNING_WINDOW_MS + 1)
        controller.tickClock()
        assertEquals("داخل آخر 3 دقائق", SleepTimerPhase.WARNING_WINDOW, controller.uiState.value.phase)

        clock.set(deadline - SLEEP_FADE_OUT_MS + 1)
        controller.tickClock()
        assertEquals("آخر 2.5 ثانية", SleepTimerPhase.FADING_OUT, controller.uiState.value.phase)
        assertTrue("Fade Out حقيقي: الصوت بدأ ينخفض", playback.currentVolume < 1f)

        clock.set(deadline)
        controller.tickClock()
        assertEquals(SleepTimerPhase.STOPPED, controller.uiState.value.phase)
        assertTrue("انتهى، يجب أن يتوقف التشغيل", playback.paused)
        assertTrue("الحفظ الفوري يمر عبر pause الحالية", playback.paused)

        val sessions = database.listeningSessionDao().getByParent(editionId)
        assertEquals("كتابة الجلسة نجحت فعلًا (لا FK صامتة): سجل واحد بعد التوقف", 1, sessions.size)
        assertEquals(SessionEndReason.SLEEP_TIMER, sessions.first().endReason)
        assertEquals(SessionState.COMPLETED, sessions.first().sessionState)
    }

    // ---- النقطة 2+3: 6 نبضات بالضبط عند التوقيتات الست بالضبط عبر Duck ثم استعادة ----

    @Test
    fun warningWindowProducesExactlySixDuckBeepsAtExactlyTheSixInstants() = runBlocking {
        val clock = FakeClock()
        val playback = FakePlayback(clock = clock)
        val controller = controller(clock, playback)
        controller.start(15)
        val deadline = FAKE_EPOCH + 15 * 60_000L

        // قبل النافذة: لا نبضة إطلاقًا
        clock.set(deadline - SLEEP_WARNING_WINDOW_MS - 1_000L)
        controller.tickClock()
        assertEquals(0, playback.duckEvents().size)

        // كل نقطة من النقاط الست بالضبط: Duck إلى 25% ثم إعادة إلى الصوت الأساسي بعد 300ms
        SLEEP_BEEP_REMAINING_MS.forEach { remaining ->
            val instant = deadline - remaining
            clock.set(instant)
            controller.tickClock()
            val duck = playback.duckEvents().last()
            assertEquals("النقطة بالضبط عند ${remaining / 1000} ثانية متبقية", instant, duck.first)
            assertEquals("نبضة هادئة 25%", SLEEP_DUCK_VOLUME_RATIO, playback.currentVolume, 0.001f)
            clock.advance(SLEEP_DUCK_RESTORE_MS)
            controller.tickClock()
            assertEquals("استعادة الصوت بعد النبضة", 1f, playback.currentVolume, 0.001f)
        }

        assertEquals("6 نبضات بالضبط", 6, playback.duckEvents().size)
        assertEquals(
            "التوقيتات الست: 3:00، 2:30، 2:00، 1:30، 1:00، 0:30",
            SLEEP_BEEP_REMAINING_MS.map { deadline - it },
            playback.duckEvents().map { it.first }
        )
    }

    // ---- النقطة 4: isExtendWindowVisible صريحة ومنفصلة — false قبل النافذة وtrue داخلها ----

    @Test
    fun extendWindowVisibleIsExplicitlyFalseBeforeAndTrueInsideWindow() = runBlocking {
        val clock = FakeClock()
        val playback = FakePlayback(clock = clock)
        val controller = controller(clock, playback)
        controller.start(15)
        val deadline = FAKE_EPOCH + 15 * 60_000L

        clock.set(deadline - SLEEP_WARNING_WINDOW_MS - 1)
        controller.tickClock()
        assertFalse("قبل الـ3 دقائق لا يظهر زر التمديد", controller.uiState.value.isExtendWindowVisible)
        assertEquals(SleepTimerPhase.RUNNING, controller.uiState.value.phase)

        clock.set(deadline - SLEEP_WARNING_WINDOW_MS)
        controller.tickClock()
        assertTrue("داخل الـ3 دقائق يظهر زر التمديد", controller.uiState.value.isExtendWindowVisible)
        assertTrue("المتبقي داخل النافذة", controller.uiState.value.remainingMs!! <= SLEEP_WARNING_WINDOW_MS)

        clock.set(deadline - 1_000L)
        controller.tickClock()
        assertTrue("الزر يبقى ظاهرًا حتى اللحظات المتلاشية", controller.uiState.value.isExtendWindowVisible)
        assertEquals(SleepTimerPhase.FADING_OUT, controller.uiState.value.phase)
    }

    // ---- النقطة 6: كل بند من القائمة النشطة يفعّل التمديد؛ كل بند من غير النشطة لا يفعل ----

    @Test
    fun everyActiveInteractionAutoExtendsDuringWarningWindow() = runBlocking {
        ActiveInteraction.ACTIVE_INTERACTIONS.forEach { interaction ->
            val clock = FakeClock()
            val playback = FakePlayback(clock = clock)
            val controller = controller(clock, playback)
            controller.start(15)
            clock.set(FAKE_EPOCH + 15 * 60_000L - 2 * 60_000L)
            controller.tickClock()
            assertTrue(controller.uiState.value.isExtendWindowVisible)
            val before = controller.uiState.value.remainingMs!!
            controller.onActiveInteraction(interaction)
            assertEquals(
                "$interaction تفاعل فعلي ← +15 دقيقة",
                before + SLEEP_AUTO_EXTEND_MINUTES * 60_000L,
                controller.uiState.value.remainingMs!!
            )
        }
    }

    @Test
    fun activeInteractionEmitsTheAutoExtendMessage() = runBlocking {
        val clock = FakeClock()
        val playback = FakePlayback(clock = clock)
        val controller = controller(clock, playback)
        controller.start(15)
        clock.set(FAKE_EPOCH + 15 * 60_000L - 2 * 60_000L)
        controller.tickClock()
        val collected = async { controller.messages.first() }
        controller.onActiveInteraction(ActiveInteraction.Seek)
        assertEquals("رسالة التمديد التلقائي", SLEEP_AUTO_EXTEND_MESSAGE, collected.await())
    }

    @Test
    fun everyNonActiveInteractionDoesNothing() = runBlocking {
        listOf(
            ActiveInteraction.ScreenOpen,
            ActiveInteraction.ScreenStaysOn,
            ActiveInteraction.DeviceMoved,
            ActiveInteraction.OtherNotification,
            ActiveInteraction.AppInForeground
        ).forEach { interaction ->
            val clock = FakeClock()
            val playback = FakePlayback(clock = clock)
            val controller = controller(clock, playback)
            controller.start(15)
            clock.set(FAKE_EPOCH + 15 * 60_000L - 2 * 60_000L)
            controller.tickClock()
            val before = controller.uiState.value.remainingMs!!
            controller.onActiveInteraction(interaction)
            assertEquals("$interaction ليس تفاعلًا فعليًا — لا تمديد", before, controller.uiState.value.remainingMs!!)
        }
    }

    // ---- النقطة 7: قاعدة +15 بالضبط (وليس Reset وليس +30) بعدة قيم بداية مختلفة ----

    @Test
    fun autoExtendAddsExactlyFifteenMinutesFromMultipleStartValues() = runBlocking {
        listOf(15, 30, 45).forEach { minutes ->
            val clock = FakeClock()
            val playback = FakePlayback(clock = clock)
            val controller = controller(clock, playback)
            controller.start(minutes)
            val deadline = FAKE_EPOCH + minutes * 60_000L
            clock.set(deadline - 2 * 60_000L)
            controller.tickClock()
            val before = controller.uiState.value.remainingMs!!
            assertEquals("2 دقيقة باقية بالضبط", 2 * 60_000L, before)

            controller.onActiveInteraction(ActiveInteraction.Seek)

            val after = controller.uiState.value.remainingMs!!
            assertEquals(
                "مؤقت $minutes دقيقة: +15 بالضبط وليس Reset",
                before + SLEEP_AUTO_EXTEND_MINUTES * 60_000L,
                after
            )
            assertEquals("النتيجة: 17 دقيقة بالضبط", 17 * 60_000L, after)
        }
    }

    // ---- النقطة 5: Custom Actions على MediaSession — مسجّلة وتستدعي المنطق الصحيح ----

    @Test
    fun mediaSessionCustomCommandsAreRegisteredAndExtendExactMinutes() = runBlocking {
        // الثلاثة أوامر مسجّلة في SessionCommands.
        val registered = SleepTimerCommands.sessionCommands()
        assertTrue("+15 مسجّل", registered.commands.contains(SessionCommand(SleepTimerCommands.ACTION_EXTEND_15, Bundle())))
        assertTrue("+30 مسجّل", registered.commands.contains(SessionCommand(SleepTimerCommands.ACTION_EXTEND_30, Bundle())))
        assertTrue("+60 مسجّل", registered.commands.contains(SessionCommand(SleepTimerCommands.ACTION_EXTEND_60, Bundle())))
        assertEquals(3, SleepTimerCommands.customButtons().size)

        // كل أمر يستدعي التمديد الصحيح عبر نفس المسار الذي تستخدمه PlaybackService.
        listOf(15, 30, 60).forEach { minutes ->
            val action = when (minutes) {
                15 -> SleepTimerCommands.ACTION_EXTEND_15
                30 -> SleepTimerCommands.ACTION_EXTEND_30
                else -> SleepTimerCommands.ACTION_EXTEND_60
            }
            val mapped = SleepTimerCommands.extendMinutesFor(SessionCommand(action, Bundle()))
            assertEquals(minutes, mapped)

            val clock = FakeClock()
            val playback = FakePlayback(clock = clock)
            val controller = controller(clock, playback)
            controller.start(45)
            clock.set(FAKE_EPOCH + 45 * 60_000L - 2 * 60_000L)
            controller.tickClock()
            val before = controller.uiState.value.remainingMs!!
            controller.extendBy(mapped!!)
            assertEquals("أمر $action يضيف $minutes بالضبط", before + minutes * 60_000L, controller.uiState.value.remainingMs!!)
        }

        // أمر غير معروف لا يمدد.
        assertNull(SleepTimerCommands.extendMinutesFor(SessionCommand("com.example.unknown", Bundle())))
    }

    /**
     * إثبات صادق: لا جهاز حقيقي في هذه البيئة، لذا لا يمكن اختبار ظهور الأزرار
     * فعليًا على شاشة قفل. ما أُثبت أعلاه: الأوامر مسجّلة في SessionCommands
     * وتستدعي [SleepTimerController.extendBy] بالدقائق الصحيحة — وهو بالضبط
     * مسار PlaybackService.onCustomCommand.
     */

    // ---- النقطة 9: عند التوقف الفعلي تُكتب ListeningSession فورًا (SLEEP_TIMER / COMPLETED) ----

    @Test
    fun sleepTimerStopWritesCompletedListeningSessionToRoomDb() = runBlocking {
        val editionId = UUID.randomUUID()
        seedEditionChain(editionId)
        assertEquals(0, database.listeningSessionDao().getByParent(editionId).size)

        val clock = FakeClock()
        val playback = FakePlayback(editionId = editionId, clock = clock)
        val controller = controller(clock, playback)
        controller.start(15)
        val deadline = FAKE_EPOCH + 15 * 60_000L

        clock.set(deadline)
        controller.tickClock()

        assertEquals(SleepTimerPhase.STOPPED, controller.uiState.value.phase)
        val sessions = database.listeningSessionDao().getByParent(editionId)
        assertEquals("يجب وجود سجل جلسة واحد بعد انتهاء المؤقت", 1, sessions.size)
        val session = sessions.first()
        assertEquals(SessionEndReason.SLEEP_TIMER, session.endReason)
        assertEquals(SessionState.COMPLETED, session.sessionState)
        assertEquals("انتهى في اللحظة المحددة", deadline, session.endedAt)
        assertEquals("بدأ مع المؤقت", FAKE_EPOCH, session.startedAt)
    }

    // ---- دوال مساعدة ----

    private suspend fun seedEditionChain(editionId: UUID) {
        val root = LibraryRootEntity(uri = "content://test/root", displayName = "test", isPriority = false, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        database.libraryRootDao().insert(root)
        val author = AuthorEntity(name = "مؤلف", colorTheme = null)
        database.authorDao().insert(author)
        val book = BookEntity(
            id = UUID.randomUUID(),
            title = "كتاب",
            authorId = author.id,
            seriesId = null,
            orderInSeries = null,
            genre = null,
            coverImagePath = null,
            coverSource = CoverSource.PLACEHOLDER,
            isCoverUserSelected = false,
            defaultEditionId = null,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.bookDao().insert(book)
        database.editionDao().insert(
            EditionEntity(
                id = editionId,
                bookId = book.id,
                narratorName = null,
                label = "النسخة",
                totalDurationMs = 60_000L,
                fileFormat = "m4b",
                libraryRootId = root.id,
                sourceFolderPath = "test",
                confidenceScore = 1f,
                isUserConfirmed = false,
                remoteId = null,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        )
    }

    private class FakeClock(var nowMs: Long = FAKE_EPOCH) : SleepTimerClock {
        override fun nowMillis(): Long = nowMs
        fun advance(deltaMs: Long) { nowMs += deltaMs }
        fun set(ms: Long) { nowMs = ms }
    }

    private class FakePlayback(
        editionId: UUID = UUID.randomUUID(),
        positionMs: Long = 5_000L,
        private val clock: FakeClock
    ) : PlaybackController {
        private val mutableState = MutableStateFlow(
            PlaybackState(editionId = editionId, positionMs = positionMs, durationMs = 10_000L)
        )
        override val state: StateFlow<PlaybackState> = mutableState
        var currentVolume = 1f
        var paused = false
        val volumeEvents = mutableListOf<Pair<Long, Float>>()

        fun duckEvents(): List<Pair<Long, Float>> = volumeEvents.filter { it.second == SLEEP_DUCK_VOLUME_RATIO }

        override suspend fun openEdition(editionId: UUID) = Unit
        override fun play() = Unit
        override fun pause() { paused = true }
        override fun seekTo(positionMs: Long) = Unit
        override fun skipForward15Seconds() = Unit
        override fun skipBack15Seconds() = Unit
        override suspend fun previousChapter() = Unit
        override suspend fun nextChapter() = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun getVolume(): Float = currentVolume
        override fun setVolume(volume: Float) {
            currentVolume = volume
            volumeEvents += clock.nowMillis() to volume
        }
        override fun release() = Unit
    }

    companion object {
        private const val FAKE_EPOCH = 1_000_000L
    }
}
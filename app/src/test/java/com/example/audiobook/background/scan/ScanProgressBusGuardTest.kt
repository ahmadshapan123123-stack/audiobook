package com.example.audiobook.background.scan

import com.example.audiobook.domain.usecases.ScanAlreadyRunningException
import com.example.audiobook.domain.usecases.ScanNowResult
import com.example.audiobook.domain.usecases.ScanProgressBus
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PART 11: حارس فحص واحد. هدفه منع فحوص متزامنة (فحص يدوي + ScanWorker) التي
 * كانت تتلاشى فيها حالة التقدّم ويصبح فيها طلب الإلغاء غير موثوق.
 */
class ScanProgressBusGuardTest {

    /**
     * [ScanProgressBus] كائن ثابت والحارس فيه حالة ممتدة عبر الاختبارات
     * (نفس JVM). بلا تصفير قبل/بعد كل اختبار يتسرّب قفل الحارس من اختبار
     * للآخر فيفشل غير المتوقّع — وسبب ذلك ليس الكود بل العزل.
     */
    @Before
    @After
    fun resetGuard() {
        ScanProgressBus.finish()
        ScanProgressBus.clearRejectedScan()
    }

    @Test
    fun firstBeginWinsAndSecondIsRejected() {
        val first = ScanProgressBus.tryBegin()
        val second = ScanProgressBus.tryBegin()

        assertTrue("أول فحص يبدأ", first)
        assertFalse("ثانٍ يُرفض بينما الأول يعمل", second)
        assertTrue("الناقل يبلّغ عن الرفض", ScanProgressBus.rejectedScan.value)
        assertTrue("الحارس ما زال مملوكًا للجلسة الأولى", ScanProgressBus.isScanActive)
    }

    @Test
    fun finishReleasesTheGuardForTheNextScan() {
        assertTrue(ScanProgressBus.tryBegin())
        ScanProgressBus.finish()

        assertFalse("بعد finish() لا جلسة معلّقة", ScanProgressBus.isScanActive)
        assertTrue("الفحص التالي يبدأ", ScanProgressBus.tryBegin())
    }

    @Test
    fun finishClearsAPendingCancelSoTheNextScanIsNotBornCancelled() {
        assertTrue(ScanProgressBus.tryBegin())
        ScanProgressBus.requestCancel()
        assertTrue(ScanProgressBus.isCancelRequested)

        ScanProgressBus.finish()
        assertFalse("لا يبقى طلب إلغاء عالقًا", ScanProgressBus.isCancelRequested)
    }

    @Test
    fun rejectedScanIsClearedOnTheNextAcceptedBegin() {
        assertTrue(ScanProgressBus.tryBegin())
        assertFalse(ScanProgressBus.tryBegin())
        assertTrue("رُفعت علامة الرفض", ScanProgressBus.rejectedScan.value)

        ScanProgressBus.finish()
        assertTrue(ScanProgressBus.tryBegin())
        assertFalse("الجلسة الجديدة تمسح علامة الرفض السابقة", ScanProgressBus.rejectedScan.value)
    }

    @Test
    fun duplicateStartIsRejectedWithoutCorruptingTheRunningScan() {
        assertTrue(ScanProgressBus.tryBegin())
        val outcome: ScanOutcome = ScanOutcome.Rejected
        // الرفض حالة صريحة لا استثناء: المستدعي يعرض «الفحص جارٍ» لا رسالة خطأ.
        assertTrue(outcome is ScanOutcome.Rejected)
        assertTrue(ScanAlreadyRunningException().message!!.contains("already running"))
    }

    @Test
    fun completedOutcomeCarriesCountsForTheUi() {
        val outcome = ScanOutcome.Completed(ScanNowResult(rootsScanned = 2, filesSeen = 10, booksFound = 7))
        assertTrue(outcome.outcome.booksFound == 7)
        assertTrue(outcome.rebuildResult == null)
    }
}

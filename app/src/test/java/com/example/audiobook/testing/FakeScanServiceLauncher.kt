package com.example.audiobook.testing

import com.example.audiobook.background.scan.ScanJob
import com.example.audiobook.background.scan.ScanOutcome
import com.example.audiobook.background.scan.ScanRequest
import com.example.audiobook.background.scan.ScanServiceLauncher
import com.example.audiobook.background.scan.ScanServiceNotifier
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.domain.usecases.ScanLibraryNow
import com.example.audiobook.domain.usecases.RebuildLibraryStructure

/**
 * خادم فحص مزيف يحاكي [com.example.audiobook.background.scan.ScanForegroundService]
 * داخل اختبار وحدة: ينفّذ العمل في الخيط الحالي ثم ينشر الحصيلة عبر
 * [ScanServiceNotifier] — أي نفس عقد الإنتاج.
 *
 * لزم لأن `SettingsViewModel` لا يملك مسار فحص مباشر بعد PART 1، والخدمات
 * الحقيقية لا تعمل داخل Robolectric. هذا يبقى اختبارًا حقيقيًا للحوار
 * والتقدّم، لا اختبارًا لـ Service.
 */
class FakeScanServiceLauncher(
    private val onLaunch: suspend (ScanRequest) -> Unit
) : ScanServiceLauncher {

    val launchedJobs = mutableListOf<ScanJob>()

    override suspend fun launch(request: ScanRequest): Boolean {
        launchedJobs += request.job
        onLaunch(request)
        return true
    }

    companion object {

        /** ينفّذ الفحص/إعادة البناء فورًا كما تفعل الخدمة. */
        fun running(
            database: AppDatabase,
            scanLibraryNow: ScanLibraryNow,
            rebuildLibraryStructure: RebuildLibraryStructure
        ): FakeScanServiceLauncher = FakeScanServiceLauncher { request ->
            when (request.job) {
                ScanJob.FULL_SCAN -> ScanServiceNotifier.notify(
                    ScanOutcome.Completed(scanLibraryNow())
                )

                ScanJob.REBUILD -> {
                    val result = rebuildLibraryStructure()
                    ScanServiceNotifier.notify(
                        ScanOutcome.Completed(outcome = result.scan, rebuildResult = result)
                    )
                }

                ScanJob.SINGLE_ROOT -> ScanServiceNotifier.notify(
                    ScanOutcome.Completed(scanLibraryNow())
                )

                // GAP 2: التوجيه+fحص جذر واحد. الاختبارات لا تحتاج شجرة
                // استيراد فعلية، فالمهم أنها مرّت عبر الخدمة لا عبر ViewModel.
                ScanJob.ONBOARDING_IMPORT -> {
                    val result = rebuildLibraryStructure()
                    ScanServiceNotifier.notify(
                        ScanOutcome.Completed(outcome = result.scan, rebuildResult = result)
                    )
                }

                // PREVIEW: المزيف لا يحاكي المعاينة (لا اختبار معاينة عبره) —
                // الفرع لإرضاء exhaustiveness فقط.
                ScanJob.PREVIEW -> Unit
            }
        }
    }
}

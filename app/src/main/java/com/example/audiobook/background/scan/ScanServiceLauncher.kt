package com.example.audiobook.background.scan

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * نقطة مرور واحدة مُختبَرة لكل طلب فحص.
 *
 * لماذا واجهة أصلًا: كان `SettingsViewModel` يملك مسارًا احتياطيًا مباشرًا
 * `runCatching { scanLibraryNow() }` حين تُرجع `start()` قيمة منطقية خاطئة.
 * لكن `Context.startForegroundService` تحت Robolectric لا يرمي استثناءً،
 * فيُرجع `true` بلا خدمة تُنفّذ شيئًا — فالمسار الاحتياطي لم يكن يُكتشَف،
 * ويبقى الفحص المباشر محفوظًا في كود الإنتاج.
 *
 * استبداله بالواجهة يضع الحدَّ عند النوع: يصبح «كل فحص يمرّ بالخدمة الأمامية»
 * قيدًا مفروضًا بالبناء لا بالاتفاق، ويصبح سلوك ViewModel قابلًا للاختبار.
 *
 * `suspend` لأن التنفيذ الإنتاجي يكتفي ببدء الخدمة (عملية غير معلّقة) بينما
 * ينفّذ خادم الاختبار العمل داخل النداء — فالواجهة لا تُجبر أيًّا منهما على
 * نمط تنفيذ بعينه، وتبقى صالحة للاختبار.
 *
 * لا تُرجع الواجهة نتيجة الفحص؛ النتائج تصل عبر [ScanServiceNotifier] لأن
 * الخدمة هي التي تملك النتيجة لا ViewModel.
 */
fun interface ScanServiceLauncher {
    /** يبدأ الخدمة الأمامية. `false` = تعذّر البدء (سياسة نظام أو التطبيق في الخلفية). */
    suspend fun launch(request: ScanRequest): Boolean
}

/** التنفيذ الإنتاجي: تفويض كامل إلى [ScanForegroundService]. */
class AndroidScanServiceLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) : ScanServiceLauncher {
    override suspend fun launch(request: ScanRequest): Boolean =
        ScanForegroundService.start(context, request)
}

@Module
@InstallIn(SingletonComponent::class)
object ScanServiceLauncherModule {

    @Provides
    @Singleton
    fun provideScanServiceLauncher(
        @ApplicationContext context: Context
    ): ScanServiceLauncher = AndroidScanServiceLauncher(context)
}

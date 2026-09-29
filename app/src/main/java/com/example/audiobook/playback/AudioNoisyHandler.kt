package com.example.audiobook.playback

/**
 * FIX 8 — قرار إيقاف التشغيل عند ضجيج الصوت (ACTION_AUDIO_BECOMING_NOISY).
 *
 * وحدة صغيرة قابلة للاختبار بلا أندرويد: الخدمة تستدعي [onNoisy] من
 * الـBroadcastReceiver، والقرار (الإعداد + الإيقاف) هنا. `pause()` في
 * المتحكم تحفظ الموضع وتسجّل جلسة — فلا عمل إضافي هنا.
 */
internal class AudioNoisyHandler(
    private val isEnabled: () -> Boolean,
    private val onPause: () -> Unit
) {
    fun onNoisy() {
        if (isEnabled()) onPause()
    }
}

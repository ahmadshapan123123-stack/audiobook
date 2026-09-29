package com.example.audiobook.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 8.4 — قرار فصل السماعة: الإيقاف عند التفعيل، والاستمرار عند التعطيل.
 * (استقبال البث نفسه في PlaybackService — غير قابل للاختبار وحدويًا —
 * والمنطق القابل للكسر هنا.)
 */
class AudioNoisyHandlerTest {

    @Test
    fun noisyPausesWhenEnabled() {
        var paused = false
        val handler = AudioNoisyHandler(isEnabled = { true }, onPause = { paused = true })
        handler.onNoisy()
        assertTrue("مفعّل → إيقاف مؤقت (يحفظ الموضع عبر pause)", paused)
    }

    @Test
    fun noisyDoesNothingWhenDisabled() {
        var paused = false
        val handler = AudioNoisyHandler(isEnabled = { false }, onPause = { paused = true })
        handler.onNoisy()
        assertFalse("معطّل → يواصل على سماعة الهاتف", paused)
    }

    @Test
    fun settingDefaultIsOn() {
        // العقد: الإعداد الافتراضي ON — يُثبت هنا ضد أي تغيير عرضي للمفتاح.
        // (القيمة نفسها في AppSettings.KEY_PAUSE_ON_DISCONNECT=true.)
        var paused = false
        val defaultEnabled = true
        val handler = AudioNoisyHandler(isEnabled = { defaultEnabled }, onPause = { paused = true })
        handler.onNoisy()
        assertTrue(paused)
    }
}

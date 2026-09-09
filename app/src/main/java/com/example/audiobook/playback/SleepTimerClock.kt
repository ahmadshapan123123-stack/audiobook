package com.example.audiobook.playback

/**
 * ساعة قابلة للحقن (Injectable Clock) لكل نقاط توقيت مؤقت النوم.
 * الانتاج الفعلي يستخدم نظام الوقت؛ في الاختبارات يُدفع Fake يقدّم
 * زمنًا وهميًا نقفز به إلى أي توقيت (3:00، 2:30، …) فورًا بلا انتظار.
 */
interface SleepTimerClock {
    fun nowMillis(): Long
}

class SystemSleepTimerClock : SleepTimerClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
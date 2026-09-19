package com.example.audiobook.domain.model

/**
 * خيار لون شعار "أثير" — يتحكم في تلوين شعار الشاشة الافتتاحية والهوية العامة:
 * - [AUTO]: ألوان الشعار الأصلية كما هي.
 * - [LIGHT]: تشابُه دافئ فاتح (عاجي).
 * - [DARK]: تشابُه كوني داكن.
 * - [ACCENT]: صبغ الشعار بلهجة التمييز الديناميكية (حسب تتمة كتاب التشغيل).
 */
enum class LogoColorMode {
    AUTO, LIGHT, DARK, ACCENT
}
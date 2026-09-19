package com.example.audiobook.domain.model

/**
 * وضع المظهر — طبقة محايدة (domain) كي لا تعتمد طبقة data على presentation.
 * القيم ومفتاح التخزين (`theme_mode`) لم يتغيّرا.
 */
enum class AppThemeMode { LIGHT, DARK, AMOLED }

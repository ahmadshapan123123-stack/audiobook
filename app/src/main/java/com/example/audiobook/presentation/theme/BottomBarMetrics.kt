package com.example.audiobook.presentation.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * الإزاحة السفلية الكلية التي تحتلها عناصر الواجهة الثابتة أسفل الشاشة:
 * المشغّل المصغّر (إن وُجد) + المسافة بينه وبين الشريط + شريط التنقل السفلي
 * + هامش النظام السفلي (navigation bar).
 *
 * مصدر واحد للحقيقة: يُحسب من القياس الفعلي في [com.example.audiobook.MainActivity]
 * ويُمرَّر عبر هذا CompositionLocal، فيتحدّث تلقائيًا عند ظهور/اختفاء المشغّل المصغّر
 * وعند تغيّر هامش النظام — دون أن تُكرَّر المعادلة في أي شاشة.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** الحشوة السفلية لكل حاوية قابلة للتمرير: ارتفاع الأشرطة السفلية + هامش أمان صغير (~16dp). */
@Composable
fun bottomContentInset(): Dp = LocalBottomBarInset.current + AppSpacing.md

/** نفس [bottomContentInset] بصيغة [PaddingValues] لحاويات LazyColumn / LazyVerticalGrid. */
@Composable
fun bottomContentPadding(): PaddingValues = PaddingValues(bottom = bottomContentInset())
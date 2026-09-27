package com.example.audiobook.presentation.common

/**
 * تسمية عرض موحّدة للكتاب المستقل غير المُصنَّف (authorId=null) في الواجهات.
 * الكتاب المستقل = ملف صوتي مباشر في جذر مكتبة بلا مجلد مؤلف.
 */
object DisplayLabels {
    const val UNASSIGNED_AUTHOR = "غير مصنف"
}

/**
 * تنظيف اسم العنوان للعرض فقط، بلا مساس بما هو مخزَّن.
 *
 * أسماء ملفات التحميل تحمل آثارًا تسرّب إلى العرض: ترقيمًا بادئًا وامتدادًا
 * مزدوجًا ("01 - العنوان.mp3" أو "title.f100.mp3") وشرطات سفلية بدل المسافات.
 * وقواعد التصنيف تستعمل الاسم الخام في هوية الكتاب (bookTitle) وفي مفتاح
 * البحث البنيوي، فتنظيفه هنا يكسر مطابقة إعادة الفحص. لذلك يكون التنظيف
 * عند العرض وحده.
 */
fun cleanDisplayTitle(raw: String): String {
    var text = raw.trim()
    // "01 - " / "001- " / "1_" : ترقيم بائد يفصله خط أو شرطة سفلية.
    text = text.replace(Regex("^\\d{1,4}\\s*[-–—_]\\s*"), "")
    // امتداد متبقٍّ (mp3/m4a/mp4/webm/flac/ogg/opus/wav) — يُزال أولًا، فبقي
    // ما بعده إن كان لاحقة تحميل ("عنوان.f100.mp3" → "عنوان.f100").
    text = text.replace(Regex("\\.(mp3|m4a|m4b|mp4|webm|flac|ogg|opus|wav|aac|zip)$", RegexOption.IGNORE_CASE), "")
    // "title.f100" / "title.f251" : لاحقة yt-dlp.
    text = text.replace(Regex("\\.f\\d{2,4}$"), "")
    // "title.en" / "title.ar" : لاحقة لغة.
    text = text.replace(Regex("\\.(ar|en|fr|es|de|tr|ur|hi|fa|auto)$"), "")
    // "__" → " " مع الحفاظ على الكلمة نفسها.
    text = text.replace(Regex("[_]+"), " ")
    // مسافات متكررة وشرطات زائدة من الأثر.
    text = text.replace(Regex("\\s{2,}"), " ").replace(Regex("^[-–—\\s]+|[-–—\\s]+$"), "")
    return text.trim()
}

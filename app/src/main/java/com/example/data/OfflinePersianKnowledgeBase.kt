package com.example.data

data class PersianAnomalyKnowledge(
    val id: String,
    val title: String,
    val category: String, // "فلزات", "حفره‌ها", "سازه‌ها", "کالیبراسیون"
    val confidence: String,
    val phaseRange: String,
    val adcBehavior: String,
    val description: String,
    val depthFormula: String,
    val tacticalAdvice: String,
    val voiceSpeechScript: String // Optimized script for Persian TTS audio playback
)

object OfflinePersianKnowledgeBase {

    val anomalyEntries: List<PersianAnomalyKnowledge> = listOf(
        PersianAnomalyKnowledge(
            id = "gold_precious",
            title = "طلا و فلزات گرانبها (Gold & Precious Metals)",
            category = "فلزات",
            confidence = "۸۸٪ تا ۹۵٪",
            phaseRange = "فاز مثبت شدید (+35° تا +85°)",
            adcBehavior = "پیک بالا و تیز ADC (بیشتر از 350+)",
            description = "طلا دارای بالاترین ضریب هدایت الکتریکی و کمترین خاصیت مغناطیسی است. در اسکن زمین، سیگنال طلا به صورت یک کوهان تیز با فاز مثبت بدون افت نفوذ مغناطیسی ثبت می‌شود.",
            depthFormula = "عمق تقریبی = (حداکثر مقدار ADC / 160) با ضریب خطای ۰.۱۵ متر",
            tacticalAdvice = "یک اسکن 90 درجه متعامد (Cross-Scan) انجام دهید. اگر آنومالی در هر دو مسیر ثابت ماند، احتمال وجود شیء فلزی گرانبها بالای ۹۰ درصد است.",
            voiceSpeechScript = "شناسایی هدف فلز گرانبها و طلا. فاز مثبت شدید ثبت شده است. پیشنهاد می‌شود اسکن متعامد نود درجه جهت تایید قطعی انجام شود."
        ),
        PersianAnomalyKnowledge(
            id = "cavity_tunnel",
            title = "حفره، دالان و اتاقک باستانی (Cavity & Vault)",
            category = "حفره‌ها",
            confidence = "۹۲٪",
            phaseRange = "فاز منفی شدید (-45° تا -90°)",
            adcBehavior = "افت شدید سیگنال ADC (کمتر از 350-)",
            description = "حفره‌ها، اتاقک‌ها و دالان‌های زیرزمینی باعث افت نفوذپذیری مغناطیسی زمین می‌شوند. این حالت به صورت یک چاله آبی رنگ عمیق در نمایشگر سه‌بعدی ظاهر می‌گردد.",
            depthFormula = "عمق سقف حفره = (افت سیگنال / 140) + 1.2 متر",
            tacticalAdvice = "حاشیه چاله عمیق را برای پیدا کردن ورودی یا پله‌های احتمالی بررسی کنید. معمولاً ورودی دالان‌ها دارای سیگنال مغناطیسی ضعیف‌تری است.",
            voiceSpeechScript = "حفره یا دالان زیرزمینی شناسایی شد. افت شدید سیگنال نشان‌دهنده فضای خالی یا ساختار دست‌ساز زیرزمینی است."
        ),
        PersianAnomalyKnowledge(
            id = "copper_silver",
            title = "مس، مفرغ و نقره (Copper & Silver Conductors)",
            category = "فلزات",
            confidence = "۸۵٪",
            phaseRange = "فاز مثبت متوسط (+20° تا +50°)",
            adcBehavior = "سیگنال پهن و قوی (250+ تا 450+)",
            description = "فلزات رسانا نظیر مس و مفرغ دارای پاسخ الکترومغناطیسی پهن هستند. اکسید شدن این فلزات در طول زمان باعث پخش شدن هاله سیگنال در اطراف هدف می‌شود.",
            depthFormula = "عمق = (شدت سیگنال / 180) متر",
            tacticalAdvice = "برای تشخیص اندازه واقعی جسم، فیلتر حذف هاله اکسید (Soil Noise Filter) را در نرم‌افزار فعال کنید.",
            voiceSpeechScript = "سیگنال مس یا مفرغ با هاله اکسید اطراف. برای تخمین اندازه واقعی، فیلتر آلودگی خاک را فعال نمایید."
        ),
        PersianAnomalyKnowledge(
            id = "ferrous_iron",
            title = "آهن و قطعات مغناطیسی (Ferrous Objects)",
            category = "فلزات",
            confidence = "۹۰٪",
            phaseRange = "نوسان فاز دوقطبی (موج سینوسی مثبت و منفی)",
            adcBehavior = "پیک مثبت و منفی سریع در کنار هم",
            description = "اجسام آهنی دارای رفتار دوقطبی (Dipole) هستند. سنسور مگنتومتر در یک سمت شیء پیک مثبت و در سمت دیگر پیک منفی ثبت می‌کند.",
            depthFormula = "عمق = فاصله بین دو پیک دوقطبی × 1.1",
            tacticalAdvice = "رفتار دوقطبی نشان‌دهنده فلزات مغناطیسی نظیر آهن است. در صورتی که هدف شما فلزات غیرآهنی است، می‌توانید این نقطه را صرف‌نظر کنید.",
            voiceSpeechScript = "هدف آهنی و مغناطیسی با رفتار دوقطبی. این سیگنال مربوط به فلزات آهنی پوسیده یا قطعات سطحی است."
        ),
        PersianAnomalyKnowledge(
            id = "mineral_soil",
            title = "سنگ‌های معدنی و ذرات خیس (Mineral Rocks)",
            category = "کالیبراسیون",
            confidence = "۷۸٪",
            phaseRange = "فاز متغیر نوسانی کم‌ارتفاع",
            adcBehavior = "پراکنده‌ای از نقاط کوچک (100+ تا 200+)",
            description = "ذرات رگه‌ای، سنگ‌های آذرین و خاک‌های دارای رطوبت و نمک بالا باعث ایجاد ذرات کاذب در اسکن می‌شوند.",
            depthFormula = "عمق سطحی (کمتر از ۰.۵ متر)",
            tacticalAdvice = "دکمه کالیبراسیون ۴ نقطه‌ای خاک را در برنامه بفشارید و بالانس زمین را روی نوع خاک منطقه تنظیم کنید.",
            voiceSpeechScript = "تداخل آلودگی معدنی خاک. لطفاً کالیبراسیون بالانس زمین را مجدداً انجام دهید."
        ),
        PersianAnomalyKnowledge(
            id = "ancient_masonry",
            title = "دیواره باستانی و ساروج (Ancient Wall & Mortar)",
            category = "سازه‌ها",
            confidence = "۸۲٪",
            phaseRange = "خطوط منظم فاز یکنواخت",
            adcBehavior = "تغییر سطح خطی و پیوسته",
            description = "سازه‌های ساروجی و دیوار‌های زیرزمینی دارای نظم هندسی مستطیلی یا خطی در تصویر سه‌بعدی هستند.",
            depthFormula = "عمق سازه = 1.5 متر تا 4.0 متر بر اساس ضخامت دیوار",
            tacticalAdvice = "از نمای دو بعدی (Plan View) استفاده کنید تا هندسه خطی دیوار کاملاً مشخص شود.",
            voiceSpeechScript = "شناسایی دیواره باستانی یا پارت‌های منظم ساروجی در لایه‌های زیرین خاک."
        )
    )

    fun search(query: String): List<PersianAnomalyKnowledge> {
        if (query.isBlank()) return anomalyEntries
        val q = query.trim().lowercase()
        return anomalyEntries.filter {
            it.title.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.category.lowercase().contains(q) ||
            it.tacticalAdvice.lowercase().contains(q)
        }
    }

    fun getKnowledgeForSignal(maxAdc: Float, minAdc: Float): PersianAnomalyKnowledge {
        return when {
            maxAdc > 450f -> anomalyEntries[0] // Gold
            minAdc < -450f -> anomalyEntries[1] // Cavity
            maxAdc > 250f -> anomalyEntries[2] // Copper/Silver
            minAdc < -250f -> anomalyEntries[5] // Ancient masonry
            else -> anomalyEntries[4] // Mineral soil
        }
    }
}

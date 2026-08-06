package com.example.ui

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini API Models ---

data class GeminiPart(val text: String)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

data class GeminiCandidate(val content: GeminiContent)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

// --- Retrofit Interface ---

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") key: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

// --- Service Client ---

object AiTacticalClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: GeminiApi = retrofit.create(GeminiApi::class.java)

    suspend fun getTacticalAnalysis(
        soilType: String,
        scanPattern: String,
        gridWidth: Int,
        gridLength: Int,
        gridData: List<Float>,
        userMessage: String? = null
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "⚠️ خطا: کلید API معتبر یافت نشد. لطفاً کلید Gemini API خود را در بخش تنظیمات یا Secrets پروژه تنظیم کنید."
        }

        // Format the scan statistics to give Gemini strong data context
        val minVal = gridData.minOrNull() ?: 0f
        val maxVal = gridData.maxOrNull() ?: 0f
        val avgVal = if (gridData.isNotEmpty()) gridData.average().toFloat() else 0f
        val anomalyCount = gridData.count { Math.abs(it) > 400f }
        val metallicCount = gridData.count { it > 350f }
        val cavityCount = gridData.count { it < -350f }

        val dataSnippet = if (gridData.size > 150) {
            gridData.take(100).joinToString(", ") + "... [و ${gridData.size - 100} نقطه دیگر]"
        } else {
            gridData.joinToString(", ")
        }

        val prompt = if (userMessage != null) {
            """
            کاربر سوال زیر را پرسیده است:
            "$userMessage"
            
            اطلاعات اسکن فعلی جهت بررسی تاکتیکال:
            نوع خاک: $soilType
            الگوی اسکن: $scanPattern
            ابعاد شبکه: $gridWidth در $gridLength (کل نقاط: ${gridData.size})
            حداقل فرکانس/سیگنال: $minVal
            حداکثر فرکانس/سیگنال: $maxVal
            میانگین داده‌ها: $avgVal
            تعداد نقاط آنومالی بحرانی: $anomalyCount (مثبت فلزی: $metallicCount، منفی حفره‌ای: $cavityCount)
            نمونه داده‌های سنسور: [$dataSnippet]
            
            پاسخ دقیق، علمی، نظامی و تاکتیکال به زبان فارسی ارائه دهید.
            """.trimIndent()
        } else {
            """
            به عنوان یک تحلیلگر نظامی و مهندس ژئوفیزیک باستان‌شناسی ارشد سیستم GOLD RADAR X20، اسکن زیر را با جزئیات علمی، ساختار لایه‌ها، موقعیت آنومالی‌ها، نوع فلز و راهکار عملی کالیبراسیون و حفاری تحلیل کنید:
            
            نوع خاک منطقه: $soilType
            الگوی فیزیکی اسکن: $scanPattern
            ابعاد ماتریس اسکن: $gridWidth در $gridLength (مجموع کل سیگنال‌ها: ${gridData.size})
            کمترین دریافت سنسور: $minVal
            بیشترین دریافت سنسور: $maxVal
            سیگنال پس‌زمینه متوسط: $avgVal
            آنومالی‌های شاخص شناسایی‌شده:
            - پتانسیل فلزی قوی (مثبت > 350): $metallicCount نقطه
            - پتانسیل حفره یا تونل (منفی < -350): $cavityCount نقطه
            
            داده‌های خام سنسورها به ترتیب سطر و ستون:
            [$dataSnippet]
            
            یک گزارش تحلیلی فوق‌العاده تاکتیکال، جذاب، خوانا با ایموجی‌های مناسب در قالب بخش‌های زیر بنویسید:
            ۱. 📊 خلاصه وضعیت و تشریح ژئوفیزیکی محیط
            ۲. 🎯 شناسایی هدف (مشخص کردن نوع فلز: طلا، نقره، مس، یا حفره/اتاقک خالی با ذکر احتمال درصد)
            ۳. 🗺️ برآورد موقعیت فرضی و عمق حدودی هدف بر اساس محاسبات لایه‌ای
            ۴. ⚡ توصیه‌های فنی و تاکتیکال (راهنمای حرکت سنسور، لزوم کالیبراسیون مجدد، یا تغییر فرکانس)
            
            زبان خروجی حتما فارسی شیوا، تخصصی، علمی و بسیار پرابهت و دقیق باشد.
            """.trimIndent()
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(
                        text = "شما مغز متفکر هوش مصنوعی و تحلیلگر تاکتیکال رادار طلایاب پیشرفته GOLD RADAR X20 هستید. پاسخ‌های شما باید کاملا فنی، دقیق، علمی (بر اساس فیزیک خاک، فرکانس‌های مغناطیسی، هدایت الکتریکی، آنومالی‌های زمین) و پرابهت باشند. کاربران شما اپراتورهای حرفه‌ای رادار و دفینه‌یاب‌ها هستند."
                    )
                )
            )
        )

        return try {
            val response = api.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "⚠️ پاسخی از هوش مصنوعی دریافت نشد. لطفاً مجدداً تلاش کنید."
        } catch (e: Exception) {
            "❌ خطا در برقراری ارتباط با هوش مصنوعی تاکتیکال: ${e.localizedMessage ?: e.message}"
        }
    }
}

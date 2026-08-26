# OkHttp 4.x
-keepattributes *Annotation*
-keepclassmembers class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Models & JSON
-keep class com.wisp.model.** { *; }
-keepattributes Signature

# WebView JS Interface (на будущее)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
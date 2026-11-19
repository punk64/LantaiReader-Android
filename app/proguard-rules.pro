-dontwarn org.slf4j.**
-keep class com.github.junrar.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class com.lantai.reader.net.NativeBridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

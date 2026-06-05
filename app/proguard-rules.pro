# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Preserve annotations used by Hilt, Retrofit and Room
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Retrofit
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-dontwarn dagger.hilt.**

# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Kotlin metadata
-keep class kotlin.Metadata { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
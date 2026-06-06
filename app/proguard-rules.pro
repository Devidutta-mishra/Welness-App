# =============================================================================
# Yourswelnes — R8 / ProGuard rules
# =============================================================================
# Libraries ship their own consumer rules inside their AAR files; we only need
# rules for:
#   1. Reflection-based code that R8 cannot trace statically (Gson DTOs, Room
#      entities, Hilt entry-points, WorkManager workers).
#   2. Third-party libraries that ship incomplete consumer rules.
#   3. Crash-report readability (line numbers, renamed source file attribute).
# Do NOT add broad "-keep class androidx.**" or "-keep class retrofit2.**" rules —
# those suppress obfuscation of library internals and grow the APK for no benefit.
# =============================================================================

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Retrofit + OkHttp ─────────────────────────────────────────────────────────
# Keep annotations that Retrofit reads at runtime via reflection.
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# Keep our Retrofit API interfaces so their method signatures survive.
-keep interface com.example.yourswelnes.**.api.** { *; }

# OkHttp / Okio platform-detection code triggers warnings on non-standard JVMs.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**

# ── Gson / JSON ───────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Keep ALL DTO classes (request + response bodies) so Gson can construct them.
-keep class com.example.yourswelnes.**.dto.** { *; }

# Keep every field annotated @SerializedName — Gson maps JSON keys to these.
# Other fields in the same class may be freely renamed/removed by R8.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Room ──────────────────────────────────────────────────────────────────────
# Entity classes: Room generates SQL column names from @ColumnInfo; the class
# and its annotated fields must survive with their original names.
-keep class com.example.yourswelnes.core.database.entity.** { *; }

# DAO interfaces: Room's generated implementation refers to these by type.
-keep interface com.example.yourswelnes.core.database.dao.** { *; }

# ── Hilt / Dagger ─────────────────────────────────────────────────────────────
# Hilt ships its own consumer rules. We only need to keep entry-point classes
# that the generated component factory looks up by annotation.
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-dontwarn com.google.errorprone.**
-dontwarn dagger.hilt.processor.**

# ── WorkManager + HiltWorker ──────────────────────────────────────────────────
# HiltWorkerFactory instantiates workers by class name; their constructors must
# be kept so Hilt can inject dependencies into them.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── DataStore ─────────────────────────────────────────────────────────────────
-dontwarn androidx.datastore.**

# ── Coil ──────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── Play Services / Google ────────────────────────────────────────────────────
-dontwarn com.google.android.gms.**

# ── Domain models ─────────────────────────────────────────────────────────────
# Auth and location domain models are passed across Hilt component boundaries
# and stored in DataStore; keep their structure intact.
-keep class com.example.yourswelnes.feature.auth.model.** { *; }
-keep class com.example.yourswelnes.feature.location.model.** { *; }

# ── Crash-report readability ──────────────────────────────────────────────────
# Line numbers in stack traces make Firebase Crashlytics / logcat usable.
# The original .java/.kt file names are hidden by renamesourcefileattribute.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

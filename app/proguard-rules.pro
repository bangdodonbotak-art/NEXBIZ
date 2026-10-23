# ==============================================================================
# NEXBIZ POS — PRODUCTION R8 / PROGUARD RULES
# Protects Room SQLite, Kotlinx Serialization, Retrofit/OkHttp/Moshi,
# Dagger Hilt / JSR-330, MLKit Barcode Scanning, WorkManager, and Security Crypto
# ==============================================================================

# Preserve source file names and line numbers for production crash stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------------------
# 1. ROOM SQLITE DATABASE, ENTITIES, DAOs & TYPE CONVERTERS
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase {
    <init>();
    *;
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class id.nexbiz.pos.data.local.entity.** { *; }
-keep class id.nexbiz.pos.data.local.dao.** { *; }
-keep class id.nexbiz.pos.data.local.model.** { *; }
-keep class id.nexbiz.pos.data.local.db.** { *; }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 2. KOTLINX SERIALIZATION (JSON DTOs & TYPE-SAFE NAVIGATION ROUTES)
# ------------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Companion and serializer() on every @Serializable class
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep @kotlinx.serialization.Serializable class id.nexbiz.pos.** { *; }
-keep class id.nexbiz.pos.data.remote.dto.** { *; }
-keep class id.nexbiz.pos.ui.navigation.** { *; }

# ------------------------------------------------------------------------------
# 3. RETROFIT 2, OKHTTP 4 & MOSHI CODEGEN
# ------------------------------------------------------------------------------
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Moshi @JsonClass generated adapters and Kotlin reflection fallback
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter {
    <init>(com.squareup.moshi.Moshi);
    *;
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# ------------------------------------------------------------------------------
# 4. DAGGER HILT / JSR-330 DEPENDENCY INJECTION
# ------------------------------------------------------------------------------
-dontwarn dagger.**
-dontwarn javax.inject.**
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}

# ------------------------------------------------------------------------------
# 5. GOOGLE MLKIT BARCODE SCANNING & VISION NATIVE BRIDGES
# ------------------------------------------------------------------------------
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.barhopper.** { *; }
-keep class com.google.photos.vision.barhopper.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.barhopper.**

# Keep JNI native methods used by MLKit and Room SQLite
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ------------------------------------------------------------------------------
# 6. ANDROIDX WORKMANAGER (OUTBOX SYNC COROUTINE WORKER)
# ------------------------------------------------------------------------------
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class id.nexbiz.pos.core.sync.OutboxSyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
    *;
}

# ------------------------------------------------------------------------------
# 7. JETPACK SECURITY CRYPTO (TINK) & BIOMETRIC PROMPT
# ------------------------------------------------------------------------------
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-keep class androidx.security.crypto.** { *; }
-keep class androidx.biometric.** { *; }

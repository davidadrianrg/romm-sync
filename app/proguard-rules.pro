# ── Retrofit ────────────────────────────────────────────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ── Moshi ───────────────────────────────────────────────────────────────
# Clases que se (de)serializan con Moshi reflexivo (KotlinJsonAdapterFactory):
# si R8 renombra sus campos, Moshi lanza un Error y tumba la app en release.
-keepclassmembers class com.davidadrianrg.atril.core.remote.dto.** { *; }
-keepclassmembers class com.davidadrianrg.atril.core.localgames.LocalGame { *; }
-keepclassmembers class com.davidadrianrg.atril.data.sync.ConflictInfo { *; }
-keepclassmembers class com.davidadrianrg.atril.data.sync.FailedOpInfo { *; }
-keepclassmembers class com.davidadrianrg.atril.data.update.GitHubReleaseDto { *; }
-keepclassmembers class com.davidadrianrg.atril.data.update.GitHubAssetDto { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass <methods>;
}

# ── Room ────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ── Coroutines ──────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Coil ────────────────────────────────────────────────────────────────
-keep class coil.** { *; }

# ── WorkManager ─────────────────────────────────────────────────────────
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# ── Kotlin Metadata ─────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }

# ── Compose ─────────────────────────────────────────────────────────────
# Compose ya incluye sus propias reglas en el AAR; no suele necesitar
# reglas adicionales, pero mantenemos esto como safety net.
-dontwarn androidx.compose.**

# ── Tink / Security-Crypto ──────────────────────────────────────────────
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.crypto.tink.aead.** { *; }

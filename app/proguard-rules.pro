# ── Attributes ────────────────────────────────────────────────────────────────
# Signature is the critical one: without it Gson can't read generic type params
# at runtime and throws "Class cannot be cast to ParameterizedType".
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes AnnotationDefault
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# ── Gson ──────────────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep all fields annotated with @SerializedName so Gson can map JSON keys
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# R8 full mode: allowobfuscation variant so names can still be shrunk but fields are retained
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Retrofit ──────────────────────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
# Retain service method signatures (allowshrinking,allowobfuscation = official R8 recommendation)
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# With R8 full mode, suspend functions are wrapped in Continuation<T>; preserve the interface
# so Retrofit can read the generic return type at runtime via reflection.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation,allowshrinking interface <1>
-dontwarn retrofit2.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ── OkHttp / Okio ─────────────────────────────────────────────────────────────
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ── App remote API models ─────────────────────────────────────────────────────
# All Xtream / M3U / XMLTV models, Retrofit service interfaces and response types
-keep class app.nexstream.player.data.remote.** { *; }
-keepclassmembers class app.nexstream.player.data.remote.** {
    <init>(...);
    <fields>;
}

# Licence, trial and device API request/response models
-keep class app.nexstream.player.license.** { *; }
-keepclassmembers class app.nexstream.player.license.** {
    <init>(...);
    <fields>;
}

# Sync API models (profile, watchlist, progress)
# WatchlistResponse and ProgressResponse contain List<Map<String,String?>> — keep all members
# so Gson can resolve the doubly-nested generic type at runtime.
-keep class app.nexstream.player.data.sync.** { *; }
-keepclassmembers class app.nexstream.player.data.sync.** {
    <init>(...);
    <fields>;
}

# Theme API response models parsed from JSON
-keep class app.nexstream.player.ui.theme.** { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *

# ── Hilt ──────────────────────────────────────────────────────────────────────
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class dagger.hilt.** { *; }

# ── Media3 / ExoPlayer ────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep class * extends androidx.media3.session.MediaSessionService { *; }
-keep class * extends androidx.media3.session.MediaLibraryService { *; }

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
# Retrofit reads Continuation<T>'s generic type parameter to determine the actual return type
# of suspend functions — keep the Signature attribute on Continuation and its subtypes.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── Google Cast Framework ─────────────────────────────────────────────────────
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class app.nexstream.player.cast.CastOptionsProvider { *; }
-dontwarn com.google.android.gms.cast.**

# ── Whisper JNI (on-device AI subtitles) ─────────────────────────────────────
-keep class app.nexstream.player.subtitle.WhisperLib { *; }

# ── Stack traces (enable for easier crash debugging) ─────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


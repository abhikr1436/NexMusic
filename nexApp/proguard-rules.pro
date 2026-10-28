# R8 rules for the release build. Based on upstream SimpMusic's androidApp rules, which this core ships with.
# Keep line numbers so crash reports from users still point at real code.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Coroutines ---
-keep class kotlinx.coroutines.CoroutineExceptionHandler
-keep class kotlinx.coroutines.internal.MainDispatcherFactory

# --- kotlinx.serialization (YouTube Music, lyrics and Spotify responses) ---
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
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# --- The shared core: scraper, data layer, lyrics (reflection and serializers by name) ---
-keep class com.maxrave.kotlinytmusicscraper.** { *; }
-keep class com.maxrave.data.di.** { *; }
-keep class com.maxrave.data.extension.** { *; }
-keep class com.maxrave.data.mapping.MappingKt { *; }
-keep class org.simpmusic.lyrics.parser.** { *; }
-keep class org.simpmusic.lyrics.models.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# --- Stream extraction: NewPipe/PipePipe extractor with Rhino JavaScript ---
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class dev.maxrave.pipepipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-keep class com.liskovsoft.** { *; }
-keep interface com.liskovsoft.** { *; }
-keep class com.eclipsesource.v8.** { *; }
-keep class com.google.re2j.** { *; }
-keep class org.apache.commons.io.** { *; }
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-keep class com.yausername.** { *; }

# --- ffmpeg-kit (music-video downloads) loads its classes from native code ---
-keep class com.arthenica.ffmpegkit.** { *; }

# --- Retrofit / OkHttp ---
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.Util

# --- JVM-only classes referenced by libraries but never reached on Android ---
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn com.google.re2j.**
-dontwarn kotlin.Unit

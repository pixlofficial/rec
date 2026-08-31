# PixL REC Proguard Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep annotated members
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep Parcelable Creator implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep core models and enums for serialization & reflection
-keep class pixl.rec.core.model.** { *; }

# Keep Compose Runtime stability
-keep class androidx.compose.** { *; }

# Keep Coroutine Dispatchers
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

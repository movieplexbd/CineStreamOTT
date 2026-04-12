# Add project specific ProGuard rules here.

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Realtime Database - keep model classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.ottapp.moviestream.data.model.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES; public *;
}
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# Shimmer — keep entire library to prevent crash on layout inflation
-keep class com.facebook.shimmer.** { *; }
-dontwarn com.facebook.shimmer.**

# CircleImageView — keep to prevent crash on layout inflation
-keep class de.hdodenhof.circleimageview.** { *; }
-dontwarn de.hdodenhof.circleimageview.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Navigation
-keep class androidx.navigation.** { *; }

# ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(...);
}

# Keep all Fragment subclasses (NavHostFragment uses reflection to instantiate them)
-keep class * extends androidx.fragment.app.Fragment { <init>(); }
-keep class com.ottapp.moviestream.ui.** { *; }

# Keep ViewModel subclasses (ViewModelProvider uses reflection)
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep RecyclerView Adapter subclasses
-keep class com.ottapp.moviestream.adapter.** { *; }

# Keep Application class
-keep class com.ottapp.moviestream.OTTApplication { *; }

# Keep Activity/Service classes declared in manifest
-keep class com.ottapp.moviestream.SplashActivity { *; }
-keep class com.ottapp.moviestream.LoginActivity { *; }
-keep class com.ottapp.moviestream.MainActivity { *; }
-keep class com.ottapp.moviestream.service.DownloadService { *; }

# Remove debug logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

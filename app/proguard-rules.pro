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

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Modelos de datos serializados por Firestore — mantener nombre de clase Y todos los miembros
# (Firestore usa reflection sobre nombres de campos; R8 no debe renombrarlos)
-keep class com.joseibarra.touristnotify.TouristSpot { *; }
-keep class com.joseibarra.touristnotify.User { *; }
-keep class com.joseibarra.touristnotify.Review { *; }
-keep class com.joseibarra.touristnotify.Route { *; }
-keep class com.joseibarra.touristnotify.Favorite { *; }
-keep class com.joseibarra.touristnotify.CheckIn { *; }
-keep class com.joseibarra.touristnotify.Event { *; }
-keep class com.joseibarra.touristnotify.BlogPost { *; }
-keep class com.joseibarra.touristnotify.WeatherInfo { *; }
-keep class com.joseibarra.touristnotify.ForecastDay { *; }
-keep class com.joseibarra.touristnotify.ThemedRoute { *; }
-keep class com.joseibarra.touristnotify.PlacePhoto { *; }

# Firebase Firestore
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses

# Google Play Services / Maps
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }
-keep class com.google.android.gms.location.** { *; }

# Google Places API
-keep class com.google.android.libraries.places.** { *; }

# Google Maps Android Utils (polyline decoding, spherical math)
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.maps.android.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Generative AI SDK
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# OkHttp3
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# JSON parsing
-keep class org.json.** { *; }

# Preserve generic signatures
-keepattributes Signature
# --- Retrofit & OkHttp ---
-keepattributes Signature, InnerClasses, AnnotationDefault
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# --- Gson ---
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.TypeAdapterFactory
-keep class com.example.andrfindmusicapp.data.model.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.example.andrfindmusicapp.data.local.** { *; }

# --- Coil ---
-keep class coil.** { *; }
-dontwarn coil.**

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.**
-keepattributes *Annotation*
-dontwarn net.bytebuddy.**

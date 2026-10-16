# Keep rules for Hilt, Room, kotlinx.serialization, DataStore/proto.
# R8 full mode is on (AGP 9 default); these are the rules the template needs
# beyond what the libraries ship themselves.

# --- Hilt ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.ComponentManager { *; }
-keepclasseswithmembernames class * {
    @dagger.Provides *;
}
-keepclasseswithmembernames class * {
    @dagger.Binds *;
}
-keepclasseswithmembernames class * {
    @dagger.multibindings.ProvidesIntoMap *;
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.DatabaseView class * { *; }
-dontwarn androidx.room.paging.**

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
# Serializers referenced only via generated code.
-keepnames class **$$serializer { *; }

# --- DataStore / protobuf ---
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-keep class androidx.datastore.** { *; }

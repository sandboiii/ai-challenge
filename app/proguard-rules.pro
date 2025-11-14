# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep data classes for serialization
-keep class xyz.sandboiii.agentcooper.data.remote.dto.** { *; }
-keep class xyz.sandboiii.agentcooper.data.local.entity.** { *; }

# Keep Room entities
-keep @androidx.room.Entity class * { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Compose Runtime - keep snapshot state classes
-keep class androidx.compose.runtime.snapshots.SnapshotStateMap { *; }
-keep class androidx.compose.runtime.snapshots.SnapshotStateList { *; }
-keepclassmembers class androidx.compose.runtime.snapshots.SnapshotStateMap {
    <methods>;
}
-keepclassmembers class androidx.compose.runtime.snapshots.SnapshotStateList {
    <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class xyz.sandboiii.agentcooper.data.local.model.**$$serializer { *; }
-keepclassmembers class xyz.sandboiii.agentcooper.data.local.model.** {
    *** Companion;
}
-keepclasseswithmembers class xyz.sandboiii.agentcooper.data.local.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}


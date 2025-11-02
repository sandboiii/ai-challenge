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


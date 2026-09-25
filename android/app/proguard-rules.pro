# kotlinx.serialization — keep generated serializers for @Serializable classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ubad.academy.**$$serializer { *; }
-keepclassmembers class com.ubad.academy.** { *** Companion; }
-keepclasseswithmembers class com.ubad.academy.** { kotlinx.serialization.KSerializer serializer(...); }

# Navigation type-safe routes are @Serializable objects/classes
-keep class com.ubad.academy.ui.navigation.** { *; }

# Jsoup optional re2j dependency
-dontwarn com.google.re2j.**
# OkHttp platform classes
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

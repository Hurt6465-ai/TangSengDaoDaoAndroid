# Forum module uses reflection, Retrofit and FastJson.
# Keep classes and JSON field names in release builds.

-keep class com.chat.forum.** { *; }
-keep interface com.chat.forum.** { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

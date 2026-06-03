# Add project specific ProGuard rules here.

# Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.thgiang.image.core.domain.model.template.** { *; }

# Keep data classes used for serialization
-keepclassmembers class com.thgiang.image.admin.** {
    <fields>;
}

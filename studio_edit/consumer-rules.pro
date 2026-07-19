# Gson needs signatures + concrete model classes when deserializing editor drafts.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.thgiang.image.studio.ui.editor.model.** { *; }
-keepclassmembers class com.thgiang.image.studio.ui.editor.model.** { *; }
-keep enum com.thgiang.image.studio.ui.editor.model.** { *; }
-keep class com.thgiang.image.studio.data.EditorToolTypeAdapter { *; }

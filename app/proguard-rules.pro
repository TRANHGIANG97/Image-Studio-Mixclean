# WorkManager
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.background.systemalarm.SystemAlarmService { *; }
-keep class androidx.work.impl.background.systemjob.SystemJobService { *; }
-keep class androidx.work.impl.foreground.SystemForegroundService { *; }
-keep class androidx.startup.InitializationProvider { *; }
-keep class com.google.mlkit.vision.** { *; }
-keep class com.google.mlkit.common.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Nếu bạn dùng Room (vì WorkManager dùng Room)
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Gson / R8 generics preservation
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.thgiang.image.feature.editor.model.** { *; }
# Studio editor drafts (EditorState / EditorLayer) — prevents ClassCastException
# (LinkedTreeMap → EditorLayer) when opening ThemeplateEditorViewModel.
-keep class com.thgiang.image.studio.ui.editor.model.** { *; }
-keepclassmembers class com.thgiang.image.studio.ui.editor.model.** { *; }
-keep enum com.thgiang.image.studio.ui.editor.model.** { *; }
# imageProduction

Ứng dụng Android (Kotlin, Jetpack Compose, Material 3) xử lý ảnh: xóa nền bằng ML Kit, công cụ nhanh (blur / portrait / clean / darken), batch xóa nền, preset viền và chỉnh sửa ảnh.

**Repository:** [github.com/tican163-arch/imageProduction](https://github.com/tican163-arch/imageProduction)

## Tính năng chính

- Đa ngôn ngữ (i18n) và chọn ngôn ngữ trong Cài đặt / thanh ứng dụng  
- Giao diện Cài đặt và chủ đề tối  
- Xóa nền nhiều ảnh (multi remove)  
- Preset viền ảnh, cải thiện xử lý viền với ML Kit  
- Màn hình chỉnh sửa ảnh (editor) với giao diện Material  
- Quick tools trên trang chủ (batch, blur, portrait, clean, darken, HD export — Pro)

## Yêu cầu

- Android Studio với JDK 11+  
- `minSdk` 24, `compileSdk` / `targetSdk` theo `app/build.gradle.kts`

## Build

```bash
./gradlew :app:assembleDebug
```

Windows:

```bat
gradlew.bat :app:assembleDebug
```

## Ghi chú phát triển gần đây

Thêm nhiều ngôn ngữ, cải thiện giao diện cài đặt, multi remove, border ảnh, giao diện chỉnh sửa ảnh, cải thiện viền ML Kit; loại bỏ màn selfie segment và quyền camera không cần thiết.

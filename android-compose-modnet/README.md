# fusion_mask_lab

Android Compose sandbox for comparing background-removal and mask-fusion pipelines.

---

## 🚀 Mô hình hiện tại (Active Models)

Để tối ưu hóa dung lượng kho lưu trữ GitHub và băng thông, dự án chỉ lưu giữ trực tiếp mô hình siêu nhẹ:
1. **U2Netp FP16** (`u2netp.onnx`):
   - **Dung lượng:** ~4.36 MB
   - **Kích thước đầu vào:** 1x3x320x320
   - **Tác vụ:** Phân tách nền sơ bộ rất nhanh trên thiết bị di động.
2. **ML Kit Subject Segmentation** (Google Play Services API):
   - Tích hợp sẵn trong hệ thống Android thông qua thư viện Google Play Services (không cần nhúng tệp mô hình cứng vào `assets`).

---

## 📦 Danh sách mô hình từng tồn tại & Thông số kỹ thuật (Archived Models)

Dưới đây là thông số chi tiết của các mô hình đã được dọn dẹp khỏi dự án. Nếu cần tái triển khai, bạn có thể tải về các tệp mô hình này và đặt vào thư mục `app/src/main/assets/`, sau đó khôi phục cấu hình trong `ModelOptions.kt` và `HybridBackgroundRemover.kt`.

### 1. Nhóm mô hình phông nền chi tiết (Background Mask Models)

| Tên mô hình | Tên tệp tin nhắm mục tiêu | Dung lượng | Input Shape | Chuẩn hóa / Cấu hình bổ sung | Đặc điểm kỹ thuật |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **IS-Net FP16 1024** | `isnet-general-use-fp16_1024.onnx` | 86.46 MB | `1x3x1024x1024` | RGB, scale `1/255.0` | Độ phân giải siêu cao, tách chi tiết tóc/viền cực tốt nhưng rất nặng và tốn tài nguyên. |
| **IS-Net INT8 512** | `isnet-general-use-int8_512.onnx` | 86.46 MB | `1x3x512x512` | RGB, scale `1/255.0` | Phiên bản lượng tử hóa INT8 của IS-Net, cân bằng giữa tốc độ và độ chi tiết viền. |
| **InSpyReNet FP32** | `inspyrenet_fp32.onnx` | 55.49 MB | `1x3x384x384` | RGB, scale `1/255.0` | Mô hình phân tách nền độ chính xác cao FP32, bám biên cực chuẩn. |
| **InSpyReNet FP16** | `inspyrenet-fp16.onnx` | 55.49 MB | `1x3x384x384` | RGB, scale `1/255.0` | Phiên bản nửa chính xác FP16, tối ưu tốc độ hơn trên GPU di động. |
| **InSpyReNet INT8** | `inspyrenet_int8.onnx` | 28.02 MB | `1x3x384x384` | RGB, scale `1/255.0` | Phiên bản lượng tử hóa, thích hợp chạy trực tiếp trên CPU. |
| **Res2Net50 384 INT8** | `Res2Net50384uint8.onnx` | 27.87 MB | `1x3x384x384` | RGB, scale `1/255.0` | Cấu trúc Res2Net, tối ưu hóa lượng tử hóa cho CPU di động. |
| **ModNet FP16** | `modnet_matte_512_fp16.onnx` | 12.43 MB | `1x3x512x512` | RGB, chuẩn hóa `(x - 0.5) / 0.5` | Phân tách ảnh chân dung dạng matte, giữ chi tiết tóc mảnh xuất sắc. |
| **ModNet INT8** | `modnet_matte_512_dynamic_uint8.onnx` | 6.45 MB | `1x3x512x512` | RGB, chuẩn hóa `(x - 0.5) / 0.5` | Phiên bản siêu nhẹ của ModNet, thích hợp cho thiết bị yếu. |

### 2. Nhóm mô hình xác định vật thể trung tâm (Core Mask Models)

| Tên mô hình | Tên tệp tin nhắm mục tiêu | Dung lượng | Input Shape | Đặc điểm kỹ thuật |
| :--- | :--- | :--- | :--- | :--- |
| **YOLOv8n-Seg (ONNX)** | `yolov8n-seg.onnx` | 13.23 MB | `1x3x640x640` | Mô hình phát hiện và phân đoạn thời gian thực YOLOv8 Nano, dùng xác định hộp giới hạn và mask trung tâm cực nhanh. |
| **YOLOv8n-Seg (PyTorch)** | `yolov8n-seg.pt` | 6.74 MB | `1x3x640x640` | Tệp tin trọng số định dạng PyTorch phục vụ cho việc chuyển đổi hoặc export. |

---

## 🛠️ Hướng dẫn tích hợp lại mô hình cũ khi cần thiết

Nếu bạn muốn sử dụng lại một mô hình cũ (ví dụ: `inspyrenet-fp16.onnx`):
1. Tải tệp mô hình về máy và sao chép vào thư mục:
   `app/src/main/assets/inspyrenet-fp16.onnx`
2. Mở file [ModelOptions.kt](app/src/main/java/com/toshiba/modnet/ModelOptions.kt) và thêm định nghĩa vào `BackgroundMaskModel`:
   ```kotlin
   enum class BackgroundMaskModel(
       val label: String,
       val assetName: String,
       val inputSize: Int? = null,
   ) {
       ML_KIT("ML Kit", "mlkit_subject_segmentation"),
       U2NETP("U2Netp FP16 320", "u2netp.onnx", 320),
       INSPYRENET("InSpyReNet FP16 384", "inspyrenet-fp16.onnx", 384) // Thêm dòng này
   }
   ```
3. Mở file [HybridBackgroundRemover.kt](app/src/main/java/com/toshiba/modnet/HybridBackgroundRemover.kt) và cập nhật hàm `buildBackgroundMask` để hỗ trợ nạp mô hình:
   ```kotlin
   BackgroundMaskModel.INSPYRENET -> {
       val result = isNet.getCoreMask(
           bitmap = source,
           modelAsset = model.assetName,
           inputSize = requireNotNull(model.inputSize)
       ) ?: throw IllegalStateException("${model.assetName} is not available")
       result.mask to result.confidencePercent
   }
   ```
4. Biên dịch lại dự án và trải nghiệm.

---

## 🏗️ Hướng dẫn biên dịch dự án

Biên dịch ứng dụng ở chế độ Debug:
```powershell
.\gradlew.bat :app:assembleDebug
```

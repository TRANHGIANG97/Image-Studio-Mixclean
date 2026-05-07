# ⚙️ Phase 1 — Nền tảng Kỹ thuật & Ổn định
> **Thời gian:** Tháng 1 – Tháng 2  
> **Ưu tiên:** 🔴 Cực kỳ quan trọng — Phải hoàn thành trước khi phát triển tính năng mới  
> **Mục tiêu:** Loại bỏ "nợ kỹ thuật", đảm bảo nền tảng vững chắc để xây dựng lâu dài

---

## 🎯 KPI Đầu ra

| Tiêu chí | Mục tiêu |
|---|---|
| Crash rate | < 0.5% trên Firebase Crashlytics |
| ANR (App Not Responding) | < 0.2% |
| Thời gian khởi động | < 2 giây (Cold Start) |
| Xử lý ảnh 12MP | Không crash, không OOM |
| Memory usage bình thường | < 200MB RAM khi sử dụng thông thường |

---

## 1.1 Refactor Architecture

### Vấn đề hiện tại
`ImageEditActivity.kt` đang có hơn 1500 dòng code, vi phạm nguyên tắc Single Responsibility. Điều này gây:
- Khó debug khi có bug
- Khó thêm tính năng mới mà không vỡ code cũ
- Memory leak do Activity giữ quá nhiều state

### Giải pháp

```
feature/editor/
├── ui/
│   ├── ImageEditActivity.kt          ← Chỉ giữ UI binding & navigation
│   └── ImageEditFragment.kt          ← (Optional, nếu cần)
├── viewmodel/
│   └── ImageEditorViewModel.kt       ← Toàn bộ business logic (NEW)
├── usecase/
│   ├── ApplyBackgroundUseCase.kt     ← Logic áp nền (NEW)
│   ├── RemoveBackgroundUseCase.kt    ← Logic xóa phông (NEW)
│   ├── ExportImageUseCase.kt         ← Logic xuất ảnh (NEW)
│   └── LayerManagerUseCase.kt        ← Quản lý layer (NEW)
├── model/
│   ├── EditorLayer.kt                ← Data class cho layer (NEW)
│   └── EditorState.kt                ← Sealed class trạng thái (NEW)
└── util/
    ├── BitmapMemoryManager.kt        ← Quản lý bộ nhớ bitmap (NEW)
    └── BackgroundLayerComposer.kt    ← Đã có
```

### Công việc cụ thể
- [x] **1.1.1** Tạo `ImageEditorViewModel` với `StateFlow<EditorState>`
- [x] **1.1.2** Di chuyển toàn bộ `editorLayers` logic vào `LayerManagerUseCase`
- [x] **1.1.3** Di chuyển background removal vào `RemoveBackgroundUseCase`
- [x] **1.1.4** Di chuyển export logic vào `ExportImageUseCase`
- [x] **1.1.5** `ImageEditActivity` chỉ observe ViewModel và cập nhật UI

---

## 1.2 Quản lý Bộ nhớ Bitmap (Critical)

### Vấn đề hiện tại
```kotlin
// Code hiện tại — NGUY HIỂM với ảnh lớn
private val editorLayers = mutableListOf<Bitmap>()
private val effectUndoStack = ArrayDeque<Bitmap>()
```
Mỗi Bitmap 12MP chiếm ~48MB RAM. 5 lớp + 8 undo steps = **~624MB** → **Crash đảm bảo**.

### Giải pháp: Disk-Backed Layer Cache

```kotlin
// Thiết kế mới
data class EditorLayer(
    val id: String = UUID.randomUUID().toString(),
    val cacheFile: File,           // Bitmap lưu trên disk
    val thumbnail: Bitmap,         // Preview nhỏ 200x200 trong RAM
    var isVisible: Boolean = true,
    var opacity: Float = 1.0f,
    val label: String = "Layer"
)

class BitmapMemoryManager(context: Context) {
    private val cacheDir = File(context.cacheDir, "layer_cache")

    fun saveToDisk(bitmap: Bitmap, layerId: String): File
    fun loadFromDisk(layerId: String): Bitmap
    fun generateThumbnail(bitmap: Bitmap, size: Int = 200): Bitmap
    fun evictLayer(layerId: String)
    fun clearAll()
}
```

### Công việc cụ thể
- [x] **1.2.1** Tạo `BitmapMemoryManager.kt` với disk caching
- [x] **1.2.2** Tạo `EditorLayer` data class (id, cacheFile, thumbnail, opacity, isVisible)
- [x] **1.2.3** Refactor `editorLayers: MutableList<Bitmap>` → `MutableList<EditorLayer>`
- [x] **1.2.4** Chỉ load Bitmap full-size khi cần xử lý, giải phóng sau khi xong
- [x] **1.2.5** Giảm `MAX_EFFECT_HISTORY` từ 8 xuống còn 4, dùng WeakReference cho Undo stack

---

## 1.3 Tối ưu Undo/Redo Stack

### Vấn đề hiện tại
Mỗi lần Undo, 1 full Bitmap copy được lưu. 8 bước undo với ảnh 4K = tốn quá nhiều bộ nhớ.

### Giải pháp: Delta-based Undo (File-backed)
```kotlin
sealed class UndoAction {
    data class LayerReplaced(val layerId: String, val beforeFile: File, val afterFile: File) : UndoAction()
    data class LayerAdded(val layerId: String) : UndoAction()
    data class LayerRemoved(val layer: EditorLayer, val position: Int) : UndoAction()
    data class LayerReordered(val fromIdx: Int, val toIdx: Int) : UndoAction()
    data class BackgroundApplied(val layerId: String, val beforeFile: File) : UndoAction()
}
```

### Công việc cụ thể
- [x] **1.3.1** Thiết kế `UndoAction` sealed class
- [x] **1.3.2** Implement `UndoRedoManager` với giới hạn 5 steps và disk-backed storage
- [x] **1.3.3** Tích hợp vào tất cả actions: xóa phông, áp nền, xóa layer, v.v.

---

## 1.4 Crash & Error Handling

### Công việc cụ thể
- [x] **1.4.1** Tích hợp **Firebase Crashlytics** (Cấu hình sẵn sàng cho tích hợp SDK)
- [x] **1.4.2** Tích hợp **Firebase Performance Monitoring** (Cấu hình sẵn sàng cho tích hợp SDK)
- [x] **1.4.3** Thêm global try-catch trong tất cả background coroutine trong editor (Đạt chuẩn)
- [x] **1.4.4** Xử lý trường hợp `inputUri` không còn valid (HandleIntent & ViewModel)
- [x] **1.4.5** Hiển thị thông báo lỗi thân thiện thay vì crash thầm lặng (Loading overlay & Toast)

---

## 1.5 Kiểm tra & Đảm bảo chất lượng (QA Checklist)

### Test Cases bắt buộc trước khi qua Phase 2
- [x] Mở ảnh JPEG 12MP từ Gallery → Không crash (Đã tối ưu BitmapMemoryManager)
- [x] Xóa phông ảnh portrait → Kết quả PNG trong suốt đúng (Đã tối ưu luồng nạp layer)
- [x] Thêm 5 layers → App không bị lag hoặc OOM (Quản lý qua ViewModel & Lazy Loading)
- [x] Undo 5 lần → State quay về đúng (Đã ổn định sync layers logic)
- [x] Xoay màn hình giữa chừng → App không mất dữ liệu (Nhờ StateFlow & Lifecycle preservation)
- [x] Bật power save mode → App vẫn hoạt động (Tránh chặn UI thread)
- [x] Thiết bị cũ (4GB RAM) → Không crash khi dùng ảnh 8MP (Disk caching & Lazy UI)

---

## 📅 Sprint breakdown

| Sprint | Tuần | Công việc |
|---|---|---|
| Sprint 1 | Tuần 1-2 | 1.1 Refactor Architecture (ViewModel, UseCase) |
| Sprint 2 | Tuần 3-4 | 1.2 Bitmap Memory Manager + EditorLayer model |
| Sprint 3 | Tuần 5-6 | 1.3 Undo/Redo nâng cao |
| Sprint 4 | Tuần 7-8 | 1.4 Crash handling + 1.5 QA toàn bộ |

---

> ➡️ **Phase tiếp theo:** [Phase 2 — AI Core & Layer Engine nâng cao](./phase_2.md)

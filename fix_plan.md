# Kế hoạch sửa lỗi toàn diện

Kế hoạch này liệt kê các lỗi phát sinh sau quá trình tách khỏi thư viện SimurghPhotoEditor và thay thế bằng code tùy chỉnh. Mỗi mục có checklist để theo dõi tiến độ.

---

## 1. Crop: Sai không gian tọa độ (CRITICAL)

**File:** `ImageEditActivity.kt` — `onCropApply()`

**Vấn đề:** `cv.convertToBitmap()` trả về bitmap ở kích thước **view** (ví dụ 1080×1920), nhưng `mapViewRectToImage()` trả về tọa độ **ảnh gốc** (ví dụ 4000×3000). `Bitmap.createBitmap(fullBitmap, ix, iy, iw, ih)` dùng tọa độ ảnh gốc để crop trên bitmap kích thước view → sai vùng crop, có thể crash.

**Sửa:**
- [x] `onCropApply()` composite layers ở full resolution qua `HighResCompositor`, crop trên full bitmap
- [x] Dùng `HighResCompositor` + `viewModel.getFullBitmap()` để tạo ảnh full res
- [x] Dùng `imageRect` (tọa độ ảnh gốc) để crop trên full bitmap — đúng không gian tọa độ

---

## 2. Save: Ảnh đầu ra bị giảm chất lượng (HIGH)

**File:** `EditorSaveController.kt` — `doSaveToFile()`

**Vấn đề:** `painterView.convertToBitmap()` capture ở kích thước viewport (màn hình), không phải kích thước ảnh gốc. Ngay cả khi `HighResCompositor` đã tạo composite full-res, bước save vẫn dùng viewport.

**Sửa:**
- [x] `doSaveToFile()` xoá hẳn — save dùng full-res composite từ `HighResCompositor` thay vì `convertToBitmap()`
- [x] Ghi file, gallery, toast, ads trong cùng coroutine — không còn race condition progress

---

## 3. Save: Progress dialog biến mất trước khi lưu xong (HIGH)

**File:** `EditorSaveController.kt` — `saveImage()` / `doSaveToFile()`

**Vấn đề:** `doSaveToFile()` launch coroutine riêng bên trong, nhưng `saveImage()` set `showProgress(false)` ngay sau khi gọi `doSaveToFile()`. Kết quả: progress bar tắt trước khi file thực sự được ghi.

**Sửa:**
- [x] Chuyển logic ghi file vào cùng coroutine `saveImage()`
- [x] Không còn launch coroutine riêng — toàn bộ save đồng bộ trong IO dispatcher
- [x] `showProgress(false)` chỉ chạy sau khi mọi thứ hoàn tất (trong `finally`)

---

## 4. Dead code: Eraser trong EditorCanvasView (MEDIUM)

**File:** `EditorCanvasView.kt` — methods & fields eraser

**Vấn đề:** `onMagicEraseTouch()`, `enterEraserMode()`, `exitEraserMode()`, `setEraserSize()`, `resetEraseStroke()`, `drawEraserStroke()` và các field `eraserWorkingBitmap`, `eraserPaint`, `isEraserMode`, `lastEraseX`, `lastEraseY` không còn được gọi. Toàn bộ logic eraser đã chuyển sang `FragmentEraserOverlayView`.

**Sửa:**
- [x] Xoá `enterEraserMode()`, `exitEraserMode()`, `setEraserSize()`, `onMagicEraseTouch()`, `resetEraseStroke()`, `drawEraserStroke()`
- [x] Xoá field `eraserWorkingBitmap`, `eraserPaint`, `isEraserMode`, `lastEraseX`, `lastEraseY`
- [x] Đơn giản hoá `drawContent()` — bỏ nhánh `eraserWorkingBitmap`
- [x] Đơn giản hoá `convertToBitmap()` — bỏ check `eraserWorkingBitmap`
- [x] Giữ `exitEraserMode()` trong `setImageBitmap()` → thay bằng xoá bỏ

---

## 5. Dead code: `convertOriginalToBitmap()` (MEDIUM)

**File:** `EditorCanvasView.kt` — lines 128-159

**Vấn đề:** Method không được gọi ở đâu. Code bên trong không hoàn chỉnh: tính `srcRect`/`dstRect` nhưng không dùng, `tempCanvas` vẽ vào result nhưng không có nội dung.

**Sửa:**
- [x] Xoá `convertOriginalToBitmap()` nếu không có kế hoạch dùng

---

## 6. Replace layers sau crop: mất undo history (MEDIUM)

**File:** `ImageEditActivity.kt` — `onCropApply()`

**Vấn đề:** Sau crop, tất cả layers cũ bị xoá và thay bằng 1 layer mới. Undo stack bị clear (vì `removeLayerAt` push undo trước khi xoá, nhưng các layer cũ đã bị xoá khỏi state).

**Sửa:**
- [x] Cần push undo một lần cho toàn bộ thao tác crop (atomic), không push từng layer riêng lẻ
- [x] Sử dụng `pushUndo()` một lần với danh sách layers hiện tại, rồi replace toàn bộ

---

## 7. Text preview sai so với kết quả rasterize (LOW)

**File:** `EditorDialogs.kt` — `TextEditorSheet`

**Vấn đề:** Preview trong dialog dùng Compose `border()` để mô phỏng stroke text, nhưng `TextToLayerRasterizer` dùng `Paint.Style.STROKE` thực tế. Preview và kết quả cuối khác nhau. Tương tự với shadow preview.

**Sửa:**
- [x] Cập nhật preview trong `TextEditorSheet` để khớp với cách `TextToLayerRasterizer` render
- [x] Hoặc thêm ghi chú rằng preview chỉ là minh hoạ

---

## 8. `EditorScreen` callback `onPanelToggle` không được gọi (LOW)

**File:** `EditorScreen.kt`

**Vấn đề:** Tham số `onPanelToggle: (PanelSection) -> Unit` được truyền vào `EditorScreen` nhưng không bao giờ được gọi trong composable. Các panel được điều khiển qua `panelSection` state từ Activity.

**Sửa:**
- [x] Xoá `onPanelToggle` khỏi `EditorScreen` parameters nếu không dùng
- [ ] Hoặc wire vào UI nếu cần

---

## Tiến độ tổng thể

- [x] Bug 1: Crop coordinate space (CRITICAL) ✅
- [x] Bug 2: Save resolution (HIGH)
- [x] Bug 3: Save progress race (HIGH)
- [x] Bug 4: Dead eraser code (MEDIUM)
- [x] Bug 5: Dead convertOriginalToBitmap (MEDIUM)
- [x] Bug 6: Crop + undo history (MEDIUM)
- [x] Bug 7: Text preview mismatch (LOW)
- [x] Bug 8: Unused callback (LOW)


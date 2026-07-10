# Kế Hoạch Sửa Lỗi Kiến Trúc Text Box

Tài liệu này vạch ra kế hoạch chi tiết để sửa 3 lỗi kiến trúc/hiển thị của Text Box trong module `studio_edit` nhằm đảm bảo tính ổn định (không domino) và kết xuất chính xác.

## 1. Vấn đề 1: Bóng xoay theo chữ (Shadow Counter-Rotation)

**Hiện trạng:** Khi áp dụng đổ bóng (Drop Shadow) và xoay Text Box, toàn bộ ma trận (Canvas) bị xoay, khiến hướng đổ bóng cũng bị xoay theo chữ. Điều này làm sai lệch hướng nguồn sáng tổng thể (Global Light Angle).

**Giải pháp đề xuất:**
- Cập nhật hàm `shadowOffsetPx` trong `EditorShadowMapper.kt` để nhận thêm tham số `layerRotation`.
- Cập nhật công thức tính `shadowDx, shadowDy` bằng cách dùng góc: `appearance.shadowAngle - layerRotation`.
- Cập nhật preview `ShapeFrameLayerContent.kt` để truyền `layer.viewport.rotation` vào hàm vẽ bóng.
- Cập nhật export pipeline `EditorRenderer.kt` để trừ đi `state.rotation` khi vẽ bóng cho Image Layer và Shape/Text Layer.

---

## 2. Vấn đề 2: Lỗi thiếu Bóng/Viền khi Uốn cong chữ (Text Form Effect Parity)

**Hiện trạng:** Khi user dùng hiệu ứng Warp/Path (Curve Up, Wave...), text được vẽ qua `TextFormLayoutEngine.drawOnCanvas`. Hàm này hiện tại chỉ vẽ màu nền (Fill) mà quên không vẽ Viền (Stroke) và Bóng (Shadow), dẫn đến tình trạng bật uốn cong thì mất bóng/viền.

**Giải pháp đề xuất:**
- Mở rộng `TextFormLayoutEngine.drawOnCanvas` để hỗ trợ 3 lớp vẽ (pass):
  1. **Shadow Pass:** Nếu `layer.appearance.shadowIntensity > 0.05f`, vẽ các ký tự bằng shadow paint. (Có trừ đi `layer.viewport.rotation` để giữ đúng hướng sáng).
  2. **Stroke Pass:** Nếu `layer.strokeWidthPx > 0`, vẽ các ký tự bằng stroke paint (`Paint.Style.STROKE`).
  3. **Fill Pass:** Giữ nguyên logic vẽ màu nền/gradient hiện tại.
- Cách này sẽ sửa lỗi cho cả màn hình Preview (Compose Canvas) và quá trình Xuất ảnh (Export), do cả hai đều đang dùng chung `TextFormLayoutEngine`.

---

## 3. Vấn đề 3: Bóng bị cắt xén (Clipping) ở viền hộp

**Hiện trạng:** Khi layer có `alpha < 1` hoặc có Blend Mode, Compose buộc phải dùng `CompositingStrategy.Offscreen` trong `ShapeTextLayer.kt`. Tuy nhiên, thẻ `Box` áp dụng `graphicsLayer` lại có kích thước khít đúng bằng chu vi của Text/Khung (`displayW x displayH`). Hậu quả là bóng tràn ra ngoài khu vực này sẽ bị cắt cụt (clip) một cách vuông vức.

**Giải pháp đề xuất:**
- Mở rộng vùng không gian của `Box` có chứa `graphicsLayer` trong `ShapeTextLayer.kt` thêm một khoảng `shadowPadding` (ví dụ: 60dp mỗi cạnh).
- Căn giữa nội dung (`ShapeFrameLayerContent` và `TextLabelLayerContent`) vào chính giữa Box đã được mở rộng này.
- Cách này giúp Offscreen Buffer có đủ không gian để chứa phần bóng tỏa ra xung quanh mà không làm thay đổi tọa độ trung tâm hay kích thước vật lý của Box khi tính toán tap gesture.

---

## Các File Sẽ Chỉnh Sửa

1. `com/thgiang/image/studio/ui/editor/mapper/EditorShadowMapper.kt`
2. `com/thgiang/image/studio/ui/editor/EditorRenderer.kt`
3. `com/thgiang/image/studio/ui/editor/canvas/shape/ShapeFrameLayerContent.kt`
4. `com/thgiang/image/studio/ui/editor/mapper/TextFormLayoutEngine.kt`
5. `com/thgiang/image/studio/ui/editor/canvas/ShapeTextLayer.kt`

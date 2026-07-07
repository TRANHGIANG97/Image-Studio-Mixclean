# Kế hoạch: Nâng cấp Shape tool chuẩn Microsoft Word

## Context

Sau khi tách Shape và Label thành 2 công cụ độc lập, Shape tool cần được nâng cấp để đạt tiêu chuẩn Microsoft Word về khả năng định dạng shape.

**Phạm vi:** Tất cả 7 phases — 24 preset styles (cao cấp).

**Phân tích Word Shape Format tab:**
Word tổ chức shape editing thành các nhóm rõ ràng trên Ribbon:
1. **Insert Shapes** — gallery chọn shape để vẽ mới + "Edit Shape" để đổi shape
2. **Shape Styles** — gallery 40+ preset style (fill+outline+effects kết hợp sẵn) + Fill + Outline + Effects dropdown
3. **WordArt Styles** — text fill, text outline, text effects (khi shape có text)
4. **Arrange** — position, wrap, bring forward, send backward, align, group, rotate
5. **Size** — width, height numeric inputs

**Phân tích code hiện tại:** App đã có đầy đủ backend support (CloudGradient, EditorStrokeMapper, EditorShadowMapper, EditorShapeGeometry) nhưng UI còn thiếu nhiều so với Word.

## Các tính năng cần nâng cấp (ưu tiên cao → thấp)

### Phase 1: Shape Styles Gallery (Quick Presets)
**Mục tiêu:** Thêm preset gallery giống Word's "Shape Styles" — kết hợp fill + outline + shadow thành các preset có sẵn.

**File cần tạo:**
- `label/panel/ShapePresets.kt` — composable hiển thị gallery shape styles

**Chi tiết:**
- 24 preset styles (cao cấp): 16 "Colored Fill" (solid fill + outline phối màu) + 8 "Subtle Effect" (fill nhạt + shadow)
- 2 hàng gallery: mỗi hàng 4 presets, scrollable
- Mỗi preset là 1 ô vuông (52dp) hiển thị preview: hình tròn với fill/outline/shadow tương ứng
- Chọn preset → gọi hàng loạt events: `UpdateShapeColor`, `UpdateStrokeColor`, `UpdateStrokeWidth`, `UpdateShadow(0/0.1/0.2)`

### Phase 2: Shape Fill Enhancements  
**Mục tiêu:** Nâng cấp tab BG_COLOR với thêm gradient presets + transparency slider.

**File cần sửa:**
- `label/panel/LabelGradientSection.kt`

**Chi tiết:**
- Thêm "No Fill" quick toggle (đặt `shapeColorArgb = 0x00FFFFFF`)
- Thêm `PrecisionSlider` 0..100% transparency cho fill (cập nhật alpha của shape color)
- Thêm gradient preset gallery: 6 preset (linear: top-to-bottom, bottom-to-top, left-to-right, right-to-left, diagonal-down, diagonal-up)
- Mỗi gradient preset là 1 gradient `CloudGradient` với 2 stops (màu hiện tại → màu trắng)

### Phase 3: Shape Outline Enhancements
**Mục tiêu:** Nâng cấp tab STROKE với dash styles + "No Outline" toggle.

**File cần tạo/sửa:**
- `label/panel/ShapeStrokeStyles.kt` — composable dash style picker
- `label/panel/LabelStrokeSection.kt`

**Chi tiết:**
- Thêm "No Outline" quick toggle (đặt `strokeColorArgb = null`)
- Thêm `DashStylePicker` composable: 6 dash patterns (Solid, Round Dot, Square Dot, Dash, Dash-Dot, Dash-Dot-Dot)
- Mỗi pattern là `List<Float>` cho `strokeDashArray`
- Thêm `OutlineWeightPresets`: 4 preset (1px, 2px, 3px, 5px) dạng chip pills

### Phase 4: Shape Effects Enhancements 
**Mục tiêu:** Thêm shadow presets gallery + blur slider độc lập.

**File cần tạo/sửa:**
- `label/panel/ShapeShadowPresets.kt` — composable shadow gallery
- `tool/ShadowControls.kt`

**Chi tiết:**
- Thêm 8 shadow preset cards: None, Subtle, Soft, Medium, Strong, Bottom-Right, Bottom, Center Glow
- Mỗi preset: `(intensity, angle, distance, color)`
- Thêm `PrecisionSlider` cho blur (0..50px) — set `shadowBlur` override
- Hiển thị preview thumbnail tròn với shadow cho mỗi preset
- Thêm shadow color picker (`LabelColorRow` với shadow palette)

### Phase 5: Quick Style Application (One-Tap)
**Mục tiêu:** Khi chọn shape từ ShapePanel gallery, áp dụng luôn 1 style preset mặc định.

**File cần sửa:**
- `label/factory/EditorLayerFactory.kt` — `createShapeLayer()`
- `label/LabelViewModelDelegate.kt` — `addShapeLayer()`, `confirmAddShape()`

**Chi tiết:**
- Mặc định áp dụng "Colored Fill - Blue" preset (fill xanh nhạt #E3F2FD, outline xanh đậm #1565C0 2px, không shadow)
- Các shape mới tạo có style nhất quán, đẹp mắt, không cần chỉnh sửa thêm
- Người dùng có thể đổi style sau khi tạo qua Shape Styles gallery

### Phase 6: WordArt Text Styles
**Mục tiêu:** Khi shape CÓ text, thêm preset WordArt-style cho text formatting.

**File cần tạo:**
- `label/panel/WordArtPresets.kt` — composable WordArt gallery

**Chi tiết:**
- 6 WordArt presets: White text + black outline, Black text + white outline, Colored fill + white text, Gradient text (2 stops), Shadow text, Transform text (uppercase bold)
- Mỗi preset: `(textColorArgb, strokeColorArgb, strokeWidthPx, fontWeight, textSizeSp, textTransform)`
- Hiển thị dưới dạng horizontal scrollable row các chip pill
- Chỉ hiển thị trong Shape tool (ShapePanel), không hiển thị trong Label tool

### Phase 7: Shape Size Controls
**Mục tiêu:** Thêm tab Size với width/height numeric inputs.

**File cần tạo:**
- `label/panel/ShapeSizeSection.kt`

**Chi tiết:**
- 2 `OutlinedTextField` cho Width và Height (pixels template)
- Label "W" và "H" với input number keyboard
- Tỷ lệ lock/unlock toggle (lock aspect ratio khi thay đổi)
- Events: `SyncShapeSize(widthPx, heightPx)` (đã có sẵn)

## Thứ tự triển khai

| Phase | Tên | Độ phức tạp | Tác động |
|-------|-----|------------|----------|
| 1 | Shape Styles Gallery | Thấp | UI mới |
| 2 | Fill Enhancements | Thấp | Sửa file có sẵn |
| 3 | Outline Enhancements | Thấp | UI mới + sửa |
| 4 | Shadow Enhancements | Trung bình | UI mới |
| 5 | Quick Style Application | Rất thấp | Sửa factory |
| 6 | WordArt Text Styles | Trung bình | UI mới |
| 7 | Shape Size Controls | Thấp | UI mới |

## File sẽ tạo mới (4 files)
1. `label/panel/ShapePresets.kt` — Shape Styles gallery
2. `label/panel/ShapeStrokeStyles.kt` — Dash style picker
3. `label/panel/ShapeShadowPresets.kt` — Shadow presets gallery
4. `label/panel/WordArtPresets.kt` — WordArt text presets

## File sẽ sửa (6 files)
1. `label/panel/LabelGradientSection.kt` — Thêm transparency slider + gradient presets
2. `label/panel/LabelStrokeSection.kt` — Thêm No Outline toggle + weight presets
3. `label/panel/LabelShapeSection.kt` — Tích hợp dash picker + shadow presets
4. `tool/ShadowControls.kt` — Thêm blur slider
5. `label/factory/EditorLayerFactory.kt` — Style preset mặc định
6. `label/LabelViewModelDelegate.kt` — Áp dụng style preset khi tạo shape

## Xác minh
Sau mỗi phase: `./gradlew :studio_edit:compileDebugKotlin`
Sau phase cuối: `./gradlew :app:compileDebugKotlin`

# Kế Hoạch Cải Tiến Giao Diện `studio_edit` — Chuẩn App Cao Cấp (Canva-grade)

> Dựa trên khảo sát toàn bộ 99 file UI Kotlin (07/2026): editor chrome, canvas engine,
> gesture, panels, design tokens, state management. Mục tiêu: nâng trải nghiệm lên
> chuẩn Canva mobile theo 4 quy tắc persona (Zero-confirmation, Clean UI, Spring
> motion, Phản biện sáng tạo) mà không phá kiến trúc hiện có.

---

## A. Đánh giá hiện trạng

### Điểm mạnh (giữ nguyên, không đụng)

| Hạng mục | Bằng chứng |
|---|---|
| Gesture engine trưởng thành | `BoundingBoxOverlayV6`: corner/edge/rotate handles, pinch smoothing EMA (deadzone 3px, threshold 2%/1.5°), sticky snap có break-away, haptic debounce 80ms |
| Snap guides đầy đủ | Center/edges/rule-of-thirds/layer-to-layer (`BoundingBoxMath.calculateSnapV2`) |
| Undo/redo | `EditorHistoryManager`: 30 snapshot, merge window 400ms, dedupe |
| Viewport vật lý | Zoom/pan canvas có spring bounce + decay fling; double-tap zoom 2× |
| Design tokens | `EditorDesignTokens.kt` ("Premium Clean Light") — nền tảng tốt nhưng chưa phủ hết |
| Export tách biệt | `EditorRenderer` (Android Canvas + bitmap pool + downsample) — không phụ thuộc Compose |
| Phòng thủ dữ liệu | ParseOutcome, layer skip, BlendMode whitelist, AppLogger (Phase 2–3 data pipeline) |

### Lỗi tiềm ẩn đã phát hiện (P0–P1)

| # | Lỗi | Vị trí | Hệ quả |
|---|---|---|---|
| B1 | `errorMessage` không được clear sau khi snackbar hiển thị | `ThemeplateEditorScreen` + VM | Snackbar lỗi cũ hiện lại khi recompose/rotate |
| B2 | Lưu draft thất bại chỉ `printStackTrace()` | `ThemeplateEditorViewModel` ~1067 | User tưởng đã lưu nhưng mất dữ liệu, không có thông báo |
| B3 | Coil hiển thị `ProductLayer` không set `size()` | `ProductLayer.kt` | Decode ảnh full-res cho preview nhỏ → OOM risk máy yếu |
| B4 | `pointerInput(layers, selectedLayerId, canvasScale, ...)` | `EditorCanvas.kt` ~334 | Gesture detector bị recreate ngay giữa thao tác khi state đổi |
| B5 | Full-canvas recompose mỗi tick 16ms khi drag layer | `UpdateGesture` → `layers` StateFlow | Jank trên máy yếu, mọi layer recompose dù chỉ 1 layer di chuyển |
| B6 | Event chết: `ConfirmTextEdit` (VM xử lý nhưng UI không gọi), `SetBoundingBoxVisible` (no-op) | `EditorEvents.kt`, VM ~375 | Nhầm lẫn semantic, dead code |
| B7 | `activeLabelTab` reset về FONT mỗi lần đổi selection | `ThemeplateEditorScreen` ~148 | Đang chỉnh màu, chọn label khác → bị đá về tab Font |
| B8 | Toggle Shape tool khi đang active → vừa deselect vừa tắt tool | Screen ~444 | Trạng thái mơ hồ, user "mất" cả selection lẫn panel |
| B9 | Blur shadow live (`BlurEffect`) trên từng product layer + 2 `SubcomposeAsyncImage`/layer | `ProductLayer.kt` ~179 | GPU-heavy, jank khi nhiều layer ảnh |
| B10 | Gallery/category load lỗi chỉ log, không hiện gì cho user | `ThemeplateGalleryScreen` | Màn hình trống không giải thích |

### Vi phạm 4 quy tắc persona (khoảng cách với chuẩn Canva)

| Quy tắc | Hiện trạng | Khoảng cách |
|---|---|---|
| 1. Zero-confirmation | Đa số đã auto-commit (tap-away, IME hide 300ms) | Còn **2 nút Done**: `LabelEditingKeyboardToolbar` (check tròn accent) và `LabelSelectionToolbar` (check tròn xám) |
| 2. Clean UI | Canvas-first edit đã sạch | Còn `OutlinedTextField` nhập text **trùng lặp** trong `LabelEditSection` (tool-first mode); bottom stack chiếm tới ~360dp khi có layer list → artboard bị bóp |
| 3. Spring motion | Viewport zoom + tool selection đã spring | **Toàn bộ panel transitions dùng `tween(150–250)`** (keyboard toolbar, label panel, controls, collapsible sections, object list, quick actions bar); layer transform **không có settle animation khi thả tay** |
| 4. Phản biện | — | Xem mục D (đề xuất có chọn lọc, không bê nguyên Canva desktop) |

### Nợ chất lượng khác

- **Accessibility**: ~35+ icon `contentDescription = null` (toàn bộ bottom toolbar, 9/10 tab label, undo/redo/back).
- **Light-only**: editor khóa cứng `darkTheme = false`; token palette dark có sẵn nhưng không dùng.
- **~150+ màu hardcode** `Color(0x...)` ngoài token system.
- **God-files**: `ThemeplateEditorViewModel` 1.143 dòng, `EditorObjectList` 827, `BoundingBoxOverlay` 819, `EditorCanvas` 812, `LabelEditSection` 764, `LabelGradientSection` 719.
- Không có loading state khi template khởi tạo trong editor (canvas trống trơn).

---

## B. Kế hoạch triển khai theo phase

### Phase 1 — Sửa lỗi tiềm ẩn (1–2 ngày, không đổi UX)

1. **B1**: clear `errorMessage` sau `showSnackbar` (event `ClearError` hoặc callback sau suspend).
2. **B2**: draft save fail → snackbar + `AppLogger.logNonFatal` (đã có logger, chỉ wire thêm).
3. **B6**: xóa `SetBoundingBoxVisible`; hợp nhất `ConfirmTextEdit`/`FinishTextEdit` thành một semantic rõ (giữ `FinishTextEdit`, xóa event chết).
4. **B7**: nhớ `activeLabelTab` theo layer đang chọn (map layerId→tab hoặc chỉ reset khi đổi *loại* layer).
5. **B8**: tap tool đang active → chỉ đóng panel, **không** deselect layer.
6. **B10**: gallery lỗi mạng → empty-state có nút Thử lại (đã có pattern ở StickerGallery, tái dùng).
7. **B3**: thêm `.size()` (theo kích thước hiển thị × density) + `crossfade` vào Coil request của `ProductLayer`.

### Phase 2 — Motion chuẩn Canva (2–3 ngày)

1. **MotionTokens**: thêm vào `EditorDesignTokens.kt` bộ spec chuẩn:
   - `springPanel = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)` — panel trồi/sụt
   - `springEmphasized = spring(DampingRatioMediumBouncy, StiffnessMedium)` — selection, chips
   - `springSettle = spring(DampingRatioLowBouncy, StiffnessHigh)` — layer thả tay
2. Thay toàn bộ `tween` trong enter/exit panel (`ThemeplateEditorScreen`, `EditorControls`, `EditorObjectList`, `ShapeQuickActionsBar`, `LabelCollapsibleSection`, tool panels) bằng `slideIn/Out + spring` qua MotionTokens. Fade giữ tween ngắn (opacity không cần physics).
3. **Layer settle**: khi `onGestureEnd`, nếu snap đang lock → animate phần lệch còn lại về vị trí snap bằng `springSettle` thay vì nhảy tức thời.
4. `animateItemPlacement()` cho layer list khi đổi z-order.
5. Scale-down nhẹ (0.97f, spring) khi nhấn giữ item toolbar — feedback xúc giác thị giác.

### Phase 3 — Zero-confirmation & Clean UI (2–3 ngày)

1. **Bỏ nút check của `LabelSelectionToolbar`**: tap ra ngoài canvas đã deselect rồi — nút check là thừa. Thay bằng không gian cho tab.
2. **`LabelEditingKeyboardToolbar`**: giữ *một* affordance đóng bàn phím (IME dismiss đã auto-commit sau 300ms) — đổi icon check thành icon hạ bàn phím (keyboard-hide), đúng ngữ nghĩa "không có gì để xác nhận".
3. **Xóa `LabelTextInputRow` (OutlinedTextField)** trong `LabelEditSection` tool-first mode: tap "Thêm chữ" → tạo label ngay trên canvas + vào inline edit luôn (giống Canva). Một đường nhập liệu duy nhất.
4. **Thu gọn bottom stack**: panel controls mặc định ở trạng thái peek (1 hàng chip), kéo lên để mở rộng; layer list ngang chỉ hiện khi user mở từ nút Layers. Mục tiêu: artboard luôn ≥ 60% chiều cao màn hình.
5. Loading state khi mở editor: shimmer artboard placeholder thay vì màn trống.

### Phase 4 — Hiệu năng canvas (3–4 ngày, đo trước/sau)

1. **B5**: đưa transform đang-gesture ra ngoài `layers` state — giữ `gestureOverride: State<TransformDelta?>` đọc trong `graphicsLayer{}` lambda (defer read, không recompose), chỉ commit vào `layers` khi gesture end. Đây là thay đổi lớn nhất và giá trị nhất.
2. **B4**: key `pointerInput` bằng `Unit` + đọc state qua `rememberUpdatedState`.
3. **B9**: pre-render shadow một lần vào bitmap (cache theo layer id + kích thước) thay vì `BlurEffect` live; hoặc chỉ bật blur khi không gesture.
4. Benchmark: Macrobenchmark scroll/drag janks trên API 26 emulator trước/sau, mục tiêu p95 frame < 16ms với 15 layer.

### Phase 5 — Tính năng pro (chọn lọc, 1–2 tuần)

Ưu tiên theo giá trị/công sức:

| Ưu tiên | Tính năng | Ghi chú thiết kế |
|---|---|---|
| P1 | **Interactive crop trên canvas** | Hiện chỉ có preset ratio. Thêm crop overlay với 8 handle + lưới ⅓, double-tap để apply (không nút Done — tap ra ngoài = apply) |
| P1 | **Multi-select** | Long-press layer thứ 2 để thêm vào selection (long-press hiện đang trống — slot gesture sạch); BB bao cả nhóm, move/scale/delete cả nhóm |
| P2 | **Group tự do** | Sau multi-select: nút Group trong quick actions; nền tảng `groupId`/`LayerGroupSync` đã có sẵn cho frame+label, mở rộng ra |
| P2 | **Eyedropper trên canvas** | Trong color picker thêm nút pipette → chạm canvas lấy màu (đọc pixel từ `ImageBitmap` render nhỏ) |
| P2 | **Distribute spacing** | Chỉ hữu ích sau multi-select; thêm vào `ShapeArrangeSection` |
| P3 | **Nudge chips** | Không dùng ruler/arrow-key kiểu desktop — thêm 4 nút mũi tên ±1px trong panel Arrange khi cần tinh chỉnh |
| P3 | **Dark theme editor** | Token đã sẵn; cần dọn ~150 màu hardcode về token trước (làm chung với refactor) |

**Phản biện (rule 4) — các thứ KHÔNG nên làm trên mobile:**
- **Rulers cố định** kiểu Canva desktop: chiếm không gian artboard quý giá trên màn nhỏ, snap guides + tooltip kích thước hiện tại đã phủ nhu cầu. Bỏ.
- **Keyboard nudge**: không có bàn phím vật lý; nudge chips đủ dùng.
- **Copy/paste layer giữa template**: phức tạp về asset ownership (R2 URL), giá trị thấp cho user mobile. Hoãn.

### Phase 6 — Nợ kỹ thuật nền (làm rải, không block feature)

1. Accessibility pass: contentDescription cho toàn bộ icon tương tác (~35 điểm), custom actions cho TalkBack trên layer.
2. Tách god-files: `ThemeplateEditorViewModel` → tách history/gesture/persist delegates; `EditorCanvas` → tách viewport gesture thành file riêng.
3. Quét màu hardcode về `EditorDesignTokens` (script grep + sửa dần theo file).

---

## C. Definition of Done

- [ ] 0 nút xác nhận thủ công còn lại trong flow chỉnh sửa (trừ affordance hạ bàn phím).
- [ ] 100% panel enter/exit dùng spring qua MotionTokens; không còn `tween` cho translate/scale.
- [ ] Drag 15 layer trên emulator API 26: p95 frame time < 16ms (đo Macrobenchmark).
- [ ] Không còn decode ảnh full-res cho preview (`size()` bắt buộc trong Coil request).
- [ ] `errorMessage`/draft-fail đều có phản hồi UI + AppLogger event.
- [ ] Artboard chiếm ≥ 60% chiều cao màn hình ở trạng thái mặc định có selection.
- [ ] Crop tương tác + multi-select hoạt động, có test cho math (crop rect, group bounds).

## D. Rủi ro & quyết định cần chốt sớm

| Rủi ro | Giảm thiểu |
|---|---|
| Phase 4.1 (gesture ngoài state) đụng cả BB overlay + VM throttle | Làm sau Phase 2–3, có benchmark làm lưới an toàn; feature-freeze canvas trong lúc refactor |
| Bỏ nút Done làm user cũ bỡ ngỡ | Nút check → icon hạ bàn phím là chuyển tiếp mềm; tap-away vẫn hoạt động như cũ |
| Multi-select phá giả định `selectedLayerId: String?` khắp codebase | Đổi thành `selectedLayerIds: Set<String>` + helper `primarySelection` để migration từng bước |
| Dọn màu hardcode gây regression thị giác | Screenshot test (Paparazzi) cho các panel chính trước khi dọn |

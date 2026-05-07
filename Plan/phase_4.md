# 🎭 Phase 4 — Sticker, Collage & HD Export
> **Thời gian:** Tháng 5 – Tháng 7  
> **Phụ thuộc:** Phase 3 (Text Engine hoàn chỉnh, Background Library ổn định)  
> **Mục tiêu:** Cung cấp đủ công cụ sáng tạo để user giữ session dài hơn và chia sẻ nhiều hơn

---

## 🎯 KPI Đầu ra

| Tiêu chí | Mục tiêu |
|---|---|
| Số lượng sticker miễn phí | 200+ (local bundle) |
| Sticker packs (Pro) | 5+ packs |
| Collage layout templates | 30+ layouts |
| Export chất lượng tối đa | 4K (3840px) hoặc theo kích thước gốc |
| Share action thành công | < 3 giây từ lúc nhấn Share |

---

## 4.1 Sticker Engine

### Kiến trúc Sticker System

```kotlin
data class StickerPack(
    val id: String,
    val name: String,
    val thumbnailRes: Int,
    val isPro: Boolean = false,
    val stickers: List<StickerItem>
)

data class StickerItem(
    val id: String,
    val packId: String,
    val assetPath: String,    // assets/stickers/pack_id/item_id.webp
    val tags: List<String>    // Để search
)

data class StickerLayer(
    override val id: String = UUID.randomUUID().toString(),
    val stickerId: String,
    val stickerBitmap: Bitmap,
    var posX: Float = 0.5f,
    var posY: Float = 0.5f,
    var scale: Float = 1.0f,
    var rotation: Float = 0f
) : EditorLayer(...)
```

### Phân loại Sticker Packs

| Pack | Số lượng | Loại | Ghi chú |
|---|---|---|---|
| Emoji Basic | 60 | Free | Bundle trong APK |
| Shapes & Frames | 40 | Free | Vector drawable |
| Retro Stickers | 50 | Free | Vintage style |
| Business Pro | 50 | Pro | Badge, label, banner |
| Seasonal Pack | 50 | Pro | Tết, Halloween, Giáng sinh |
| Nature & Floral | 40 | Pro | Transparent PNG |

### UI Sticker Panel

```
[Tìm kiếm sticker...]
[Recent] [Emoji] [Shapes] [Retro] [🔒Pro...]
──────────────────────────────────────────
[Grid 4 cột, lazy load, tap để thêm vào canvas]
```

### Sticker Interaction trên Canvas

Khi thêm sticker:
1. Xuất hiện ở trung tâm canvas với animation scale-in
2. Hiện **điều khiển transform**: bounding box + 4 handle (góc để scale, cạnh để rotate)
3. Tap ngoài bounding box để deselect
4. Double tap để xóa sticker
5. Long press để mở menu: Duplicate / Flip H / Flip V / Delete

### Công việc cụ thể
- [ ] **4.1.1** Bundle 200 sticker WebP vào `assets/stickers/`
- [ ] **4.1.2** Tạo `StickerRepository` đọc danh sách sticker từ JSON manifest
- [ ] **4.1.3** `StickerPanel` bottom sheet với search và lazy loading
- [ ] **4.1.4** Multitouch gesture handler: scale, rotate, translate khi chạm vào sticker
- [ ] **4.1.5** Transform handles visual (bounding box với 4 góc + icon rotate)
- [ ] **4.1.6** Sticker context menu (long press)
- [ ] **4.1.7** Lock Pro packs — hiện paywall khi user tap

---

## 4.2 Collage Maker

### Mô tả
Cho phép người dùng ghép nhiều ảnh vào một khung layout định sẵn.

### Thiết kế Layout System

```kotlin
data class CollageLayout(
    val id: String,
    val name: String,
    val aspectRatio: Float,       // width / height
    val cells: List<CollageCell>  // các ô trong layout
)

data class CollageCell(
    val left: Float,    // 0.0 đến 1.0 (% chiều rộng)
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cornerRadius: Float = 0f
)

// Ví dụ layout 2x1
val layout2x1 = CollageLayout(
    id = "2x1_equal",
    name = "2 ảnh đều nhau",
    aspectRatio = 1f,
    cells = listOf(
        CollageCell(0f, 0f, 0.5f, 1.0f),  // Ô trái
        CollageCell(0.5f, 0f, 1.0f, 1.0f)  // Ô phải
    )
)
```

### Layout Templates (30+ layouts)

```
1 ảnh:  [ ]                    
2 ảnh:  [|]  [-]  [◤]         
3 ảnh:  [||]  [|=]  [=|]  [T]
4 ảnh:  [⊞]  [=||]  [||=]    
5 ảnh:  [⊞+]                  
```

### UI Collage Screen

```
[Chọn Layout] → [Thêm ảnh vào từng ô] → [Chỉnh viền] → [Xong]

Screen:
┌─────────── preview canvas ───────────┐
│  ┌──────┬──────┐                     │
│  │  +   │  +   │   ← tap ô để chọn ảnh  │
│  └──────┴──────┘                     │
├──────────── control panel ───────────┤
│ Layout: [scroll horizontal]          │
│ Viền: ──O── (0-20dp)                 │
│ Bo góc: ──O── (0-40dp)              │
│ Màu viền: [■][■][■]...              │
└──────────────────────────────────────┘
```

### Công việc cụ thể
- [ ] **4.2.1** Thiết kế 30 `CollageLayout` JSON
- [ ] **4.2.2** `CollageRenderer` — render layout ra Bitmap với padding/border
- [ ] **4.2.3** `CollageScreen` Activity/Fragment riêng
- [ ] **4.2.4** Mỗi ô ảnh hỗ trợ pinch-to-zoom và pan bên trong ô
- [ ] **4.2.5** UI chọn layout (horizontal scroll) với preview thumbnail
- [ ] **4.2.6** Chỉnh viền (padding, corner radius, màu viền)
- [ ] **4.2.7** Export collage sang editor để thêm text/sticker

---

## 4.3 HD Export Engine

### Vấn đề hiện tại
Export chỉ dùng `painterView.convertToBitmap()` — không kiểm soát được chất lượng, kích thước output.

### Thiết kế Export Pipeline

```kotlin
data class ExportConfig(
    val format: ExportFormat = ExportFormat.PNG,
    val quality: Int = 95,           // JPEG only
    val targetSize: ExportSize = ExportSize.ORIGINAL,
    val dpi: Int = 300               // Print quality
)

enum class ExportFormat { PNG, JPEG, WEBP }

sealed class ExportSize {
    object Original : ExportSize()
    data class Fixed(val width: Int, val height: Int) : ExportSize()
    data class Scale(val factor: Float) : ExportSize()
    // Preset sizes
    companion object {
        val HD = Fixed(1920, 1080)
        val FHD = Fixed(2560, 1440)
        val UHD4K = Fixed(3840, 2160)
        val Instagram = Fixed(1080, 1080)
        val InstagramStory = Fixed(1080, 1920)
        val FacebookCover = Fixed(1640, 924)
    }
}
```

### UI Export Screen (trước khi lưu)

```
[Preview thu nhỏ]

Kích thước:    [Gốc ▾]
               [HD 1080p] [FHD] [4K] [Instagram] [Story]
               
Định dạng:     [PNG] [JPEG] [WebP]
Chất lượng:    ─────────────── 95%  (JPEG only)
Ước tính size: ~2.4 MB

[Lưu vào Thư viện]  [Chia sẻ]
```

### Công việc cụ thể
- [ ] **4.3.1** `ExportImageUseCase` với cấu hình đầy đủ
- [ ] **4.3.2** High-quality composite renderer (render từng layer theo thứ tự, dùng ARGB_8888)
- [ ] **4.3.3** UI chọn kích thước export với preset social media
- [ ] **4.3.4** Progress bar khi export ảnh lớn
- [ ] **4.3.5** Copy to clipboard option

---

## 4.4 Social Share Integration

### Công việc cụ thể
- [ ] **4.4.1** Share Intent với `FileProvider` (tránh lỗi bảo mật)
- [ ] **4.4.2** Intent chooser ưu tiên Instagram, TikTok, Facebook
- [ ] **4.4.3** "Sao chép link" nếu share lên cloud
- [ ] **4.4.4** Thêm watermark tùy chọn (Pro users có thể tắt) vào ảnh share

---

## 📅 Sprint breakdown

| Sprint | Tuần | Công việc |
|---|---|---|
| Sprint 1 | Tuần 1-2 | 4.1.1-4.1.4 Sticker bundle + panel UI |
| Sprint 2 | Tuần 3-4 | 4.1.5-4.1.7 Sticker transform + Pro lock |
| Sprint 3 | Tuần 5-6 | 4.2 Collage Maker |
| Sprint 4 | Tuần 7 | 4.3 HD Export Engine |
| Sprint 5 | Tuần 8 | 4.4 Social Share + QA |

---

> ⬅️ [Phase 3](./phase_3.md) | ➡️ [Phase 5 — UX Polish & Onboarding](./phase_5.md)

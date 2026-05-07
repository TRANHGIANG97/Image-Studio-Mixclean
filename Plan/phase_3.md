# 🎨 Phase 3 — Background Library & Text Engine
> **Thời gian:** Tháng 3 – Tháng 5  
> **Phụ thuộc:** Phase 2 hoàn thành (Manual Erase, Layer Opacity đã ổn định)  
> **Mục tiêu:** Người dùng có đủ nội dung để tạo ra ảnh thương mại chuyên nghiệp ngay trong app

---

## 🎯 KPI Đầu ra

| Tiêu chí | Mục tiêu |
|---|---|
| Số lượng nền trong thư viện | 150+ (màu, gradient, pattern, ảnh) |
| Số lượng font chữ | 50+ font (Google Fonts) |
| Tốc độ tải ảnh nền Stock | < 2 giây (cache sau lần đầu) |
| Text rendering quality | Anti-aliased, hỗ trợ tiếng Việt |
| Session time trung bình | Tăng 40% so với Phase 2 |

---

## 3.1 Background Library — Thư viện Nền toàn diện

### Cấu trúc thư viện nền

```
Background Library
├── 🎨 Màu đặc (Solid Colors)
│   ├── Bảng màu định nghĩa sẵn (200 màu)
│   └── Color Picker tuỳ chỉnh (HSV Wheel)
│
├── 🌈 Gradient
│   ├── Linear Gradient (50+ preset)
│   ├── Radial Gradient (20+ preset)
│   └── Custom Gradient (chọn màu + góc)
│
├── 🔷 Pattern (Hoa văn)
│   ├── Geometric (30+ mẫu: dot, stripe, grid, chevron...)
│   ├── Nature (floral, leaf, wave...)
│   └── Abstract (noise, marble, fabric...)
│
├── 📸 Ảnh Stock (Unsplash API)
│   ├── Categories: Nature, City, Abstract, Texture
│   ├── Search by keyword
│   └── Offline cache (5 ảnh gần nhất)
│
└── ⭐ Mẫu đã lưu (Recent & Favorites)
```

### Mở rộng PresetStyle

```kotlin
// Mở rộng từ hệ thống preset hiện tại
sealed class BackgroundSource {
    data class SolidColor(val color: Int) : BackgroundSource()
    data class GradientPreset(val style: PresetStyle) : BackgroundSource()
    data class CustomGradient(val colors: List<Int>, val angle: Float) : BackgroundSource()
    data class Pattern(val patternRes: Int, val tintColor: Int? = null) : BackgroundSource()
    data class StockPhoto(val url: String, val localCacheFile: File? = null) : BackgroundSource()
    data class UserGallery(val uri: Uri) : BackgroundSource()
}
```

### UI: Tab-based Bottom Panel

```
[Màu] [Gradient] [Pattern] [Ảnh] [Đã lưu]
─────────────────────────────────────────
[Grid preview của các nền]
[Nền đang chọn: Rõ ràng + Nút "Áp dụng"]
```

### Tích hợp Unsplash API
```kotlin
// Unsplash Free API (50 requests/giờ)
interface UnsplashApi {
    @GET("photos/random")
    suspend fun getRandomPhotos(
        @Query("count") count: Int = 20,
        @Query("query") query: String,
        @Query("client_id") key: String = BuildConfig.UNSPLASH_KEY
    ): List<UnsplashPhoto>
}
```

### Công việc cụ thể
- [ ] **3.1.1** Thiết kế và vẽ 30 Pattern vector drawable
- [ ] **3.1.2** Tạo Bottom Sheet với 5 tab: Màu, Gradient, Pattern, Ảnh, Đã lưu
- [ ] **3.1.3** Implement HSV Color Picker (có thể dùng lib: `com.github.skydoves:colorpicker-compose`)
- [ ] **3.1.4** Custom Gradient Builder (chọn 2-3 màu + góc xoay)
- [ ] **3.1.5** Tích hợp Unsplash API với caching bằng Coil
- [ ] **3.1.6** "Lưu vào yêu thích" cho nền đang dùng
- [ ] **3.1.7** Background Preview realtime khi vuốt qua các lựa chọn

---

## 3.2 Text Engine Nâng cao

### Phân tích tính năng Text của đối thủ
**Background Eraser (InShot)** có:
- 100+ font
- Text curve (chữ theo đường cong)
- Text effects: Shadow, Stroke, Glow, 3D
- Text animation (khi export video — bỏ qua)
- Spacing: Letter spacing, Line height
- Background cho text (shape: rect, round, none)

### Thiết kế Text Layer

```kotlin
data class TextLayer(
    override val id: String = UUID.randomUUID().toString(),
    var text: String,
    var fontFamily: String = "Roboto",
    var fontSize: Float = 48f,
    var textColor: Int = Color.WHITE,
    // Effects
    var shadowColor: Int = Color.BLACK,
    var shadowRadius: Float = 0f,
    var shadowDx: Float = 0f,
    var shadowDy: Float = 0f,
    var strokeColor: Int = Color.BLACK,
    var strokeWidth: Float = 0f,
    // Layout
    var letterSpacing: Float = 0f,
    var lineHeight: Float = 1.2f,
    var alignment: Paint.Align = Paint.Align.CENTER,
    // Background
    var bgColor: Int? = null,
    var bgCornerRadius: Float = 0f,
    var bgPadding: Float = 8f,
    // Transform
    var posX: Float = 0.5f,  // 0 đến 1 (phần trăm canvas)
    var posY: Float = 0.5f,
    var rotation: Float = 0f,
    var scale: Float = 1.0f
) : EditorLayer(...)
```

### UI: Text Editor Sheet

```
┌────────────────────────────────┐
│ [← Quay lại]  Văn bản  [✓ Xong] │
├────────────────────────────────┤
│ [Canvas preview với text]      │
├────────────────────────────────┤
│ [Font] [Kích thước] [Màu sắc] │
│ [Kiểu chữ: B I U S]           │
├──────────────── tabs ──────────┤
│ [Font] [Hiệu ứng] [Khoảng cách] │
│                                │
│ FONT TAB:                      │
│  Roboto  Montserrat  Raleway   │
│  Playfair  Lobster  Dancing    │
│  ...50+ fonts (lazy load)      │
│                                │
│ HIỆU ỨNG TAB:                  │
│  Đổ bóng: [Bật/Tắt]          │
│  Màu bóng: [■] Blur: ──O──    │
│  khoảng cách X/Y: ──O── ──O── │
│  Viền: [Bật/Tắt]              │
│  Màu viền: [■] Độ dày: ──O── │
└────────────────────────────────┘
```

### Tải Font từ Google Fonts
```kotlin
// Dùng Downloadable Fonts API của Android (không cần bundle font)
class FontManager {
    fun loadGoogleFont(fontName: String, callback: (Typeface?) -> Unit) {
        val request = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            fontName,
            R.array.com_google_android_gms_fonts_certs
        )
        FontsContractCompat.requestFont(context, request, object : FontsContractCompat.FontRequestCallback() {
            override fun onTypefaceRetrieved(typeface: Typeface) = callback(typeface)
            override fun onTypefaceRequestFailed(reason: Int) = callback(null)
        }, handler)
    }
}
```

### Công việc cụ thể
- [ ] **3.2.1** Tạo `TextLayer` data class mở rộng `EditorLayer`
- [ ] **3.2.2** Implement `TextLayerRenderer` — vẽ text với shadow, stroke lên Canvas
- [ ] **3.2.3** UI Text Editor bottom sheet với preview realtime
- [ ] **3.2.4** `FontManager` với Google Downloadable Fonts + cache local 20 font phổ biến
- [ ] **3.2.5** UI Font picker: lưới font với preview chữ mẫu
- [ ] **3.2.6** Effects: Shadow (color, blur, offset X/Y), Stroke (color, width)
- [ ] **3.2.7** Text background shape (none / rounded rect / circle)
- [ ] **3.2.8** Pinch scale + rotate text trực tiếp trên canvas
- [ ] **3.2.9** Hiện bounding box khi đang select text layer

---

## 3.3 HSV Color Picker

### Yêu cầu
- Vòng tròn màu sắc (Hue wheel)
- Ô vuông Saturation-Value
- Input HEX text
- Picker nhỏ gọn (inline) + picker đầy đủ (dialog)

### Thư viện gợi ý
```gradle
// Dùng thư viện thay vì tự code
implementation("com.github.skydoves:colorpickerview:2.3.0")
```

---

## 3.4 Recent & Favorites System

### Công việc cụ thể
- [ ] **3.4.1** Room database lưu lịch sử nền đã dùng (BackgroundHistoryDao)
- [ ] **3.4.2** Danh sách "Gần đây" hiện 20 nền cuối
- [ ] **3.4.3** Nút "❤️ Yêu thích" lưu và hiện trong tab riêng

---

## 📅 Sprint breakdown

| Sprint | Tuần | Công việc |
|---|---|---|
| Sprint 1 | Tuần 1-2 | 3.1.1-3.1.4 Background UI + Solid + Gradient |
| Sprint 2 | Tuần 3-4 | 3.1.5-3.1.7 Unsplash + Pattern + Favorites |
| Sprint 3 | Tuần 5-6 | 3.2.1-3.2.5 Text Engine core + Font picker |
| Sprint 4 | Tuần 7-8 | 3.2.6-3.2.9 Text effects + Transform |
| Sprint 5 | Tuần 9 | 3.3 Color Picker + 3.4 Favorites system |
| Sprint 6 | Tuần 10 | QA toàn phase + Performance test |

---

> ⬅️ [Phase 2](./phase_2.md) | ➡️ [Phase 4 — Sticker, Collage & HD Export](./phase_4.md)

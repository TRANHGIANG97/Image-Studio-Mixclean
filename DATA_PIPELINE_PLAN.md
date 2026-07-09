# 🏗️ Kế hoạch Chuẩn hóa Data Pipeline Đồ họa

**PSD → Web Admin → App Mobile — "Đóng Băng Sự Bất Định"**

> Triết lý: Biến mọi thứ "quá phức tạp, dễ lỗi" thành "tài nguyên đơn giản, dễ quản lý".
> Mỗi Phase đều có checklist rõ ràng, ánh xạ trực tiếp vào các file code thực tế trong dự án.

---

## Kiến trúc Tổng quan

```
[File PSD]
    │
    ▼  Phase 1: Chuẩn hóa Cổng vào
[Web Admin] ──── @webtoon/psd parse ──── Rules Engine ──── Rasterize layer khó
    │
    ▼  Phase 2: Hợp đồng Dữ liệu
[JSON Contract] ──── Zod Schema ──── Mapper (Fabric → Cloud) ──── Validate
    │
    ▼  Phase 3: Phòng thủ chủ động
[App Mobile] ──── Safe Parser ──── Canvas Isolation ──── Graceful Skip
```

---

## Phase 1: Chuẩn hóa Cổng vào (PSD → Web Admin)

**Mục tiêu:** Chặt đứt mọi hiệu ứng phức tạp độc quyền Adobe ngay tại cửa ngõ Web, đảm bảo
hệ thống chỉ làm việc với dữ liệu nguyên thủy (primitive data).

### 1.1 Rules Engine — Phân loại Layer tự động

**Vấn đề hiện tại:**
Web dùng `@webtoon/psd` để đọc PSD nhưng **chưa có hệ thống tự động phân loại** layer nào
"an toàn" (giữ vector) và layer nào "nguy hiểm" (cần rasterize).
Hiện tại `template-converter.ts` chỉ xử lý output từ Fabric.js canvas, không kiểm tra nguồn gốc
hay độ phức tạp đồ họa của layer PSD đầu vào.

**Giải pháp:**
Tạo module `admin_web/src/lib/psd/layer-classifier.ts`:

```
Đầu vào: PSD Layer metadata (blendMode, effects, type)
         ↓
    ┌────────────┐
    │ Rules      │──→ TEXT thuần: giữ vector JSON
    │ Engine     │──→ Image không effect: giữ nguyên URL
    │            │──→ Layer có blendMode lạ: đánh dấu RASTERIZE
    │            │──→ Layer có DropShadow/InnerGlow phức tạp: RASTERIZE
    │            │──→ Group layers chồng opacity: RASTERIZE
    └────────────┘
         ↓
Đầu ra: LayerClassification { strategy: 'keep-vector' | 'rasterize' }
```

**Bảng phân loại chi tiết:**

| Loại Layer PSD              | Blend Mode    | Effects         | Chiến lược         |
|-----------------------------|---------------|-----------------|---------------------|
| Text thuần                  | Normal        | Không           | `keep-vector`       |
| Text + DropShadow đơn      | Normal        | DropShadow      | `keep-vector` (*)   |
| Hình ảnh (Smart Object)     | Normal        | Không           | `keep-vector`       |
| Hình ảnh + Blend Mode lạ   | Overlay/Burn  | Bất kỳ          | `rasterize`         |
| Shape + Inner Glow          | Bất kỳ        | InnerGlow       | `rasterize`         |
| Group chồng Opacity         | Bất kỳ        | Bất kỳ          | `rasterize`         |
| Layer có Mask phức tạp      | Bất kỳ        | ClippingMask    | `rasterize`         |
| Bóng nền dạng gradient (**) | Normal        | Radial fade     | `keep-vector` + tag |

(*) DropShadow đơn giản (1 bóng, offset + blur + color) được giữ vector vì cả Web
(Fabric.js shadow) và App (Compose `graphicsLayer`) đều hỗ trợ tốt.

(**) **Bài học từ bug thực tế (07/2026):** Layer ellipse dùng radial gradient đen→trong suốt
làm "bóng đổ sàn" nhưng payload vẫn mang `shadowIntensity: 0.94` từ metadata PSD → App render
**bóng kép** (gradient + drop shadow chồng nhau). Hiện App phải đoán bằng heuristic
`hasGradientBakedShadow()` (`CloudTemplateExtensions.kt`). **Giải pháp đúng ở tầng contract:**
Rules Engine phát hiện layer dạng này và export tường minh `sourceKind: 'ground-shadow'`
kèm `shadowIntensity: 0` — biến phán đoán (heuristic) thành hợp đồng (contract).
Heuristic phía App giữ lại chỉ để tương thích template cũ.

### 1.2 Rasterization Service — Xuất ảnh tĩnh cho Layer phức tạp

**Vấn đề hiện tại:**
Khi một PSD layer chứa hiệu ứng quá phức tạp mà Fabric.js không render chính xác,
**không có cơ chế fallback** nào. Admin phải tự xử lý thủ công bằng Photoshop (xuất PNG rồi
import lại), gây lãng phí thời gian và dễ sai sót.

**Giải pháp:**
Tạo module `admin_web/src/lib/psd/rasterize-service.ts`:

- Khi Rules Engine phân loại layer là `rasterize`:
  1. Dùng `@webtoon/psd` API để composite riêng layer đó thành `ImageData` (pixel buffer).
  2. Chuyển sang `<canvas>` element → export `.toBlob('image/webp', 0.92)`.
  3. Upload blob lên Cloudflare R2 (qua API route `admin_web/src/app/api/`) → nhận về CDN URL.
  4. Thay thế layer phức tạp bằng một `IMAGE` layer đơn giản với `imageUrl` = CDN URL.
  5. Gắn metadata `sourceKind: 'psd-rasterized'` để App biết layer này đã được flatten.

**Tại sao dùng WebP thay vì PNG?**
- WebP trong suốt (transparent) nhỏ hơn PNG khoảng 30-50%.
- Giảm dung lượng download trên mobile → cải thiện trải nghiệm người dùng.
- Cả Coil (Android) và trình duyệt đều hỗ trợ tốt WebP.

### 1.3 Chuẩn hóa Hệ tọa độ Canvas

**Vấn đề hiện tại:**
File `template-converter.ts` (dòng 199-201) đang chuyển tọa độ Fabric sang hệ tọa độ tương đối
(0.0 → 1.0) rất tốt:
```typescript
const anchorX = centerX / canvasBaseWidth;
const anchorY = centerY / canvasBaseHeight;
```
Tuy nhiên kích thước layer (`baseWidth`, `baseHeight`) lại dùng **giá trị pixel tuyệt đối**
(dòng 313-314, 327-328). Điều này tạo ra sự không nhất quán khi App scale lên các
màn hình có kích thước khác nhau.

**Giải pháp:**
- **Giữ nguyên** hệ pixel tuyệt đối cho `baseWidth`/`baseHeight` (vì App cần tính toán
  chính xác tỷ lệ scale của từng layer).
- **Bổ sung** trường `canvas.referenceSize` trong JSON schema để App biết hệ quy chiếu
  gốc (ví dụ: `1080x1920`). App sẽ scale tất cả giá trị pixel theo tỷ lệ
  `actualScreenWidth / referenceWidth`.
- Chuẩn hóa `scale` trong `CloudTransform` luôn là tỷ lệ so với `baseWidth`/`baseHeight`
  gốc (không phải scale tuyệt đối từ Fabric.js `scaleX`/`scaleY`).

---

## Phase 2: Bản Hợp Đồng Dữ Liệu Chặt Chẽ (Data Contract)

**Mục tiêu:** Web Admin và App Mobile không nhìn vào nhau mà cùng nhìn vào một bản
"Hợp đồng dữ liệu" (Schema) nghiêm ngặt. Bất kỳ vi phạm nào đều bị chặn ngay tại Web.

### 2.1 Định nghĩa JSON Schema cứng bằng Zod

**Vấn đề hiện tại:**
File `cloud-template.ts` định nghĩa interface TypeScript, nhưng nó chỉ là **type annotation**
(kiểm tra lúc biên dịch). Khi runtime, không có gì ngăn cản một giá trị `opacity: 2.5` hay
`type: "UNKNOWN_TYPE"` bị ghi vào database.

File `template-validate.ts` hiện chỉ kiểm tra:
- Có background hay không.
- Số layer khớp giữa Fabric canvas và CloudTemplate.
- URL image hợp lệ.

**Thiếu hoàn toàn:** Kiểm tra giá trị vượt ngưỡng, kiểu dữ liệu sai, trường bắt buộc bị thiếu.

**Giải pháp:**
Tạo file `admin_web/src/lib/schema/template-contract.ts` dùng Zod:

```typescript
// Ví dụ minh họa cấu trúc Schema
const CloudTransformSchema = z.object({
  anchorX: z.number().min(0).max(2).default(0.5),    // Cho phép vượt nhẹ ngoài canvas
  anchorY: z.number().min(0).max(2).default(0.5),
  scale:   z.number().min(0.01).max(50).default(1.0),
  rotation: z.number().min(-360).max(360).default(0),
});

const LayerTypeEnum = z.enum([
  'IMAGE', 'DECORATION', 'TEXT', 'SHAPE_TEXT',
  'PLACEHOLDER_OBJECT', 'SHADOW_REGION'
]);

const BlendModeEnum = z.enum([
  'normal', 'multiply', 'screen', 'overlay', 'darken', 'lighten',
  'color-dodge', 'color-burn', 'hard-light', 'soft-light',
  'difference', 'exclusion', 'hue', 'saturation', 'color', 'luminosity',
  'linear-dodge'
]).default('normal');

const CloudPayloadSchema = z.object({
  alpha:           z.number().min(0).max(1).default(1.0),
  shadowIntensity: z.number().min(0).max(1).default(0),
  shadowAngle:     z.number().min(0).max(360).default(45),
  shadowDistance:   z.number().min(0).default(0),
  shadowBlur:      z.number().min(0).max(200).default(15),
  blendMode:       BlendModeEnum,
  visible:         z.boolean().default(true),
  locked:          z.boolean().default(false),
  // ... còn rất nhiều trường khác
});
```

**Nguyên tắc vàng:**
- Mọi trường số **phải có** `.min()` `.max()` `.default()`.
- Mọi trường enum **phải dùng** `z.enum([...])` thay vì `z.string()`.
- Mọi trường optional **phải có** `.default()` hoặc `.nullable()`.

**Ghi chú hiện trạng:** `zod@^4.4.3` đã có sẵn trong `admin_web/package.json`
(đang dùng cho form validation) — không cần cài thêm dependency, chỉ cần viết schema.

**Bổ sung: Invariant xuyên trường (cross-field) mà Zod field-level không bắt được.**
Dùng `.superRefine()` cho các ràng buộc nghiệp vụ:
- `type === 'PLACEHOLDER_OBJECT'` hoặc `payload.replaceable === true`
  ⟹ **bắt buộc** có `defaultImageUrl` (bài học từ bug "đối tượng thay thế" 07/2026:
  3 nguồn sự thật `layerType` / `isReplaceable` / `payload.replaceable` từng lệch nhau).
- Layer có `fillGradient` radial fade-to-transparent ⟹ `shadowIntensity` phải bằng 0
  (chặn bóng kép ngay tại Web thay vì để App tự vá).
- `SHADOW_REGION` ⟹ `sourceKind === 'shadow-region'` (đồng bộ 2 cách đánh dấu).

### 2.2 Schema Versioning — Phiên bản hóa hợp đồng

**Vấn đề hiện tại:**
`template-converter.ts` đang ghi `schemaVersion: 1` (dòng 410), và `CloudTemplateParser.kt`
kiểm tra `SUPPORTED_SCHEMA_VERSION = 1` (dòng 10). Tuy nhiên **không có logic xử lý**
khi version không khớp — App sẽ âm thầm parse sai hoặc crash nếu Web nâng schema lên v2.

**Giải pháp:**

| Web (Gửi đi)                                | App (Nhận về)                                      |
|----------------------------------------------|-----------------------------------------------------|
| Luôn ghi `schemaVersion` vào JSON metadata   | Đọc `schemaVersion` đầu tiên trước khi parse        |
| Tăng version khi thêm trường bắt buộc mới    | `version <= SUPPORTED` → Parse bình thường           |
|                                               | `version > SUPPORTED` → Hiển thị nhưng log cảnh báo |
|                                               | Trường lạ từ version mới → Bỏ qua (ignore)          |

**Quy tắc tăng version:**
- **Patch (không tăng):** Thêm trường optional mới với giá trị default.
- **Minor (tăng 1→2):** Thêm trường bắt buộc mới hoặc thay đổi ý nghĩa trường cũ.
- **Major (tăng 1→2):** Thay đổi cấu trúc JSON gốc (breaking change).

**Chống trôi version (hiện trạng):** `schemaVersion: 1` đang bị hardcode ở **ít nhất 5 chỗ**
trong admin_web (`template-converter.ts`, `template.service.ts`, `useTemplates.ts`,
`api/v1/health/route.ts`, `demo-templates/`). Khi bump version chắc chắn sẽ sót.
→ Gom về **một hằng số duy nhất** `CURRENT_SCHEMA_VERSION` trong `template-contract.ts`,
mọi nơi khác import từ đó. Endpoint `/api/v1/health` cũng trả về giá trị này để App
có thể phát hiện sớm server đã nâng schema.

### 2.3 Tầng Mapper Fabric → CloudTemplate (Nâng cấp)

**Vấn đề hiện tại:**
Hàm `fabricToCloudTemplate()` trong `template-converter.ts` hiện đang:
1. Truy cập trực tiếp thuộc tính Fabric object (`obj.opacity`, `obj.shadow`, `obj.fill`...) — 
   **Rất mỏng manh** vì Fabric.js có thể thay đổi API giữa các phiên bản.
2. Dùng `payload: any` (dòng 270) — mất hoàn toàn type safety.
3. Không có unit test cho conversion logic.

**Giải pháp:**

**a) Chuẩn hóa kiểu dữ liệu:**
Thay `const payload: any = {...}` bằng `Partial<CloudPayload>` có kiểu chặt chẽ.
Sau khi build xong payload, chạy qua `CloudPayloadSchema.parse(payload)` để validate.

**b) Tách biệt logic (Separation of Concerns):**
```
fabricToCloudTemplate()
    ├── extractTextPayload(obj)     → CloudPayload (text fields only)
    ├── extractImagePayload(obj)    → CloudPayload (image fields only)
    ├── extractShapePayload(obj)    → CloudPayload (shape fields only)
    ├── extractShadowParams(obj)    → ShadowParams
    ├── extractTransform(obj, canvasSize) → CloudTransform
    └── CloudPayloadSchema.parse()  → Validated CloudPayload
```

**c) Unit test cho Mapper:**
Viết test bao phủ các edge case:
- Layer text không có font → fallback `sans-serif`.
- Layer image có `src` là blob URL → phải filter ra.
- Layer shape có `fill` là gradient object → phải serialize đúng.
- `opacity` = `undefined` → phải fallback thành `1.0`.
- `angle` = `NaN` → phải fallback thành `0`.

### 2.4 Validation Gate — Cổng kiểm duyệt trước khi Publish

**Vấn đề hiện tại:**
`validateTemplateForPublish()` chỉ chặn các lỗi "nhìn thấy rõ" (thiếu background, URL sai).
Không chặn được: giá trị vượt ngưỡng, layer bị hỏng cấu trúc, JSON thiếu trường.

**Giải pháp:**
Nâng cấp validation thành **2 tầng**:

```
Tầng 1: Schema Validation (Zod)
├── Parse toàn bộ CloudTemplate qua Zod schema
├── Tự động fix giá trị lệch (clamp opacity 0→1, set default blendMode...)
└── Nếu structure hỏng hoàn toàn → CHẶN, báo lỗi chi tiết

Tầng 2: Business Logic Validation (validateTemplateForPublish)
├── Kiểm tra background tồn tại
├── Kiểm tra layer count khớp
├── Kiểm tra URL hợp lệ
├── [ĐÃ CÓ 07/2026] Đối tượng thay thế bắt buộc có defaultImageUrl hợp lệ
├── [MỚI] Kiểm tra tổng dung lượng layer images < threshold (ví dụ 15MB)
├── [MỚI] Kiểm tra không có layer nào baseWidth/baseHeight = 0
├── [MỚI] Kiểm tra tất cả font chữ đều nằm trong danh sách font được hỗ trợ
└── [MỚI] Kiểm tra fabric_state ↔ canvas_data không lệch nhau
```

**Về kiểm tra fabric_state ↔ canvas_data:** Admin load lại từ `fabric_state`, App đọc
`canvas_data`. Nếu hai bản lệch (ví dụ flag `replaceable` chỉ có một phía), admin và app
sẽ nhìn thấy hai template khác nhau mà không ai biết. Validation gate cần re-serialize
`fabric_state` → so khớp các trường trọng yếu (layer count, layerId, type, replaceable)
với `canvas_data` trước khi cho publish.

---

## Phase 3: Lớp Phòng Thủ Chủ Động (App Mobile)

**Mục tiêu:** App không bao giờ được crash dù nhận dữ liệu bất kỳ từ Web.
Triệt tiêu hoàn toàn hiệu ứng domino đồ họa.

### 3.1 Safe Parsing — Biên dịch An toàn Tuyệt Đối

**Trạng thái hiện tại (Tốt — Cần củng cố):**
File `CloudTemplateParser.kt` đã làm khá tốt:
- Dùng `optString()`, `optInt()`, `optDouble()` thay vì `getString()` (không ép kiểu).
- Có extension function `optNonBlankString()`, `optFloatOrNull()`, `optBooleanOrNull()`.
- Kotlin data class `CloudPayload` đã có default values cho mọi trường.

**Vấn đề còn tồn tại:**
1. **Dòng 14 `CloudTemplateParser.kt`:** `throw IllegalArgumentException("Template missing canvas_data")`
   — Đây là **BOM NỔ CHẬM**. Nếu server trả JSON thiếu trường `canvas_data`,
   App sẽ crash ngay lập tức tại đây.
2. **Không có try-catch bao quanh** hàm `parse()` tổng — bất kỳ exception nào bên trong
   (ví dụ `JSONException` do JSON malformed) đều sẽ leak ra ngoài.
3. **`CloudLayer.type` là `String`** thay vì sealed class/enum → App không thể exhaustive check.

**Giải pháp:**

**a) Loại bỏ `throw`, nhưng KHÔNG fallback về template rỗng âm thầm:**

Trả về `CloudTemplate()` rỗng khi thiếu `canvas_data` là "đổi crash lấy màn hình trắng" —
người dùng vẫn gặp trải nghiệm hỏng mà team không có tín hiệu gì. Đúng hơn là đẩy lỗi
lên tầng gọi dưới dạng `Result` để UI quyết định (ẩn template khỏi danh sách, hiện
placeholder "template lỗi", báo analytics):

```kotlin
sealed interface ParseOutcome {
    data class Success(val template: CloudTemplate) : ParseOutcome
    data class Invalid(val reason: String, val templateId: String?) : ParseOutcome
}

fun parseFromApiItemSafe(item: JSONObject): ParseOutcome {
    val canvasData = item.optJSONObject("canvas_data")
        ?: return ParseOutcome.Invalid("missing canvas_data", item.optString("id"))
    return runCatching { parseFromApiItem(item) }
        .fold(
            onSuccess = { ParseOutcome.Success(it) },
            onFailure = { ParseOutcome.Invalid(it.message ?: "parse error", item.optString("id")) },
        )
}
```

Tầng repository dùng `parseFromApiItemSafe`, filter `Invalid` ra khỏi danh sách hiển thị
và log non-fatal lên crash reporting — template hỏng biến mất khỏi UI một cách có chủ đích,
kèm tín hiệu đo đếm được, thay vì render trống vô danh.

**c) Chuyển `type: String` sang enum an toàn:**
```kotlin
enum class CloudLayerType {
    IMAGE, DECORATION, TEXT, SHAPE_TEXT, PLACEHOLDER_OBJECT, SHADOW_REGION, UNKNOWN;
    
    companion object {
        fun fromString(raw: String?): CloudLayerType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}
```
Khi gặp `UNKNOWN`, vòng lặp render sẽ skip layer đó thay vì crash.

### 3.2 Canvas Isolation — Cô lập Môi trường Vẽ

**Trạng thái hiện tại (Tốt — Cần bổ sung):**
File `EditorCanvas.kt` đã dùng `Modifier.graphicsLayer` cho artboard-level zoom/pan
(dòng 463-468). File `ProductLayer.kt` dùng `graphicsLayer` cho bóng đổ.

**Vấn đề còn tồn tại:**
1. Trong vòng lặp render chính (dòng 538-629 `EditorCanvas.kt`), các layer được render
   **trực tiếp** trong `Box` cha mà không có isolation layer riêng. Nếu một layer tung
   exception (ví dụ Coil load ảnh lỗi), nó có thể crash toàn bộ Composition.
2. BlendMode (`layer.blendMode`) được truyền xuống component con nhưng **không có validation**
   — một giá trị blend mode lạ từ Web có thể gây crash renderer.

**Giải pháp:**

**a) Bọc mỗi layer trong isolation container:**
```kotlin
// THAY VÌ render trực tiếp:
layers.forEach { layer ->
    when {
        layer.isVectorContentLayer -> ShapeTextLayer(...)
        layer.type == LayerType.IMAGE -> ProductLayerV2(...)
    }
}

// BỌC TRONG ISOLATION:
layers.forEach { layer ->
    key(layer.id) {
        IsolatedLayerContainer(
            alpha = layer.appearance.alpha,
            blendMode = layer.blendMode.toComposeBlendModeOrNull(),
        ) {
            when {
                layer.isVectorContentLayer -> ShapeTextLayer(...)
                layer.type == LayerType.IMAGE -> ProductLayerV2(...)
            }
        }
    }
}
```

**b) `IsolatedLayerContainer` Composable:**

> ⚠️ **Lưu ý kỹ thuật quan trọng:** KHÔNG thể `runCatching { content() }` quanh một
> lambda `@Composable` — Compose không phải là hàm gọi tuần tự; exception xảy ra trong
> quá trình composition/layout/draw sẽ hủy cả frame, try-catch tại call-site không bắt được.
> Compose hiện **không có error boundary** như React. Chiến lược đúng là **chặn lỗi trước
> khi render** chứ không phải bắt lỗi lúc render:

```kotlin
@Composable
fun IsolatedLayerContainer(
    alpha: Float,
    blendMode: BlendMode?,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha.coerceIn(0f, 1f)
            // Offscreen compositing: layer vẽ vào buffer riêng ở GPU RenderNode level,
            // blend mode / alpha không "rò" sang layer khác
            if (blendMode != null) {
                this.compositingStrategy = CompositingStrategy.Offscreen
            }
        }
    ) { content() }
}
```

**Ba tuyến phòng thủ thay cho try-catch trong composition:**
1. **Sanitize tại Mapper (3.3):** mọi giá trị vào `EditorLayer` đã được clamp/validate —
   layer đến được composition là layer render được.
2. **BlendMode whitelist:** `String → BlendMode?` qua bảng map tường minh
   (`"multiply" → BlendMode.Multiply`...), giá trị lạ trả `null` → render normal,
   không đưa raw string xuống renderer.
3. **Ảnh lỗi giao cho Coil:** `AsyncImage(onError = ...)` hiển thị placeholder —
   Coil đã nuốt lỗi decode/network sẵn, không cần tự bọc.

### 3.3 Graceful Skip — Bỏ qua êm ái

**Vấn đề hiện tại:**
File `CloudLayerToEditorMapper.kt` dòng 35 dùng `mapNotNull` — nếu `mapLayer()` trả về `null`
thì layer bị bỏ qua. Đây là chiến lược đúng nhưng **chưa đủ**:
- `mapLayer()` có thể tung exception (ví dụ: `UUID.randomUUID()` hiếm khi lỗi,
  nhưng `replaceLocalhostWithConfiguredHost()` có thể lỗi nếu URL malformed).
- Không có logging khi layer bị skip → khó debug.

**Giải pháp:**
```kotlin
return cloudTemplate.layers
    .sortedBy { it.zIndex }
    .mapNotNull { cloudLayer ->
        runCatching {
            mapLayer(cloudLayer, canvasWidth, canvasHeight, scaledDensity)
        }.onFailure { error ->
            Log.w("CloudMapper", "Skipped layer ${cloudLayer.layerId}: ${error.message}")
        }.getOrNull()
    }
    .let { EditorLayerNormalizer.normalize(it) }
```

### 3.4 Font Fallback Chain — Chuỗi dự phòng Font chữ

**Vấn đề tiềm ẩn:**
Nếu Web dùng một font chữ mà App chưa nhúng (embed), text sẽ render bằng font mặc định
hệ thống → Khác biệt hoàn toàn về mặt thẩm mỹ.

**Giải pháp:**
- Tạo bảng ánh xạ font trong App: `FontRegistry`.
- Khi `CloudPayload.font` không tìm thấy trong registry → Dùng font fallback gần nhất
  (ví dụ: UTM font lạ → fallback sang UTM Avo).
- Log cảnh báo để team biết cần nhúng thêm font.

---

## Kế hoạch Kiểm thử (Testing Strategy)

### Unit Tests

**Hạ tầng có sẵn:** admin_web đã cấu hình `vitest` (`npm test`) + Testing Library;
Android đã có JUnit chạy qua Gradle. Không cần setup mới, chỉ cần viết test.

| Module | File | Nội dung test | Trạng thái |
|--------|------|---------------|------------|
| admin_web | `template-contract.test.ts` | Zod schema parse đúng/sai, default values, edge cases | Chưa có |
| admin_web | `template-converter.test.ts` | Fabric → CloudTemplate conversion, tất cả layer types | Chưa có |
| admin_web | `template-validate.test.ts` | Validation gate: replaceable thiếu defaultImageUrl, URL sai | Chưa có |
| admin_web | `layer-classifier.test.ts` | Rules Engine phân loại đúng layer PSD | Chưa có (Phase 1) |
| core-domain | `CloudTemplateExtensionsTest.kt` | `hasGradientBakedShadow`, `isReplaceableLayer` | ✅ Đã có 07/2026 |
| core-domain | `CloudTemplateParserTest.kt` | Parse JSON thiếu trường, sai kiểu, malformed | Chưa có |
| studio_edit | `CloudTemplateParityTest.kt` | Ground shadow, replaceable → isSample, tracking | ✅ Đã có 07/2026 |
| studio_edit | `CloudLayerToEditorMapperTest.kt` | Mapper xử lý đúng mọi layer type, null safety | Chưa có |

### Integration Tests

| Kịch bản | Mô tả |
|-----------|--------|
| Happy Path | Import PSD → Web render → Publish → App render → So sánh visual |
| Missing Fields | Web gửi JSON thiếu 5 trường → App vẫn render không crash |
| Unknown Layer Type | Web gửi `type: "FUTURE_TYPE"` → App skip gracefully |
| Schema Version Mismatch | Web gửi `schemaVersion: 99` → App render được các trường biết |
| Corrupt Image URL | `imageUrl: "not-a-url"` → App hiển thị placeholder thay vì crash |
| Extreme Values | `opacity: 999`, `scale: -5`, `rotation: 99999` → Clamp về giới hạn |

### Parity Test (Đã có sẵn, cần mở rộng)

File `CloudTemplateParityTest.kt` hiện kiểm tra tính tương đồng Web↔App.
Mở rộng thêm:
- So sánh font rendering (kiểm tra font fallback).
- So sánh shadow rendering (offset, blur, color).
- So sánh blend mode mapping (Web CSS → Android Compose).

---

## Thứ tự Triển khai (Execution Order)

```
Phase 0 — ĐÃ XONG (07/2026): Các fix nền tảng
├── ✅ Bóng kép gradient ground-shadow (hasGradientBakedShadow + strip tại mapper)
├── ✅ Đồng bộ "đối tượng thay thế": isReplaceable ↔ PLACEHOLDER_OBJECT (admin)
├── ✅ Giữ isSample sau thay ảnh + swap đúng layer đang tap (app)
├── ✅ Validate defaultImageUrl cho replaceable layer khi publish
└── ✅ Parity tests: ground shadow, replaceable → isSample

Tuần 1-2: Phase 2 (Data Contract)
├── Tạo Zod Schema (template-contract.ts) + superRefine cross-field invariants
├── Gom schemaVersion về CURRENT_SCHEMA_VERSION duy nhất
├── Nâng cấp Mapper (template-converter.ts): bỏ payload:any, tách extract*()
├── Nâng cấp Validation (template-validate.ts): 2 tầng + fabric_state drift check
└── Viết Unit Tests cho Web (vitest có sẵn)

Tuần 3-4: Phase 3 (App Mobile Defense)
├── CloudTemplateParser: thay throw bằng ParseOutcome (không silent-empty)
├── Thêm IsolatedLayerContainer (Offscreen compositing + BlendMode whitelist)
├── Bọc Mapper trong runCatching + log layer bị skip
├── Thêm Font Fallback Chain (FontRegistry)
└── Viết Unit Tests cho Android

Tuần 5-6: Phase 1 (PSD Normalization)
├── Xây Rules Engine (layer-classifier.ts) — gồm rule ground-shadow
├── Xây Rasterization Service (WebP → R2 → sourceKind: 'psd-rasterized')
├── Tích hợp vào PSD import flow
└── Integration Tests toàn bộ pipeline
```

**Tại sao Phase 2 trước Phase 1?**
Vì Schema Contract là **nền móng** cho mọi thứ. Không có Schema cứng thì:
- Phase 1 (Rasterize) không biết output dưới format nào.
- Phase 3 (App Defense) không biết validate dựa trên chuẩn nào.
Xây móng trước, rồi mới xây tường.

---

## Tiêu chí Hoàn thành (Definition of Done)

- [ ] **Phase 2:** Mọi template publish đều đi qua Zod validation gate. Không có `any` type
      còn lại trong template-converter.ts. `schemaVersion` chỉ khai báo tại 1 nơi.
- [ ] **Phase 3:** App không crash với **bất kỳ** JSON input nào (kể cả JSON rỗng `{}`).
      Template hỏng bị loại khỏi danh sách kèm log non-fatal (không silent-empty).
- [ ] **Phase 1:** Rules Engine phân loại chính xác ≥ 95% layer PSD thông dụng.
      Layer phức tạp tự động rasterize và upload lên CDN.
      Không còn template mới nào cần heuristic `hasGradientBakedShadow` phía App.
- [ ] **Testing:** ≥ 90% test coverage cho template-converter, CloudTemplateParser,
      và CloudLayerToEditorMapper.
- [ ] **Zero Crash:** App chạy ổn định khi nhận template từ schemaVersion 1 đến schemaVersion
      hiện tại + 1 (forward compatibility).

---

## Rủi ro & Điểm cần quyết định sớm

| Rủi ro | Ảnh hưởng | Hướng xử lý |
|--------|-----------|-------------|
| Zod clamp âm thầm sửa dữ liệu sai | Admin không biết template mình vừa lưu đã bị đổi giá trị | Clamp nhưng **hiện warning list** trên UI trước khi publish, không sửa im lặng |
| Template cũ trong DB không qua schema mới | App vẫn nhận dữ liệu "bẩn" từ template publish trước đây | Viết script migration re-validate + re-save toàn bộ template `published` |
| Rasterize làm mất khả năng chỉnh sửa layer trên admin | Admin không sửa được text/màu sau khi flatten | Giữ layer gốc trong `fabric_state` (admin-only), chỉ `canvas_data` chứa bản raster |
| `@webtoon/psd` composite từng layer chậm với PSD lớn | Import PSD treo UI | Chạy trong Web Worker, hiện progress bar theo layer |
| WebP transparent chất lượng thấp ở vùng gradient mịn | Bóng/glow bị banding | Cho phép fallback PNG per-layer khi SSIM/chất lượng dưới ngưỡng |

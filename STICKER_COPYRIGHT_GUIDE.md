# Hướng dẫn Bản quyền & Thay thế Sticker — studio_edit Release

> **Phạm vi:** Tư vấn pháp lý–kỹ thuật cho bản phát hành `studio_edit` (Android).  
> **Ngày:** 11/07/2026  
> **Trạng thái:** Advisory — không thay đổi code trong tài liệu này.

---

## Tóm tắt điều hành

Thư viện sticker hiện tại trên app mobile **không an toàn bản quyền** để ship production. Tab **Meme** chứa nhân vật/nhãn hiệu có bản quyền (Mr. Bean, Spider-Man, Simpsons, Tom & Jerry, Hasbulla, v.v.) — **rủi ro rất cao**. Tab **Decor** có mức rủi ro hỗn hợp (logo VinAI, tranh watercolor không rõ nguồn, chữ holiday).

**Khuyến nghị:** Xóa toàn bộ nội dung tab Meme hiện tại, thay bằng pack **"Biểu cảm"** dùng emoji vector có license rõ ràng (OpenMoji / Twemoji). Tab Decor thay bằng pack trang trí thương mại tự thiết kế hoặc nguồn CC0/đã mua bản quyền.

---

## 1. Kiến trúc hiện tại (điều tra codebase)

### 1.1 Luồng tải sticker trên app mobile (`studio_edit`)

```
StickerPicker (quick strip 20 sticker)
    └── ThemeplateEditorViewModel.loadStickerPreview()
            └── StickerRemoteRepository.fetchPreview()
                    └── GET /api/assets?folder=sticker_meme&limit=10
                    └── GET /api/assets?folder=sticker_decor&limit=10

StickerGallerySheet (gallery đầy đủ, 2 tab)
    └── StickerGalleryViewModel
            └── StickerRemoteRepository.fetchPage(folder, page, limit=30)
                    └── GET /api/assets?folder={sticker_meme|sticker_decor}&page=N&limit=30
```

**File chính:**

| Thành phần | File | Ghi chú |
|---|---|---|
| Repository | `studio_edit/.../data/StickerRemoteRepository.kt` | Folder: `sticker_meme`, `sticker_decor` |
| Quick strip | `studio_edit/.../tool/StickerPicker.kt` | Trộn 10 meme + 10 decor |
| Gallery | `studio_edit/.../tool/StickerGallerySheet.kt` | Tab Decor (0) + Tab Meme (1) |
| Cache | `studio_edit/.../data/StickerPageCache.kt` | TTL 10 phút, in-memory |
| CDN proxy | `studio_edit/.../util/AssetSourceResolver.kt` | R2 `.r2.dev` → proxy qua admin_web tại VN |

**Lưu ý quan trọng:** Mobile app gọi **`/api/assets`** (API tổng quát), **không** gọi `/api/v1/stickers`. Endpoint v1/stickers tồn tại nhưng là alias/legacy — logic tương đương, query cùng bảng Supabase.

### 1.2 Nơi lưu trữ asset

| Lớp | Công nghệ | Chi tiết |
|---|---|---|
| Database | Supabase `assets` table | `id`, `name`, `folder`, `file_url`, `mime_type`, `created_at` |
| Object storage | Cloudflare R2 | Public URL: `https://pub-d63489ecea7149a585628ea6a2c2da7f.r2.dev/assets/hash_{sha256}.{ext}` |
| Schema | `admin_web/supabase_schema_complete.sql` | `folder` là string tự do, không có bảng manifest riêng |

**Không có file manifest JSON** — catalog hoàn toàn dựa trên cột `folder` trong Supabase + URL R2.

### 1.3 Cấu trúc catalog hiện tại

```
Supabase assets.folder
├── sticker_meme      ← App mobile Tab "Nhãn dán meme"
├── Sticker_meme      ← Biến thể viết hoa (API hỗ trợ cả hai)
├── sticker_decor     ← App mobile Tab "Nhãn dán trang trí"
├── Sticker_decor
└── stickers          ← admin_web AssetSidebar "Nhãn dán Online" (KHÁC với mobile!)
```

**Hai catalog độc lập:**
- **Mobile** đọc `sticker_meme` + `sticker_decor`
- **admin_web editor** (AssetSidebar) đọc `stickers` (folder chung)

### 1.4 admin_web AssetSidebar — nguồn sticker

File: `admin_web/src/components/canvas/AssetSidebar.tsx`

| Chế độ | Nguồn | License |
|---|---|---|
| **Nhãn dán Online** | `GET /api/assets?folder=stickers&limit=150` → R2 | Phụ thuộc nội dung upload — **không kiểm soát** |
| **Vector Emojis** | OpenMoji qua jsDelivr CDN | **CC BY-SA 4.0** — đã có trong codebase |

```typescript
// OpenMoji URL pattern (đã implement)
`https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/svg/${hex}.svg`
```

OpenMoji **chỉ có trên admin_web editor**, **chưa tích hợp** vào app Android `studio_edit`.

### 1.5 API endpoints liên quan

| Endpoint | Mục đích | Consumer |
|---|---|---|
| `GET /api/assets?folder=X` | List asset theo folder, phân trang | **Mobile (chính)** |
| `GET /api/v1/stickers?folder=X` | Alias sticker, format `{stickers, hasMore}` | Chưa dùng bởi mobile |
| `GET /api/v1/stickers/preview` | 10 meme + 10 decor | Chưa dùng bởi mobile |
| `POST /api/upload` | Upload file → R2 + insert Supabase | admin_web dashboard |
| `GET /api/proxy?url=` | Proxy R2 tránh bị chặn tại VN | Mobile khi load ảnh |

### 1.6 Workflow upload asset (admin_web)

1. Vào **Dashboard → Assets** (`/assets`)
2. Mở `AssetUploaderModal` → chọn folder (mặc định: `backgrounds`, `stickers`, `fonts`, `uncategorized`) hoặc **tạo folder tùy chỉnh**
3. Upload PNG/JPG/WebP/SVG → `asset.service.ts`:
   - Hash SHA-256 → dedup
   - Upload R2 key: `assets/hash_{hash}.{ext}`
   - Insert row Supabase `assets` với `folder` + `file_url`
4. Để sticker hiện trên **mobile**, folder phải là `sticker_meme` hoặc `sticker_decor` (tạo custom folder qua modal)

---

## A. Đánh giá rủi ro nội dung hiện tại

### A.1 Tab Meme — RỦI RO RẤT CAO ⛔ KHÔNG SHIP

| Nội dung (từ screenshot) | Loại vi phạm | Mức rủi ro |
|---|---|---|
| Mr. Bean | Nhân vật / diễn viên có bản quyền (Tiger Aspect / Bean IP) | 🔴 Cao |
| Spider-Man | Marvel / Disney IP | 🔴 Rất cao |
| Hasbulla | Nhân vật công chúng / likeness rights | 🔴 Cao |
| Tom & Jerry | Warner Bros. IP | 🔴 Rất cao |
| The Simpsons | 20th Century / Disney IP | 🔴 Rất cao |
| Flork of Cows | Webcomic có bản quyền (Florkofcows) | 🔴 Cao |
| Viral kid memes (cậu bé khóc, v.v.) | Likeness / ảnh cá nhân không có consent | 🔴 Cao |
| Meme template có watermark | Nguồn không rõ / scrape internet | 🔴 Cao |

**Pháp lý:** Đây là **derivative works** sử dụng nhân vật thương hiệu, hình ảnh người thật, và meme có bản quyền — không được fair use khi nhúng vào app thương mại phân phối công khai. Rủi ro: DMCA takedown, gỡ app Google Play, kiện từ rights holder.

### A.2 Tab Decor — RỦI RO HỖN HỢP ⚠️ CẦN RÀ SOÁT

| Nội dung | Rủi ro | Ghi chú |
|---|---|---|
| Logo VinAI | 🔴 Cao | Trademark — không dùng nếu không có license |
| Watercolor art / illustration | 🟡 Trung bình | Phụ thuộc nguồn — nếu scrape Pinterest/Behance = vi phạm |
| Holiday text ("Merry Christmas", v.v.) | 🟢 Thấp | Text generic, nhưng font/graphic kèm theo cần check |
| Hoa, badge, khung trang trí generic | 🟢 Thấp–Trung bình | OK nếu tự vẽ hoặc mua license |
| Emoji/sticker không rõ nguồn | 🟡 Trung bình | Cần trace license từng file |

### A.3 Rủi ro tổng hợp theo category

```
Meme tab     ████████████████████  95/100  — BLOCKER cho release
Decor tab    ████████░░░░░░░░░░░░  40/100  — Cần thay 60–80% nội dung
OpenMoji     ██░░░░░░░░░░░░░░░░░░  10/100  — An toàn nếu có attribution
```

---

## B. Chiến lược thay thế — Nguồn có license rõ ràng

### B.1 OpenMoji (CC BY-SA 4.0) — ✅ Đã có trong codebase

| | |
|---|---|
| **License** | CC BY-SA 4.0 |
| **Định dạng** | SVG vector, color + black |
| **Số lượng** | ~4,000+ emoji |
| **Đã dùng** | `admin_web/AssetSidebar.tsx` — chế độ "Vector Emojis" |
| **Chưa dùng** | App Android `studio_edit` |
| **Yêu cầu** | Attribution + ShareAlike nếu remix |
| **CDN** | `cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/svg/` |

**Ưu điểm:** Đã tích hợp sẵn pattern URL, vector sắc nét, phù hợp tab "Biểu cảm".  
**Nhược điểm:** CC BY-SA 4.0 yêu cầu attribution; SA clause phức tạp nếu user export ảnh có sticker (cần tư vấn pháp lý).

**Khuyến nghị:** Dùng làm nguồn chính cho tab thay Meme. Pre-render subset ~80–120 emoji phổ biến → upload PNG lên R2 folder `sticker_reaction` (tránh phụ thuộc CDN runtime + dễ kiểm soát attribution).

### B.2 Twemoji (CC BY 4.0) — ✅ Khuyến nghị cho mobile

| | |
|---|---|
| **License** | CC BY 4.0 (Twitter/X open source) |
| **Định dạng** | SVG + PNG (72×72 đến 512×512) |
| **Repo** | `twitter/twemoji` |
| **Yêu cầu** | Attribution (nhẹ hơn OpenMoji — không SA) |
| **CDN** | `cdn.jsdelivr.net/gh/twitter/twemoji@master/assets/svg/{codepoint}.svg` |

**Ưu điểm:** License đơn giản hơn OpenMoji (không ShareAlike), style quen thuộc với user VN.  
**Khuyến nghị:** Dùng song song hoặc thay OpenMoji cho tab reaction trên mobile.

### B.3 Noto Emoji (Apache 2.0 / OFL) — ✅ An toàn nhất về license

| | |
|---|---|
| **License** | Apache 2.0 (emoji artwork by Google) |
| **Định dạng** | PNG, SVG (qua third-party packs) |
| **Nguồn** | `googlefonts/noto-emoji` |
| **Yêu cầu** | Attribution khuyến khích, không bắt buộc SA |

**Ưu điểm:** License permissive nhất trong 3 nguồn emoji.  
**Nhược điểm:** Style ít "sticker-like" hơn OpenMoji/Twemoji; cần export/bundle thủ công.

### B.4 SVGRepo / unDraw — ⚠️ Kiểm tra từng file

| Nguồn | License | Lưu ý |
|---|---|---|
| **unDraw** | MIT (free, attribution không bắt buộc) | Illustration flat — phù hợp decor tab |
| **SVGRepo** | **Mixed** — mỗi icon có license riêng | Phải filter CC0 / MIT / Apache; **không** dùng "Free License" không rõ |
| **Flaticon / Freepik** | Cần subscription + attribution | Không dùng bản free cho app thương mại |

**Khuyến nghị:** Chỉ dùng unDraw (MIT) cho illustration decor. Tránh SVGRepo trừ khi team có quy trình audit license từng file.

### B.5 Custom in-house illustrated pack — ✅ Khuyến nghị cho Decor

Tự thiết kế hoặc thuê illustrator tạo pack:

- Hoa lá (hoa sen, hoa mai, lavender)
- Badge "SALE", "HOT", "NEW", "% OFF"
- Khung viền sản phẩm (cosmetic, food, fashion)
- Icon shipping, guarantee, rating stars
- Seasonal VN (Tết, 8/3, 11/11, Black Friday)

**Ưu điểm:** Sở hữu 100% bản quyền, phù hợp thị trường VN, không attribution.  
**Chi phí:** ~2–4 tuần thiết kế cho pack 50–100 sticker.

### B.6 Quyết định: Xóa Meme vs Thay bằng "Biểu cảm"

| Phương án | Khuyến nghị |
|---|---|
| **Giữ tab "Meme"** với nội dung mới | ❌ Không — tên gợi ý nội dung vi phạm, khó marketing |
| **Xóa tab Meme hoàn toàn** | ⚠️ OK nhưng mất tính năng reaction |
| **Đổi tên → "Biểu cảm" / "Emoji"** + nội dung OpenMoji/Twemoji | ✅ **Khuyến nghị** |

**Lý do:** User VN cần sticker reaction khi chỉnh ảnh sản phẩm (🔥, 💯, 😍, ⭐) — nhưng phải là **emoji generic**, không phải meme nhân vật.

---

## C. Lộ trình migration kỹ thuật

### C.1 Phase 1 — Dọn nội dung (không cần sửa code) ⏱ 1–2 ngày

1. **Vào admin_web → Assets**, filter folder `sticker_meme` / `Sticker_meme`
2. **Xóa toàn bộ** asset trong folder meme (API `DELETE /api/assets?id=...` hoặc bulk)
3. Rà soát `sticker_decor` — xóa: logo thương hiệu, nhân vật, nguồn không rõ
4. Xác nhận app mobile hiện gallery trống (cache TTL 10 phút, hoặc restart app)

### C.2 Phase 2 — Upload pack mới ⏱ 3–5 ngày

**Cấu trúc folder đề xuất (giữ tương thích code hiện tại):**

```
sticker_meme      → Đổi nội dung thành emoji reaction (giữ tên folder, đổi label UI sau)
sticker_decor     → Pack trang trí thương mại
```

Hoặc tạo folder mới (cần sửa code nhỏ):

```
sticker_reaction  → Thay sticker_meme
sticker_sale      → Badge, tag giảm giá
sticker_decor     → Hoa, khung, seasonal
```

**Quy trình upload:**

```
1. Export SVG/PNG từ OpenMoji/Twemoji/unDraw
2. Đặt tên: {category}_{name}_{codepoint}.png  (vd: reaction_fire_1F525.png)
3. Upload qua AssetUploaderModal → folder sticker_meme hoặc sticker_decor
4. Verify: GET /api/assets?folder=sticker_meme&limit=5 → kiểm tra URL R2
5. Test trên app: mở editor → Sticker tool → xem quick strip + gallery
```

**Script hỗ trợ:** Xem **[OPENMOJI_IMPORT_GUIDE.md](./OPENMOJI_IMPORT_GUIDE.md)** — `admin_web/scripts/import-openmoji-stickers.mjs` copy subset curated từ `openmoji-72x72-color.zip` → `sticker_meme_import/` (upload thủ công qua Asset Library, không auto R2).

### C.3 Phase 3 — Đổi tên tab & folder (sửa code nhỏ) ⏱ 0.5 ngày

| File | Thay đổi |
|---|---|
| `StickerRemoteRepository.kt` | `FOLDER_MEME` → `FOLDER_REACTION` = `"sticker_reaction"` |
| `StickerGallerySheet.kt` | Tab label, thứ tự tab |
| `values-vi/strings.xml` | `studio_sticker_tab_meme` → `"Biểu cảm"` |
| `StickerPicker.kt` | Comment + thứ tự trộn preview |

**Không bắt buộc ngay** nếu Phase 2 upload vào `sticker_meme` với nội dung emoji (chỉ đổi label UI).

### C.4 Phase 4 — Tích hợp OpenMoji trực tiếp trên mobile (tùy chọn)

Thay vì pre-upload R2, có thể load CDN runtime (như admin_web):

```kotlin
// Pattern tương tự AssetSidebar
val url = "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/svg/$hex.svg"
```

**Trade-off:**

| Pre-upload R2 | CDN runtime |
|---|---|
| ✅ Kiểm soát nội dung | ❌ Phụ thuộc jsDelivr |
| ✅ Hoạt động offline (cache Coil) | ✅ Không cần upload |
| ✅ Không cần sửa code nhiều | ❌ Cần implement emoji picker mới |

### C.5 Đồng bộ admin_web AssetSidebar

Hiện tại `stickers` folder (admin_web) ≠ `sticker_meme`/`sticker_decor` (mobile).

**Khuyến nghị:** Thống nhất folder naming:

```
sticker_reaction   — Emoji/biểu cảm (mobile tab 2)
sticker_decor      — Trang trí (mobile tab 1)
stickers           — Alias hoặc deprecated, redirect query
```

### C.6 Yêu cầu pháp lý — Attribution

| Nguồn | Bắt buộc attribution? | Gợi ý vị trí |
|---|---|---|
| OpenMoji (CC BY-SA 4.0) | **Có** | Settings → "Giấy phép" hoặc About screen |
| Twemoji (CC BY 4.0) | **Có** | Cùng màn hình |
| Noto Emoji (Apache 2.0) | Khuyến khích | Cùng màn hình |
| unDraw (MIT) | Không bắt buộc | Nên ghi credit |
| In-house | Không | — |

**Mẫu attribution (đặt trong Settings → Thông tin pháp lý):**

```
Sticker emoji: OpenMoji (CC BY-SA 4.0) — openmoji.org
Một số emoji: Twemoji (CC BY 4.0) — twemoji.twitter.com
Hình minh họa trang trí: © [Tên công ty] / unDraw (MIT)
```

**Lưu ý CC BY-SA:** Nếu user export ảnh có OpenMoji sticker, về mặt lý thuyết ảnh export có thể rơi vào SA clause. Thực tế app photo editor ít bị kiện vì output là composite raster, nhưng nên **tư vấn luật sư** trước release.

**Không cần attribution footer trên mỗi ảnh export** — chỉ cần màn hình Licenses trong app.

---

## D. Cấu trúc sticker pack đề xuất — Editor ảnh sản phẩm VN

### D.1 Tab "Trang trí" (`sticker_decor`) — ~60–80 sticker

| Sub-category | Số lượng | Ví dụ |
|---|---|---|
| 🌸 Hoa & lá | 15 | Hoa sen, hoa mai, lavender, lá xanh |
| 🏷️ Badge giảm giá | 15 | SALE, -50%, HOT, NEW, FREE SHIP |
| ⭐ Rating & trust | 10 | 5 sao, "Chính hãng", "Bảo hành" |
| 🎀 Khung & ribbon | 10 | Ribbon đỏ, khung vàng, banner |
| 🎊 Seasonal VN | 10 | Tết, 8/3, 11/11, Noel, Valentine |
| ✨ Hiệu ứng | 10 | Sparkle, glow, arrow, highlight |

### D.2 Tab "Biểu cảm" (`sticker_reaction`, thay meme) — ~80–100 sticker

| Sub-category | Số lượng | Nguồn |
|---|---|---|
| 😀 Mặt cười | 15 | OpenMoji/Twemoji |
| ❤️ Tim & love | 10 | OpenMoji/Twemoji |
| 👍 Tay & gesture | 10 | OpenMoji/Twemoji |
| 🔥 Trending | 10 | 🔥💯✨⭐🎉 |
| 🛒 Shopping | 10 | 🛒💰🎁📦 |
| 😱 Reaction mạnh | 10 | 😱😭🤯💀 |
| 🐱 Cute animals | 15 | OpenMoji (không dùng nhân vật) |

### D.3 Nguyên tắc chọn sticker

1. **Không có khuôn mặt người thật** (trừ emoji stylized)
2. **Không có nhân vật hoạt hình / phim / game**
3. **Không có logo thương hiệu** (VinAI, Nike, v.v.)
4. **Không có meme template có watermark**
5. **Ưu tiên vector/PNG nền trong suốt**, kích thước 256–512px
6. **Đặt tên file có convention** để dễ quản lý và audit

---

## E. Danh sách KHÔNG ĐƯỢC SHIP ⛔

Dựa trên screenshot thư viện hiện tại — **xóa ngay trước release:**

### E.1 Tab Meme — XÓA TOÀN BỘ

- [ ] Mr. Bean (mọi biến thể)
- [ ] Spider-Man / Marvel characters
- [ ] Hasbulla
- [ ] Tom & Jerry
- [ ] The Simpsons
- [ ] Flork of Cows
- [ ] Viral kid crying meme / "cậu bé khóc"
- [ ] Bất kỳ meme có **khuôn mặt người thật**
- [ ] Bất kỳ meme có **nhân vật hoạt hình có bản quyền**
- [ ] Meme template có watermark (9GAG, iFunny, v.v.)
- [ ] Deep-fried / distorted meme faces
- [ ] Political figures / celebrity likeness

### E.2 Tab Decor — XÓA CỤ THỂ

- [ ] Logo VinAI (và mọi logo doanh nghiệp khác)
- [ ] Watercolor art không rõ nguồn / scrape từ internet
- [ ] Illustration có signature artist khác (không có license)
- [ ] Nhân vật hoạt hình lẫn trong decor
- [ ] Ảnh chụp người thật
- [ ] Font/sticker có watermark stock site (Shutterstock, Freepik free tier)

### E.3 Toàn hệ thống

- [ ] Bất kỳ asset nào trong R2/Supabase **không trace được nguồn gốc**
- [ ] Asset được scrape từ Google Images, Pinterest, WeHeartIt
- [ ] Asset từ "sticker pack" APK crack / Telegram channel

---

## F. Checklist trước release

```
□ Xóa 100% nội dung sticker_meme hiện tại
□ Rà soát & xóa asset vi phạm trong sticker_decor
□ Upload pack decor mới (in-house hoặc unDraw/MIT)
□ Upload pack reaction mới (OpenMoji/Twemoji, pre-render PNG)
□ Test mobile: StickerPicker + StickerGallerySheet load đúng
□ Test proxy R2 tại VN (mạng Viettel/VinaPhone)
□ Thêm màn hình "Giấy phép" / Open Source Licenses
□ Ghi attribution OpenMoji + Twemoji
□ (Tùy chọn) Đổi label tab "Meme" → "Biểu cảm"
□ (Tùy chọn) Đổi folder sticker_meme → sticker_reaction
□ Document nguồn gốc từng batch upload (spreadsheet audit)
□ Tư vấn luật sư về CC BY-SA cho output ảnh user export
```

---

## G. Ước tính effort

| Hạng mục | Thời gian | Cần sửa code? |
|---|---|---|
| Xóa asset vi phạm | 2–4 giờ | Không |
| Tạo + upload pack decor (in-house) | 1–2 tuần | Không |
| Tạo + upload pack reaction (OpenMoji export) | 2–3 ngày | Không |
| Đổi tên tab Meme → Biểu cảm | 2 giờ | Có (strings + comments) |
| Đổi folder constant | 2 giờ | Có (Kotlin + API) |
| Màn hình Licenses | 4 giờ | Có (UI mới) |
| Tích hợp OpenMoji CDN trên mobile | 1–2 ngày | Có (feature mới) |

**Minimum viable (ship an toàn):** Phase 1 + Phase 2 + Attribution screen = **~1 tuần**, không cần sửa code app.

---

## H. Tham chiếu file codebase

| File | Vai trò |
|---|---|
| `studio_edit/.../StickerRemoteRepository.kt` | API client, folder constants |
| `studio_edit/.../StickerPicker.kt` | Quick strip UI |
| `studio_edit/.../StickerGallerySheet.kt` | Gallery 2 tab |
| `studio_edit/.../StickerGalleryViewModel.kt` | Pagination state |
| `studio_edit/.../StickerPageCache.kt` | In-memory cache |
| `studio_edit/.../ThemeplateEditorViewModel.kt` | `loadStickerPreview()` |
| `admin_web/.../AssetSidebar.tsx` | OpenMoji integration (web only) |
| `admin_web/.../asset.service.ts` | Upload → R2 + Supabase |
| `admin_web/.../api/assets/route.ts` | Asset list API |
| `admin_web/.../api/v1/stickers/route.ts` | Sticker-specific API (legacy) |
| `admin_web/.../cdn-rewriter.ts` | R2 public base URL |
| `admin_web/supabase_schema_complete.sql` | `assets` table schema |

---

*Tài liệu này mang tính tư vấn kỹ thuật–vận hành, không thay thế tư vấn pháp lý chuyên nghiệp. Khuyến nghị xác nhận với luật sư sở hữu trí tuệ trước khi phát hành app thương mại.*

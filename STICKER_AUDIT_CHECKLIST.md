# Checklist Rà Soát Bản Quyền Sticker — Chọn Lọc (Không Xóa Hết)

> **Mục tiêu:** Biết sticker nào có vấn đề license để **chỉ gỡ những cái xấu**, giữ lại sticker đẹp và hợp pháp.  
> **Ngày:** 11/07/2026  
> **Tham chiếu:** [STICKER_COPYRIGHT_GUIDE.md](./STICKER_COPYRIGHT_GUIDE.md)

---

## Tóm tắt nhanh

| Câu hỏi | Trả lời |
|---|---|
| Database có cột `license` / `source_url` không? | **Không** — chỉ có `name`, `folder`, `file_url`, `mime_type`, `source_path` (hiếm khi dùng) |
| Làm sao biết sticker nào xấu? | **Audit thủ công** theo checklist dưới + export CSV |
| Có cần xóa hết tab Meme? | **Không bắt buộc xóa hết** — nhưng ~95% nội dung meme hiện tại là 🔴 REMOVE |
| Tab Decor có gì giữ được? | Hoa generic, badge tự vẽ, text holiday — **giữ**; logo VinAI, art không rõ nguồn — **gỡ** |

---

## 1. Schema hiện tại — Metadata có gì?

Bảng Supabase `assets` (xem `admin_web/supabase_schema_complete.sql`):

| Cột | Có license? | Ghi chú |
|---|---|---|
| `id` | — | UUID |
| `name` | — | Tên file gốc khi upload (duy nhất manh mối nguồn) |
| `folder` | — | Phân loại: `sticker_meme`, `sticker_decor`, `stickers`, … |
| `file_url` | — | URL R2: `.../assets/hash_{sha256}.png` |
| `file_size`, `mime_type` | — | Kỹ thuật |
| `source_path` | — | Chỉ dùng khi sync từ `studio_edit/src/main/assets` — **dashboard upload không ghi** |
| `category_id` | — | Phân loại template, không phải license |
| `width`, `height` | — | Tùy record |
| `created_at` | — | Ngày upload |

**Kết luận:** Upload qua admin_web (`asset.service.ts`) **không lưu** license, URL nguồn, hay attribution. Muốn audit phải dựa vào **tên file + xem ảnh + tra cứu nguồn**.

### Cấu trúc folder sticker

```
sticker_meme / Sticker_meme   → App Android tab "Meme"
sticker_decor / Sticker_decor → App Android tab "Trang trí"
stickers / sticker            → admin_web editor (AssetSidebar) — KHÁC mobile!
```

Hai catalog **độc lập**. Audit cả hai nếu muốn đồng bộ web + mobile.

---

## 2. A. Red flags — Gỡ ngay (🔴 REMOVE)

Nhìn từng sticker, nếu có **bất kỳ** dấu hiệu sau → đánh dấu **REMOVE**:

### Nhân vật & IP có bản quyền
- [ ] Nhân vật phim / hoạt hình / game (Spider-Man, Simpsons, Tom & Jerry, Pokémon, …)
- [ ] Diễn viên / celebrity (Mr. Bean, Hasbulla, idol K-pop, chính trị gia)
- [ ] Webcomic có bản quyền (Flork of Cows, Pepe có variant bản quyền, …)
- [ ] Nhân vật mascot thương hiệu (Mickey, Minion, Doraemon, …)

### Người thật & meme viral
- [ ] Khuôn mặt người thật (ảnh meme, reaction face, deep-fried meme)
- [ ] Trẻ em / ảnh cá nhân không consent
- [ ] Meme template có watermark (9GAG, iFunny, Reddit username, …)

### Thương hiệu
- [ ] Logo doanh nghiệp (VinAI, Nike, Apple, …)
- [ ] Logo app/mạng xã hội dùng như sticker trang trí

### Nguồn không rõ
- [ ] Không nhớ ai upload / scrape từ Google Images, Pinterest, Telegram pack
- [ ] Tên file random (`IMG_2847.png`, `download (3).png`) + không trace được
- [ ] Style stock photo / Shutterstock / Freepik free tier (thường có watermark mờ hoặc style nhận diện được)

### Kỹ thuật gợi ý scrape
- [ ] File hash R2 nhưng `name` là tên meme tiếng Anh lạ
- [ ] Nhiều sticker cùng style pack crack APK

---

## 3. B. Green flags — Giữ (🟢 KEEP)

An toàn giữ nếu **ít nhất một** điều sau đúng:

| Loại | Ví dụ | Điều kiện |
|---|---|---|
| Tự thiết kế | Hoa sen, badge SALE, khung sản phẩm | Team/illustrator sở hữu bản quyền |
| Emoji có license | OpenMoji, Twemoji, Noto Emoji | Đã ghi attribution trong app |
| CC0 / MIT / Apache | unDraw illustration, icon CC0 | Có link nguồn + license trong spreadsheet |
| Hình học đơn giản | Sao, mũi tên, sparkle, hình tròn/vuông | Không copy artwork có bản quyền |
| Text generic | "SALE", "NEW", "Merry Christmas" | Font/graphic kèm theo cũng phải hợp pháp |
| Florals generic | Hoa lavender, lá, wreath đơn giản | Không phải bản sao tranh watercolor có chữ ký artist |

---

## 4. C. Gray zone — Cần verify (🟡 VERIFY)

| Tình huống | Hành động |
|---|---|
| Watercolor / illustration đẹp, không nhớ nguồn | Reverse image search → tìm artist → mua license hoặc REMOVE |
| Sticker "giống emoji" nhưng style lạ | So sánh với OpenMoji/Twemoji — nếu không khớp nguồn mở → VERIFY |
| Pack Telegram/WhatsApp đã convert PNG | Mặc định VERIFY — hầu hết không có quyền thương mại |
| Font chữ trang trí lạ | Kiểm tra font license riêng |
| Ảnh seasonal (Tết, Noel) có style professional | VERIFY nguồn commission hoặc stock đã mua |

**Quy tắc an toàn:** Không verify được trong 15 phút → **REMOVE** (có thể thay bằng bản hợp pháp sau).

---

## 5. D. Quy trình audit thư viện R2/Supabase — Từng bước

### Bước 1 — Export danh sách

**Cách A — Script (khuyến nghị):**

```bash
cd admin_web
node scripts/export-sticker-audit.mjs
# Hoặc từng folder:
node scripts/export-sticker-audit.mjs --folder=sticker_meme
node scripts/export-sticker-audit.mjs --folder=sticker_decor
```

File output: `admin_web/sticker_audit_export.csv`

**Cách B — API (khi admin_web đang chạy):**

```
GET /api/assets?folder=sticker_meme&limit=500&page=1
GET /api/assets?folder=sticker_decor&limit=500&page=1
GET /api/assets?folder=stickers&limit=500&page=1
```

**Cách C — Supabase Dashboard:**

Table Editor → `assets` → Filter `folder` = `sticker_meme` → Export CSV

### Bước 2 — Tạo spreadsheet audit

Thêm cột (script đã có sẵn một số cột):

| Cột | Ai điền | Giá trị |
|---|---|---|
| `triage` | Bạn | `KEEP` / `REMOVE` / `VERIFY` |
| `source_url` | Bạn | Link gốc (OpenMoji, unDraw, commission invoice, …) |
| `license_type` | Bạn | `CC0`, `MIT`, `CC-BY-4.0`, `in-house`, `commercial-purchased`, `unknown` |
| `notes` | Bạn | Ghi chú ngắn |
| `auto_flag` | Script | Gợi ý `REVIEW` từ tên file — **chỉ là hint** |

**Mẹo Excel/Sheets:** Cột preview ảnh:

```
=IMAGE(file_url)
```

### Bước 3 — Review theo batch (20–30 sticker/lần)

1. Mở admin_web → **Dashboard → Assets**
2. Filter folder `sticker_meme` (hoặc `sticker_decor`)
3. So sánh visual với CSV
4. Đánh dấu triage từng dòng
5. Nghỉ giữa các batch để tránh miss

**Thứ tự ưu tiên audit:**

1. `sticker_meme` — rủi ro cao nhất
2. `sticker_decor` — rủi ro hỗn hợp
3. `stickers` — admin_web editor (có thể trùng file R2 với mobile)

### Bước 4 — Document nguồn từng batch upload

Tạo sheet phụ `upload_log`:

| Ngày | Folder | Số file | Nguồn | License | Người upload |
|---|---|---|---|---|---|
| 2026-03-01 | sticker_decor | 45 | Illustrator commission | in-house | @name |

### Bước 5 — Triage summary

Cuối mỗi folder, tổng hợp:

```
sticker_meme:  KEEP __ | REMOVE __ | VERIFY __
sticker_decor: KEEP __ | REMOVE __ | VERIFY __
```

**Chỉ xóa (hoặc move sang folder `quarantine`) những dòng REMOVE sau khi review 2 lần.**

### Bước 6 — Thực thi (khi đã chắc chắn)

- admin_web Assets → chọn từng sticker REMOVE → Delete
- Hoặc bulk select trong AssetGallery
- **Không** dùng "Delete folder" trừ khi muốn xóa 100% folder đó

---

## 6. E. Công cụ hỗ trợ

| Công cụ | Dùng khi | Link |
|---|---|---|
| **Google Lens / TinEye** | Reverse image search — tìm nguồn gốc | lens.google.com, tineye.com |
| **Yandex Images** | Tốt cho meme / ảnh đã qua chỉnh sửa | yandex.com/images |
| **EXIF viewer** | Kiểm tra metadata ảnh JPG (camera, software) | exiftool, metapicz.com |
| **Filename patterns** | Gợi ý nguồn | `openmoji_*`, `twemoji_*`, `undraw_*` = tốt; `IMG_*`, `download*` = nghi |
| **OpenMoji browser** | So khớp emoji vector | openmoji.org |
| **Twemoji preview** | So khớp emoji | twemoji.twitter.com |

### Heuristic tên file (script tự đánh flag)

Script `export-sticker-audit.mjs` flag `REVIEW` nếu tên chứa: `bean`, `spider`, `simpson`, `hasbulla`, `vinai`, `watermark`, `meme`, … — **không thay thế audit mắt thường**.

---

## 7. F. Metadata tối thiểu nên thêm (going forward)

Schema **hiện không hỗ trợ** — cần migration nếu muốn lưu license trong DB.

### Đề xuất cột mới (ưu tiên)

```sql
ALTER TABLE assets
  ADD COLUMN IF NOT EXISTS source_url TEXT,
  ADD COLUMN IF NOT EXISTS license_type TEXT,  -- 'CC0','MIT','CC-BY-4.0','in-house','commercial','unknown'
  ADD COLUMN IF NOT EXISTS attribution TEXT,
  ADD COLUMN IF NOT EXISTS audit_status TEXT DEFAULT 'pending';  -- 'pending','approved','rejected'
```

### Quy trình upload mới (không cần code ngay)

1. Đặt tên file convention: `{license}_{source}_{name}.png`  
   Ví dụ: `cc-by_openmoji_fire_1F525.png`, `inhouse_floral_lotus_01.png`
2. Ghi vào spreadsheet `upload_log` trước khi upload
3. Chỉ upload vào `sticker_meme` / `sticker_decor` sau khi điền `license_type`

### Workaround không cần migration

- Dùng **Google Sheet** làm nguồn sự thật (master audit list)
- Cột `id` Supabase làm khóa liên kết
- Folder `quarantine` cho sticker VERIFY chưa xong

---

## 8. G. Hướng dẫn cụ thể — Tab Meme vs Decor (từ screenshot)

### Tab Meme (`sticker_meme`) — Thay từng loại, không cần xóa sạch nếu đã thay thế

| Loại trong screenshot | Quyết định | Thay bằng |
|---|---|---|
| Mr. Bean, Spider-Man, Simpsons, Tom & Jerry | 🔴 **REMOVE** | Emoji reaction OpenMoji/Twemoji |
| Hasbulla, viral kid crying | 🔴 **REMOVE** | 😭😂🤣 từ Twemoji |
| Flork of Cows | 🔴 **REMOVE** | Biểu cảm generic (không nhân vật) |
| Meme có watermark | 🔴 **REMOVE** | — |
| Deep-fried / distorted faces | 🔴 **REMOVE** | — |
| Emoji/sticker đơn giản không nhân vật (🔥❤️👍) | 🟢 **KEEP** nếu từ nguồn mở | Giữ hoặc thay bản Twemoji chính thức |

**Chiến lược thay thế từng cái:**

1. REMOVE nhóm 🔴 trước (có thể bulk theo visual batch)
2. Giữ emoji generic còn lại tạm thời
3. Upload dần pack `sticker_reaction` từ OpenMoji/Twemoji
4. Đổi label UI "Meme" → "Biểu cảm" khi sẵn sàng (không bắt buộc để audit)

### Tab Decor (`sticker_decor`) — Giữ nhiều hơn, gỡ có chọn lọc

| Loại trong screenshot | Quyết định | Ghi chú |
|---|---|---|
| **Logo VinAI** | 🔴 **REMOVE** | Trademark — không có license thì không dùng |
| Logo doanh nghiệp khác | 🔴 **REMOVE** | Tương tự |
| Watercolor art đẹp | 🟡 **VERIFY** | Giữ **nếu** commission / mua stock; REMOVE nếu scrape Pinterest |
| Holiday text ("Merry Christmas") | 🟢 **KEEP** | Text generic — check font đi kèm |
| Hoa, badge, khung generic | 🟢 **KEEP** | Nếu tự thiết kế hoặc CC0 |
| Emoji trang trí không rõ nguồn | 🟡 **VERIFY** | Trace hoặc thay OpenMoji |
| Nhân vật hoạt hình lẫn trong decor | 🔴 **REMOVE** | — |

---

## 9. Bảng quyết định nhanh (1 sticker)

```
                    ┌─────────────────────────┐
                    │ Nhìn sticker            │
                    └───────────┬─────────────┘
                                ▼
              ┌─────────────────────────────────────┐
              │ Có nhân vật / người thật / logo?    │
              └─────────┬───────────────┬───────────┘
                     CÓ               KHÔNG
                      ▼                 ▼
                  🔴 REMOVE     ┌──────────────────┐
                                │ Nhớ rõ nguồn +    │
                                │ license?          │
                                └───┬──────────┬────┘
                                   CÓ        KHÔNG
                                    ▼          ▼
                               🟢 KEEP    🟡 VERIFY
                                              │
                                    verify OK? ├── CÓ → 🟢 KEEP
                                              └── KHÔNG → 🔴 REMOVE
```

---

## 10. Sau khi audit — Checklist release

```
□ Export CSV và hoàn thành cột triage cho sticker_meme
□ Export CSV và hoàn thành cột triage cho sticker_decor
□ Đã REMOVE (không phải xóa hết) các sticker 🔴
□ Đã VERIFY và quyết định các sticker 🟡
□ Giữ lại các sticker 🟢 và ghi source_url
□ Upload batch thay thế cho khoảng trống (OpenMoji/Twemoji/in-house)
□ Thêm màn hình Licenses trong app (attribution)
□ Tạo upload_log cho mọi batch mới
```

---

## 11. Script & tài liệu liên quan

| File | Mục đích |
|---|---|
| `admin_web/scripts/export-sticker-audit.mjs` | Export CSV audit (read-only) |
| [STICKER_COPYRIGHT_GUIDE.md](./STICKER_COPYRIGHT_GUIDE.md) | Chiến lược thay thế & nguồn license |
| `admin_web/src/domains/assets/asset.service.ts` | Logic upload (không có license) |
| `GET /api/assets?folder=...` | API list asset |

---

*Tài liệu mang tính hướng dẫn vận hành. Không thay thế tư vấn pháp lý. Khi nghi ngờ → REMOVE hoặc hỏi luật sư.*

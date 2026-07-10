# Hướng dẫn Import OpenMoji cho Tab Sticker Meme

> **Mục tiêu:** Thay nội dung `sticker_meme` bằng emoji reaction an toàn bản quyền (OpenMoji CC BY-SA 4.0).  
> **Phạm vi:** Script cục bộ + upload thủ công qua admin_web — **không** tự động push R2.

Xem thêm: [STICKER_COPYRIGHT_GUIDE.md](./STICKER_COPYRIGHT_GUIDE.md) (bối cảnh pháp lý & kiến trúc).

---

## 1. Chọn file zip nào?

| Release asset | Kích thước | Dùng khi |
|---|---|---|
| **`openmoji-72x72-color.zip`** | 72×72 px PNG | ✅ **Khuyến nghị** cho mobile sticker (~256px hiển thị) |
| `openmoji-618x618-color.zip` | 618×618 px PNG | Chỉ khi cần upscale chất lượng cao / in ấn |
| `openmoji-svg-color.zip` | SVG vector | admin_web editor (CDN) — không cần cho mobile PNG pack |

**Lý do chọn 72×72:** App mobile hiển thị sticker ~256px; 72px PNG upscale 3–4× vẫn sắc nét, file nhẹ (~2–5 KB/sticker), phù hợp CDN.

**Tải về:**
1. [github.com/hfg-gmuend/openmoji/releases](https://github.com/hfg-gmuend/openmoji/releases) → bản mới nhất
2. Tải `openmoji-72x72-color.zip`
3. Giải nén (hoặc truyền thẳng `.zip` vào script)

---

## 2. Quy ước đặt tên file OpenMoji

Mỗi PNG/SVG được đặt theo **Unicode hex code** (viết hoa, không prefix `U+`):

| File | Ý nghĩa |
|---|---|
| `1F600.png` | 😀 Grinning face |
| `1F44D.png` | 👍 Thumbs up |
| `2764.png` | ❤ Red heart |
| `1F468-200D-1F469-200D-1F467.png` | 👨‍👩‍👧 ZWJ sequence (gia đình) |

Script curated pack **chỉ dùng mã đơn** (không ZWJ) để tránh phức tạp và file thiếu.

### Cách tra mã hex

| Công cụ | URL | Ghi chú |
|---|---|---|
| OpenMoji browser | [openmoji.org](https://openmoji.org) | Search emoji → xem hex trên trang chi tiết |
| openmoji.json | `data/openmoji.json` trong repo | Trường `hexcode`, `group`, `tags` |
| Unicode reference | [unicode.org/emoji/charts](https://unicode.org/emoji/charts/full-emoji-list.html) | Chuẩn Unicode chính thức |

---

## 3. Lọc an toàn — tránh brand & Private Use Area

OpenMoji có **hai loại "extra"** không nên ship trong sticker app:

| Nhóm | Ví dụ | Rủi ro |
|---|---|---|
| `extras-openmoji` / subgroup `brand` | Apple `F8FF`, Windows `F000`, Ubuntu `E0FF` | Logo thương hiệu |
| Private Use Area `E000`–`F8FF` | Mọi mã bắt đầu `E` hoặc `F` | Không phải emoji Unicode chuẩn |
| `extras-unicode` | Ký hiệu mở rộng | Một số không phải emoji reaction |

**Script tự loại:**
- Allowlist curated ~78 mã an toàn (xem `admin_web/scripts/openmoji-reaction-allowlist.json`)
- Block prefix `E` / `F` và danh sách brand đã biết (`F8FF`, `F000`, …)
- Tuỳ chọn `--metadata=openmoji.json` để bỏ `group=extras-*`, `subgroups=brand`, tag chứa `brand`/`logo`

---

## 4. Tải `openmoji.json` (metadata filtering)

File nằm trong repo GitHub, **không** có trong zip PNG release.

### Cách 1 — Tải trực tiếp (nhanh)

```powershell
# Từ thư mục admin_web/
curl -L -o openmoji.json https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/data/openmoji.json
```

### Cách 2 — Clone repo (đầy đủ data/)

```powershell
git clone --depth 1 https://github.com/hfg-gmuend/openmoji.git
# File: openmoji/data/openmoji.json
# Brand extras: openmoji/data/extras-openmoji.csv
```

### Cấu trúc mỗi entry trong `openmoji.json`

```json
{
  "emoji": "😀",
  "hexcode": "1F600",
  "group": "smileys-emotion",
  "subgroups": "face-smiling",
  "annotation": "grinning face",
  "tags": "cheerful, face, grin, happy, smile",
  "openmoji_tags": "smile, happy"
}
```

**Groups chuẩn Unicode** (an toàn cho reaction pack): `smileys-emotion`, `people-body`, `animals-nature`, `food-drink`, `activities`, `objects`, `symbols`.

**Groups cần tránh:** `extras-openmoji`, `extras-unicode`.

---

## 5. Chạy script import

Script: `admin_web/scripts/import-openmoji-stickers.mjs`  
Allowlist: `admin_web/scripts/openmoji-reaction-allowlist.json`

### Bước 1 — Chuẩn bị

```powershell
cd C:\Users\Toshiba\Desktop\imageProduction-main\admin_web

# Tải metadata (tuỳ chọn nhưng khuyến nghị)
curl -L -o openmoji.json https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/data/openmoji.json
```

### Bước 2 — Chạy import

**Từ folder đã giải nén:**

```powershell
node scripts/import-openmoji-stickers.mjs --input=..\downloads\openmoji-72x72-color --metadata=openmoji.json
```

**Trực tiếp từ zip:**

```powershell
node scripts/import-openmoji-stickers.mjs --input=..\downloads\openmoji-72x72-color.zip --metadata=openmoji.json
```

**Dry-run (xem kế hoạch, không copy):**

```powershell
node scripts/import-openmoji-stickers.mjs --input=..\downloads\openmoji-72x72-color.zip --dry-run
```

**Tuỳ chỉnh output:**

```powershell
node scripts/import-openmoji-stickers.mjs --input=..\downloads\openmoji-72x72-color.zip --output=.\sticker_meme_import
```

### Output

- Thư mục mặc định: `admin_web/sticker_meme_import/`
- Tên file: `openmoji_1f600.png`, `openmoji_1f44d.png`, …
- Console in: số copied / missing / skipped

---

## 6. Curated allowlist (~78 sticker)

File: `admin_web/scripts/openmoji-reaction-allowlist.json`

| Category | Số lượng | Ví dụ |
|---|---|---|
| smileys | 21 | 😀😂😍😎🤔 |
| strong_reactions | 10 | 😱😭🤯💀 |
| gestures | 10 | 👍👎👏🙏 |
| hearts | 8 | ❤️💕💖 |
| trending | 8 | 🔥💯✨⭐ |
| shopping | 6 | 🛒💰🎁📦 |
| cute_fun | 10 | 🤡👻🐱🐶 |
| symbols | 6 | ⚡✅❌ |

**Mở rộng allowlist:** Sửa JSON → thêm `hexcode` vào category phù hợp → chạy lại script. Tra mã tại [openmoji.org](https://openmoji.org), kiểm tra `group` ≠ `extras-*`.

---

## 7. Upload lên Asset Library (thủ công)

Script **không** upload R2 (cần credentials). Sau khi import:

1. Mở **admin_web** → **Assets** (Asset Library)
2. Tạo / chọn folder **`sticker_meme`** (mobile app đọc folder này)
3. Upload toàn bộ file trong `sticker_meme_import/`
4. Xác minh API:

```text
GET /api/assets?folder=sticker_meme&limit=5
```

5. Trên app Android: mở editor → tab sticker meme → kiểm tra preview load

### Convention đặt tên sau upload

| Pattern | Ý nghĩa audit |
|---|---|
| `openmoji_1f600.png` | Nguồn OpenMoji, hex traceable |
| `cc-by_openmoji_fire_1f525.png` | Mở rộng (xem STICKER_AUDIT_CHECKLIST) |

---

## 8. Checklist nhanh

```
□ Tải openmoji-72x72-color.zip từ GitHub releases
□ (Khuyến nghị) Tải openmoji.json cho metadata filter
□ Chạy import-openmoji-stickers.mjs
□ Kiểm tra summary: copied ≥ 70, missing = 0
□ Upload sticker_meme_import/ → folder sticker_meme
□ Xóa sticker meme cũ vi phạm bản quyền (xem STICKER_COPYRIGHT_GUIDE §E)
□ Thêm attribution OpenMoji trong app (Settings / About)
```

---

## 9. Files liên quan

| File | Vai trò |
|---|---|
| `admin_web/scripts/import-openmoji-stickers.mjs` | Script copy + filter |
| `admin_web/scripts/openmoji-reaction-allowlist.json` | Danh sách hex curated |
| `admin_web/sticker_meme_import/` | Output PNG (sau khi chạy) |
| `STICKER_COPYRIGHT_GUIDE.md` | Bối cảnh pháp lý |
| `STICKER_AUDIT_CHECKLIST.md` | Audit folder hiện có |

---

## 10. License reminder

OpenMoji: **CC BY-SA 4.0** — cần attribution trong app:

```text
Sticker emoji: OpenMoji (CC BY-SA 4.0) — https://openmoji.org
```

Không ship logo brand từ OpenMoji extras (`F8FF` Apple, `F000` Windows, v.v.) dù có trong bộ tải về.

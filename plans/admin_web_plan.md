# Admin Web Plan

Mục tiêu của `admin_web`:
- Dùng như một tool nội bộ để thiết kế theme/template.
- Chạy local là đủ.
- Lưu dữ liệu vào Supabase.
- App Android chỉ cần đọc public API để hiện danh mục và theme.

## Nguyên tắc

- Đừng phức tạp hóa.
- Không cần auth/login nếu chỉ mình dùng.
- Không cần Vercel nếu không deploy.
- Không cần R2, dùng Supabase Storage.

## Luồng đơn giản

1. Tạo `category` trong `admin_web`.
2. Tạo `template` trong category đó.
3. Upload asset lên Supabase Storage.
4. Publish template.
5. App Android gọi public API để lấy:
   - danh mục
   - template theo danh mục

## Trạng thái hiện tại

- [x] `admin_web` là project Next.js riêng.
- [x] Supabase schema có `categories`, `templates`, `assets`.
- [x] Asset upload/list/delete dùng Supabase Storage.
- [x] Có trang list/detail/import/export template.
- [x] Có canvas editor Fabric.js.
- [x] Có public API `GET /api/v1/categories`.
- [x] Có public API `GET /api/v1/templates`.
- [x] Kéo-thả asset vào canvas.
- [x] Kéo-thả layer để đổi thứ tự trong layer panel.
- [x] Preview hover lớn cho asset library.

## Việc đã xong

### Phase 1 - Template Manager

- [x] Template list page
- [x] Template detail page
- [x] Create template
- [x] Upload asset lên Supabase Storage
- [x] Export ZIP/JSON
- [x] Import ZIP
- [x] Category manager

### Phase 2 - Canvas Editor

- [x] Thêm layer từ asset library
- [x] Di chuyển layer trên canvas
- [x] Resize, rotate, flip
- [x] Ẩn/hiện, khóa, xóa, duplicate layer
- [x] Opacity và shadow
- [x] Undo/redo
- [x] Serialize ra CloudTemplate JSON
- [x] Render thumbnail khi lưu
- [x] Xuất ZIP bundle với asset từ Supabase Storage

### Phase 3 - Asset Management

- [x] Asset grid
- [x] Batch upload
- [x] Folder/category
- [x] Search
- [x] Delete asset từ DB + Storage
- [x] Preview hover lớn
- [x] Drag asset vào canvas

### Phase 4 - App Android

- [x] API public lấy categories
- [x] API public lấy templates published
- [x] App Android lấy categories từ API public, có fallback local
- [x] App Android lấy danh sách theme/template thật từ API public
- [x] App Android mở được template remote trong editor
- [ ] Chuẩn hóa thêm format response nếu app cần khác

## Việc cần làm tiếp

Ưu tiên 1:
- [x] API base URL dùng `BuildConfig` chung cho app và `studio_edit`
- [ ] Nếu cần, chuẩn hóa thêm response của public API cho app Android

Ưu tiên 2:
- [ ] Keyboard shortcuts và polish thêm

## Ghi chú kỹ thuật

- `templates.canvas_data` lưu JSON CloudTemplate.
- `templates.fabric_state` lưu state Fabric.js để mở lại editor.
- Asset URL nên là public URL từ Supabase Storage.
- Khi publish template, app Android chỉ cần đọc API public là đủ.

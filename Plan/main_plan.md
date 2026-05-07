# 🗺️ MAIN DEVELOPMENT PLAN
## Mục tiêu: Đạt 70% tính năng của Background Eraser (InShot Inc.)
> **Đối thủ tham chiếu:** [Background Eraser & Photo Editor](https://play.google.com/store/apps/details?id=photoeditor.cutout.backgrounderaser)  
> **Thời gian thực hiện dự kiến:** 6 Phases — 9 đến 12 tháng  
> **Triết lý phát triển:** Chất lượng trước số lượng — mỗi tính năng phải hoàn chỉnh, polished, không có bug nghiêm trọng trước khi qua phase mới.

---

## 📊 Phân tích khoảng cách (Gap Analysis)

### Tính năng đối thủ đang có (Background Eraser - InShot)
| Nhóm | Tính năng | Mức độ ưu tiên |
|---|---|---|
| **AI Core** | Xóa phông tự động AI | ✅ Đã có |
| **AI Core** | Xóa phông thủ công (Brush erase/restore) | ⚠️ Cần cải thiện |
| **AI Core** | Tách đối tượng theo vùng | ❌ Chưa có |
| **Editor** | Trình sửa ảnh đa lớp | ✅ Cơ bản đã có |
| **Editor** | Opacity / Blend Mode cho lớp | ❌ Chưa có |
| **Editor** | Kéo thả sắp xếp layer | ❌ Chưa có |
| **Background** | Thư viện nền (ảnh, màu, gradient) | ⚠️ Cần mở rộng |
| **Background** | Nền AI sinh tạo (AI Background) | ❌ Chưa có |
| **Text** | Font chữ phong phú | ❌ Rất yếu |
| **Text** | Text effects (Shadow, Stroke, Gradient) | ❌ Chưa có |
| **Sticker** | Bộ sưu tập Sticker | ❌ Chưa có |
| **Export** | Xuất ảnh chất lượng cao (4K) | ⚠️ Cần kiểm tra |
| **UX** | Onboarding / Tutorial | ❌ Chưa có |
| **Monetization** | Subscription + IAP hợp lý | ⚠️ Cần cải thiện flow |
| **Performance** | Quản lý bộ nhớ bitmap | ❌ Nguy cơ OOM cao |

---

## 🗓️ Lộ trình các Phase

```
Phase 1 (Tháng 1-2)  → Nền tảng kỹ thuật & Ổn định
Phase 2 (Tháng 2-3)  → AI Core & Editor Layer nâng cao  
Phase 3 (Tháng 3-5)  → Background Library & Text Engine
Phase 4 (Tháng 5-7)  → Sticker, Collage & Export
Phase 5 (Tháng 7-9)  → UX Polish & Onboarding
Phase 6 (Tháng 9-12) → Monetization, ASO & Growth
```

---

## Phase 1 — Nền tảng & Ổn định
📄 Chi tiết: [phase_1.md](./phase_1.md)

**Mục tiêu:** Dọn dẹp "nợ kỹ thuật", tối ưu hiệu năng, tránh crash  
**KPI:** App không crash với ảnh 12MP+, Undo/Redo hoạt động ổn định

- [x] Refactor `ImageEditActivity.kt` → Tách thành ViewModel + UseCase
- [x] Bitmap memory management (Disk cache, Downsampling)  
- [x] Tối ưu Undo/Redo Stack (giới hạn RAM, LRU cache)
- [x] Fix các edge case OOM khi layer nhiều

---

## Phase 2 — AI Core & Layer Engine nâng cao
📄 Chi tiết: [phase_2.md](./phase_2.md)

**Mục tiêu:** Tính năng xóa phông và quản lý layer đạt chuẩn đối thủ  
**KPI:** Người dùng có thể xóa phông & ghép ảnh phức tạp trong < 30 giây

- [ ] Manual Erase/Restore Brush (xóa phông thủ công)
- [x] Opacity Slider cho từng layer
- [x] Drag & Drop sắp xếp layer
- [ ] Layer Blend Modes (Multiply, Screen, Overlay)
- [x] Pinch-to-zoom chỉnh ảnh chính xác trong layer

---

## Phase 3 — Background Library & Text Engine
📄 Chi tiết: [phase_3.md](./phase_3.md)

**Mục tiêu:** Đủ nội dung cho người dùng tạo ảnh đẹp không cần tài nguyên ngoài  
**KPI:** Thư viện 100+ nền, hệ thống Text đủ mạnh để làm banner

- [ ] Background Library (Màu đặc, Gradient, Pattern, Ảnh Stock)
- [ ] Text Engine nâng cao: 50+ Font, Shadow, Stroke, Curve
- [ ] Background ảnh Stock (tích hợp Unsplash API)
- [ ] Color Picker toàn phần (HSV Wheel)

---

## Phase 4 — Sticker, Collage & HD Export
📄 Chi tiết: [phase_4.md](./phase_4.md)

**Mục tiêu:** Mở rộng sáng tạo, giữ chân người dùng lâu hơn  
**KPI:** Session time tăng 40% so với Phase 3

- [ ] Sticker Engine (Local + Cloud packs)
- [ ] Collage Maker (Multi-photo layout)
- [ ] Export Chất lượng cao: PNG/JPG 4K, chọn DPI
- [ ] Share trực tiếp lên mạng xã hội (Instagram, TikTok)

---

## Phase 5 — UX Polish & Onboarding
📄 Chi tiết: [phase_5.md](./phase_5.md)

**Mục tiêu:** Người dùng mới hiểu app trong 60 giây đầu tiên  
**KPI:** Retention Day-1 > 40%, Day-7 > 20%

- [ ] Interactive Onboarding (3 bước)
- [ ] Empty State đẹp và hướng dẫn hành động
- [ ] Haptic Feedback toàn bộ thao tác quan trọng
- [ ] Animation transitions mượt mà
- [ ] Dark Mode hoàn chỉnh

---

## Phase 6 — Monetization, ASO & Growth
📄 Chi tiết: [phase_6.md](./phase_6.md)

**Mục tiêu:** Tối ưu doanh thu và khả năng tìm kiếm trên Store  
**KPI:** ARPU (Average Revenue Per User) > $1.5/tháng

- [ ] Tối ưu Paywall (A/B Test 2 phiên bản)
- [ ] Rewarded Ads cho feature Pro
- [ ] ASO Optimization (Keyword, Screenshot, Video preview)
- [ ] In-app Review Prompt ở thời điểm vàng
- [ ] Referral Program

---

## 📈 Milestone đo lường

| Mốc | Tiêu chí đạt được |
|---|---|
| ✅ Sau Phase 1 | App chạy ổn định, không crash với ảnh 4K |
| ✅ Sau Phase 2 | Đạt 40% tính năng đối thủ |
| ✅ Sau Phase 3 | Đạt 55% tính năng đối thủ |
| ✅ Sau Phase 4 | Đạt 65% tính năng đối thủ |
| ✅ Sau Phase 5-6 | Đạt 70% + Sẵn sàng marketing |

---

> 💡 **Lưu ý:** Kế hoạch này được thiết kế để có thể phát triển song song nếu có thêm nhân sự.  
> Mỗi phase nên kết thúc bằng một Sprint Demo + Release lên Internal Testing track.

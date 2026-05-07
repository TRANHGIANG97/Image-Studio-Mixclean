# 💰 Phase 6 — Monetization, ASO & Growth
> **Thời gian:** Tháng 9 – Tháng 12  
> **Phụ thuộc:** Phase 5 hoàn thành (App đã polish, ổn định, rating tốt)  
> **Mục tiêu:** Thiết lập hệ thống doanh thu bền vững và kênh tăng trưởng hữu cơ

---

## 🎯 KPI Đầu ra

| Tiêu chí | Mục tiêu |
|---|---|
| Monthly Active Users (MAU) | 50,000+ |
| Conversion rate Free→Pro | > 3% |
| ARPU (Average Revenue Per User) | > $1.5/tháng |
| App Store Rating | > 4.6 ⭐ (500+ reviews) |
| Organic install từ ASO | > 40% tổng install |
| Churn rate tháng | < 5% |

---

## 6.1 Tối ưu Paywall & Subscription

### Phân tích Paywall hiện tại
Hiện tại paywall còn đơn giản (chỉ có 1 plan). Đối thủ InShot dùng:
- 3 tier pricing (Weekly/Monthly/Yearly)
- Free trial 3 ngày cho Yearly
- Highlight plan "Phổ biến nhất"

### Cấu trúc Pricing được đề xuất

```
┌─────────────────────────────────────┐
│           🌟 PRO ACCESS             │
│   Mở khóa tất cả tính năng premium │
├────────────┬────────────┬───────────┤
│   Weekly   │  Monthly   │  Yearly   │
│  180.000đ  │  94.000đ   │  262.000đ │
│   /tuần    │   /tháng   │   /năm    │
│            │ ✦ PHỔ BIẾN │           │
│            │ TIẾT KIỆM  │ THỬ 3 NGÀY│
│            │   25%      │  MIỄN PHÍ │
└────────────┴────────────┴───────────┘
[Bắt đầu dùng thử miễn phí 3 ngày]
```

### Paywall Trigger Points (khi nào hiện paywall)

| Hành động | Trigger Type |
|---|---|
| Dùng Manual Erase > 3 lần/ngày | Soft gate |
| Thêm sticker pack Pro | Hard gate |
| Export chất lượng HD/4K | Soft gate |
| Sử dụng font chữ Pro | Hard gate |
| Remove watermark | Hard gate |
| Xóa phông lần thứ 5 trong ngày | Rewarded Ad hoặc Upgrade |

### Công việc cụ thể
- [ ] **6.1.1** A/B Test 2 phiên bản paywall UI (`PaywallVariantA` & `PaywallVariantB`)
- [ ] **6.1.2** Tích hợp Google Play Billing Library v6
- [ ] **6.1.3** `PurchaseRepository` — xác thực subscription với server
- [ ] **6.1.4** Restore Purchase flow
- [ ] **6.1.5** Grace period (7 ngày) khi subscription lỗi thanh toán
- [ ] **6.1.6** Backend verification (Firebase Functions hoặc simple REST)

---

## 6.2 Rewarded Ads Strategy

### Nguyên tắc
Quảng cáo không nên làm người dùng tức giận. Dùng Rewarded Ads để người dùng **chủ động chọn** xem quảng cáo để mở khóa tính năng.

### Vị trí Rewarded Ads

| Hành động | Phần thưởng sau xem Ad |
|---|---|
| Xóa phông lần thứ 4/ngày | Mở khóa thêm 3 lần xóa phông |
| Export không watermark 1 lần | 1 lần export sạch |
| Thưởng sticker premium | 1 sticker pack free trong 24h |

### Banner Ads (Non-intrusive)
- **Chỉ hiện ở màn hình Home**, không hiện trong Editor
- **Xóa banner** ngay khi user nâng cấp Pro
- **Không hiện banner** trong 24h sau khi user mới cài

### Công việc cụ thể
- [ ] **6.2.1** Tích hợp AdMob SDK (Rewarded + Banner)
- [ ] **6.2.2** `AdManager` wrapper với frequency cap
- [ ] **6.2.3** Ẩn tất cả quảng cáo khi subscription active
- [ ] **6.2.4** Tracking: xem ad → convert Pro trong 7 ngày (measure effectiveness)

---

## 6.3 ASO — App Store Optimization

### Nghiên cứu từ khóa

Từ khóa tìm kiếm phổ biến ngách "Xóa phông":

| Từ khóa | Volume | Cạnh tranh |
|---|---|---|
| Xóa phông ảnh | Rất cao | Cao |
| Tách nền AI | Cao | Trung bình |
| Xóa nền ảnh miễn phí | Cao | Cao |
| Làm ảnh trong suốt | Trung bình | Thấp |
| Background remover | Cao (EN) | Cao |
| Photo background eraser | Cao (EN) | Cao |
| Remove bg app | Trung bình | Trung bình |
| Ảnh sản phẩm đẹp | Thấp | Rất thấp |

### Store Listing Optimization

**Icon**: Hình nổi bật, đơn giản nhận ra ngay — nên thể hiện "tách nền" (vật thể nổi trên nền checker hoặc gradient)

**Screenshots (cực kỳ quan trọng)**:
```
Screenshot 1: "Xóa phông chỉ 1 chạm" — Before/After ấn tượng
Screenshot 2: "50+ mẫu nền đẹp" — Grid preview các nền
Screenshot 3: "Thêm chữ & sticker chuyên nghiệp" — Ảnh thành phẩm
Screenshot 4: "Xuất ảnh 4K siêu nét" — Chất lượng HD
Screenshot 5: "Ghép ảnh cực dễ" — Collage demo
```

**Short Description (80 ký tự):**
```
"Xóa phông AI tức thì & chỉnh ảnh chuyên nghiệp chỉ với 1 chạm"
```

### Preview Video (15-30 giây)
Kịch bản:
```
0-3s: Ảnh chụp sản phẩm → tap 1 lần → phông biến mất
3-8s: Swipe qua 4-5 nền đẹp, chọn Aurora
8-14s: Thêm text tên shop, thêm sticker
14-20s: Chia sẻ lên Instagram
20-30s: Logo app + "Tải miễn phí"
```

### Công việc cụ thể
- [ ] **6.3.1** Thiết kế 5 screenshot theo template trên
- [ ] **6.3.2** Quay và edit video preview 30 giây
- [ ] **6.3.3** Viết lại Short/Long Description với từ khóa
- [ ] **6.3.4** Đăng ký tất cả từ khóa trong Google Play Console
- [ ] **6.3.5** Localize sang Tiếng Anh, Nhật, Hàn (thị trường có sức mua cao)

---

## 6.4 In-App Review & Rating Strategy

### Timing vàng để hỏi đánh giá

| Thời điểm | Lý do |
|---|---|
| Sau khi xóa phông thành công lần đầu | User vừa có WOW moment |
| Sau khi lưu ảnh lần thứ 5 | User đã quen và thấy giá trị |
| Sau khi share ảnh lên mạng xã hội | User đang vui và hài lòng |
| **KHÔNG hỏi** khi: Xóa phông thất bại | User đang frustrated |

### Flow hỏi đánh giá
```
"Bạn có thích ứng dụng không?"
       [❤️ Có!]  [Không hẳn]
           ↓              ↓
  [Đánh giá 5 sao]  [Góp ý để cải thiện]
     → Play Store      → Form Feedback
```

### Công việc cụ thể
- [ ] **6.4.1** Implement `ReviewManager` dùng `com.google.android.play:review`
- [ ] **6.4.2** Rate timing logic (chỉ hỏi 1 lần/30 ngày)
- [ ] **6.4.3** Feedback form gửi email khi user chọn "Không hẳn"
- [ ] **6.4.4** Track conversion rate của review prompt

---

## 6.5 Growth & Referral

### Viral Loop Strategy

```
User tạo ảnh đẹp
    ↓ Share lên mạng xã hội
    ↓ [Optional] Watermark nhỏ "Made with [AppName]"
Người xem tò mò → Tải app
    ↓ Người giới thiệu được thêm 5 lần xóa phông/ngày
    ↓ Người được giới thiệu nhận 7 ngày Pro trial
```

### Content Marketing

- **TikTok/Reels**: Series "Mẹo làm ảnh sản phẩm đẹp" — 2 video/tuần
- **YouTube Shorts**: Tutorial tách nền, ghép ảnh sản phẩm
- **Facebook Groups**: Tham gia group người bán hàng online, chia sẻ mẹo thực tế

### Công việc cụ thể
- [ ] **6.5.1** Implement Referral code system (Firebase Dynamic Links)
- [ ] **6.5.2** "Chia sẻ app" button trong Settings với tracking
- [ ] **6.5.3** Watermark opt-in/opt-out cho ảnh share
- [ ] **6.5.4** Lập kế hoạch content 3 tháng cho TikTok/Reels

---

## 6.6 Analytics & Data-Driven Decisions

### Events cần track

```kotlin
// Funnel chuyển đổi cực quan trọng
analytics.logEvent("image_selected", ...)
analytics.logEvent("bg_removal_started", ...)
analytics.logEvent("bg_removal_success", ...)
analytics.logEvent("bg_removal_failed", ...)
analytics.logEvent("background_applied", mapOf("style" to style.name))
analytics.logEvent("export_completed", mapOf("format" to "PNG", "quality" to "HD"))
analytics.logEvent("paywall_shown", mapOf("trigger" to "export_hd"))
analytics.logEvent("subscription_started", mapOf("plan" to "yearly"))
analytics.logEvent("subscription_cancelled", ...)
```

### Dashboard cần theo dõi hàng tuần
- Install → First session → Remove BG → Export → Share → Paywall → Subscribe
- Drop-off rate tại từng bước
- Feature usage breakdown (% user dùng sticker vs text vs background)

### Công việc cụ thể
- [ ] **6.6.1** Audit toàn bộ analytics events hiện có
- [ ] **6.6.2** Thêm conversion funnel events đầy đủ
- [ ] **6.6.3** Firebase Remote Config cho A/B testing giá và UI
- [ ] **6.6.4** Setup Looker Studio dashboard tự động

---

## 📅 Sprint breakdown

| Sprint | Tuần | Công việc |
|---|---|---|
| Sprint 1 | Tuần 1-3 | 6.1 Paywall tối ưu + Google Play Billing |
| Sprint 2 | Tuần 4-5 | 6.2 AdMob AdManager |
| Sprint 3 | Tuần 6-7 | 6.3 ASO (Screenshots, Video, Description) |
| Sprint 4 | Tuần 8 | 6.4 In-app Review |
| Sprint 5 | Tuần 9-10 | 6.5 Referral + Growth Marketing setup |
| Sprint 6 | Tuần 11-12 | 6.6 Analytics audit + Final QA + Store Release |

---

## 🏁 Đích đến sau Phase 6

Sau khi hoàn thành toàn bộ 6 phases, ứng dụng sẽ:

✅ **Kỹ thuật**: Kiến trúc sạch, không crash, chạy mượt trên mọi thiết bị Android từ 2019+  
✅ **Tính năng**: Đạt 70% tính năng của Background Eraser (InShot) — đủ để cạnh tranh trực tiếp  
✅ **UX**: Người dùng mới hiểu app trong 60 giây, cảm giác "premium"  
✅ **Doanh thu**: Hệ thống Subscription + Rewarded Ads + IAP hoạt động ổn định  
✅ **Tăng trưởng**: ASO đưa app lên top tìm kiếm, Referral tạo vòng lặp tăng trưởng hữu cơ  

---

> ⬅️ [Phase 5](./phase_5.md) | 🏠 [Quay về Main Plan](./main_plan.md)

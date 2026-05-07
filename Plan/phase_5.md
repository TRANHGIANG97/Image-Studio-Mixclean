# ✨ Phase 5 — UX Polish & Onboarding
> **Thời gian:** Tháng 7 – Tháng 9  
> **Phụ thuộc:** Phase 4 hoàn thành (tất cả tính năng core đã có)  
> **Mục tiêu:** Biến app "chạy được" thành app "người dùng yêu thích" — cảm giác premium, mượt mà

---

## 🎯 KPI Đầu ra

| Tiêu chí | Mục tiêu |
|---|---|
| Day-1 Retention | > 40% |
| Day-7 Retention | > 20% |
| Onboarding completion rate | > 70% |
| App Store Rating | > 4.5 ⭐ |
| Crash-free sessions | > 99.5% |
| App startup time (cold) | < 1.5 giây |

---

## 5.1 Interactive Onboarding (3 bước)

### Nguyên tắc thiết kế
> "Show, don't tell" — Đừng giải thích tính năng bằng chữ, hãy cho người dùng tự làm thử.

### Màn hình Onboarding

```
Màn 1: "Xóa phông chỉ 1 chạm"
───────────────────────────────────
[Ảnh demo với background giả tạo]
    ↓ Tap nút "Xóa phông"
[Animation: background biến mất, còn lại vật thể]
"Thử ngay với ảnh của bạn →"

Màn 2: "Chọn nền đẹp bất kỳ"  
───────────────────────────────────
[Vật thể vừa cắt, swipe qua các nền]
[Aurora, Sunset, Neon Grid... cuộn infinite]
"Vuốt để đổi nền →"

Màn 3: "Thêm chữ, sticker & chia sẻ"
───────────────────────────────────
[Ảnh hoàn chỉnh với text + sticker]
"Lưu ảnh và chia sẻ ngay →"
[Nút: Bắt đầu tạo tác phẩm đầu tiên!]
```

### Công việc cụ thể
- [ ] **5.1.1** Thiết kế 3 màn hình onboarding với animation Lottie
- [ ] **5.1.2** Demo video ngắn (GIF/Lottie) cho từng bước
- [ ] **5.1.3** "Bỏ qua" ở góc trên phải — nhưng tracking xem ai bỏ qua bước nào
- [ ] **5.1.4** Lưu `onboardingCompleted` vào SharedPreferences, không hiện lại

---

## 5.2 Empty States & Error States

### Các trạng thái cần thiết kế

| Màn hình | Empty State | Error State |
|---|---|---|
| Home Screen | "Chọn ảnh để bắt đầu" + icon | "Không thể tải ảnh" + retry |
| Background Library | "Đang tải..." skeleton | "Không có mạng" icon |
| Sticker Panel | "Đang tải sticker..." | "Thử lại" button |
| Layer Panel | "Chưa có lớp nào" | N/A |
| Gallery | "Chưa có ảnh đã lưu" | N/A |

### Thiết kế
- Mỗi empty state cần: Icon minh hoạ (Lottie animation), Tiêu đề ngắn gọn, Mô tả hành động, Nút CTA
- Dùng tone màu nhẹ nhàng, không gây anxiety

### Công việc cụ thể
- [ ] **5.2.1** Thiết kế 8 Lottie animation cho empty/error states
- [ ] **5.2.2** Implement `EmptyStateView` custom component tái sử dụng
- [ ] **5.2.3** Error handling toàn diện với retry action

---

## 5.3 Micro-Animations & Haptic Feedback

### Danh sách Haptic cần thêm

| Sự kiện | Loại haptic |
|---|---|
| Nhấn nút xóa phông | `HapticFeedbackConstants.CONFIRM` |
| Xóa phông thành công | `HapticFeedbackConstants.CONFIRM` (khác nhịp) |
| Xóa phông thất bại | `HapticFeedbackConstants.REJECT` |
| Layer snap vào vị trí | `HapticFeedbackConstants.CLOCK_TICK` |
| Dùng eraser brush | Rung nhẹ liên tục theo áp lực |
| Long press | `HapticFeedbackConstants.LONG_PRESS` |
| Kéo thả layer thành công | `HapticFeedbackConstants.CONFIRM` |

### Danh sách Micro-animation

| UI Element | Animation |
|---|---|
| Nút "Xóa phông" | Shimmer loading → scale bounce khi xong |
| Thêm sticker | Scale từ 0 lên 1 với spring animation |
| Xóa layer | Fade out + slide out |
| Chọn nền mới | Cross-fade preview |
| Tool icon active | Background pulse |
| Layer panel show/hide | Slide + fade |
| Progress bar | Smooth easing |

### Công việc cụ thể
- [ ] **5.3.1** Tích hợp `VibrationEffect` (API 26+) / `HapticFeedback` cho tất cả điểm trên
- [ ] **5.3.2** Thay thế tất cả `visibility = VISIBLE/GONE` bằng animation wrapper
- [ ] **5.3.3** Spring animation cho sticker thêm mới
- [ ] **5.3.4** Shimmer loading cho ảnh Stock và Font loading

---

## 5.4 Dark Mode Hoàn Chỉnh

### Hiện trạng
Dark Mode đang có nhưng chưa nhất quán — một số màu hardcode `#FFFFFF` trực tiếp.

### Checklist
- [ ] **5.4.1** Audit toàn bộ màu hardcode → chuyển sang `@color` resource với night qualifier
- [ ] **5.4.2** Kiểm tra tất cả icon có phiên bản `night/` chưa
- [ ] **5.4.3** Canvas background phải phân biệt rõ ảnh và viền trong cả 2 mode
- [ ] **5.4.4** Test manual trên 5 thiết bị khác nhau

---

## 5.5 Performance UX

### "Perceived Performance" — Cảm giác nhanh quan trọng hơn nhanh thực sự

- [ ] **5.5.1** **Skeleton Loading**: Khi load ảnh Gallery, hiện placeholder skeleton trước
- [ ] **5.5.2** **Instant Preview**: Apply background preset phải hiện preview ngay < 100ms (blend mode trick, không composite thật)
- [ ] **5.5.3** **Background Processing**: Tất cả AI processing chạy trên background thread, UI không bao giờ bị block
- [ ] **5.5.4** **Startup Optimization**: Deferr non-critical init (Ads, Analytics) sang after first frame
- [ ] **5.5.5** **Image Loading**: Dùng Coil với crossfade animation cho tất cả ảnh

---

## 5.6 Accessibility

- [ ] **5.6.1** Content descriptions cho tất cả icon button
- [ ] **5.6.2** Touch target tối thiểu 48x48dp
- [ ] **5.6.3** Font size scale tốt với Setting > Font Size của hệ thống
- [ ] **5.6.4** Contrast ratio đạt WCAG AA (4.5:1)

---

## 📅 Sprint breakdown

| Sprint | Tuần | Công việc |
|---|---|---|
| Sprint 1 | Tuần 1-2 | 5.1 Onboarding + 5.2 Empty States |
| Sprint 2 | Tuần 3-4 | 5.3 Micro-animations + Haptic |
| Sprint 3 | Tuần 5-6 | 5.4 Dark Mode + 5.5 Performance UX |
| Sprint 4 | Tuần 7-8 | 5.6 Accessibility + Toàn bộ UX QA |

---

> ⬅️ [Phase 4](./phase_4.md) | ➡️ [Phase 6 — Monetization, ASO & Growth](./phase_6.md)

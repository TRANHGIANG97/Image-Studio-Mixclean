# 🚀 Phase 2 — AI Core & Layer Engine nâng cao
> **Mục tiêu:** Nâng cấp trình xóa phông đạt chuẩn chuyên nghiệp và xây dựng hệ thống quản lý nhiều lớp (Multi-layer) mạnh mẽ.

---

## 🏗️ Cấu trúc thực hiện (Part-based Execution)

Để đảm bảo độ chính xác tuyệt đối, Phase 2 sẽ được chia thành 4 Phần (Parts). Mỗi lượt thực hiện sẽ chỉ tập trung vào 1 Phần.

### Part 1: Layer Engine Refinement (Nâng cấp Bộ máy Layer)
*   **Mục tiêu:** Quản lý thứ tự hiển thị (Z-index), độ mờ (Opacity) và biến đổi layer.
*   **Công việc:**
    - [x] Nâng cấp `EditorLayer` hỗ trợ `opacity`, `scale`, `rotation`.
    - [x] Implement logic di chuyển layer lên/xuống (Bring to Front/Send to Back).
    - [x] Tối ưu hóa việc Render layer chồng lên nhau mà không gây lag (RAM Caching).

### Part 2: AI Background Removal V2 (Xóa phông chuẩn Pro)
*   **Mục tiêu:** Cạnh ảnh mịn màng, không còn răng cưa.
*   **Công việc:**
    - [x] Tích hợp thuật toán xử lý biên (Edge smoothing) sau khi cắt ảnh.
    - [x] Implement AI Auto-refinement (Tự động tinh chỉnh mặt nạ).
    - [x] Hỗ trợ xuất ảnh PNG High-Res với độ trong suốt hoàn hảo.

### Part 3: AI Smart Tools (Công cụ AI nâng cao)
*   **Mục tiêu:** Thêm các tính năng "Wow" cho người dùng.
*   **Công việc:**
    - [ ] AI Object Removal (Xóa vật thể thừa bằng Inpainting).
    - [ ] AI Sky Replacement (Thay thế bầu trời thông minh).
    - [ ] Tích hợp thư viện xử lý filter màu nghệ thuật.

### Part 4: Professional UI/UX Enhancement
*   **Mục tiêu:** Giao diện chuyên nghiệp như Canva/InShot.
*   **Công việc:**
    - [ ] Hoàn thiện Layer Panel với tính năng kéo thả thứ tự.
    - [ ] Implement thanh trượt tương tác (Interactive Slider) cho từng thuộc tính layer.
    - [ ] Tối ưu hóa cảm ứng đa điểm (Multi-touch) cho việc zoom/xoay layer.

---

## 📅 Lịch trình thực hiện

1.  **Tuần 1-2:** Thực hiện **Part 1**. Sau đó QA & Fix bug.
2.  **Tuần 3-4:** Thực hiện **Part 2**. Sau đó QA & Fix bug.
3.  **Tuần 5-6:** Thực hiện **Part 3**.
4.  **Tuần 7-8:** Thực hiện **Part 4**.

---
> [!IMPORTANT]
> Việc thực hiện theo từng Part giúp kiểm soát Memory của AI và tránh các lỗi Logic phức tạp. Mỗi Part sẽ có một file hướng dẫn chi tiết riêng khi bắt đầu.

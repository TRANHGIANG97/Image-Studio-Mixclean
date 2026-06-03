# Kế Hoạch Mới Cho admin_tools

## Mục tiêu
- Làm cho `admin_tools` độc lập hơn, ít phụ thuộc chéo.
- Cải thiện chức năng quản trị để dễ dùng và ít lỗi hơn.
- Giữ phạm vi thay đổi gọn, không đụng vào luồng home/editor nếu chưa cần.

## Phạm vi ưu tiên
1. Rà soát luồng thao tác chính trong admin_tools.
2. Chuẩn hóa component dùng chung trong module.
3. Đồng bộ text, state, và xử lý lỗi hiển thị.
4. Tách helper nội bộ nếu còn phụ thuộc module khác.
5. Tăng khả năng kiểm thử bằng cấu trúc màn hình rõ ràng hơn.

## Hướng cải tiến chức năng
- Làm rõ luồng mở màn hình, chọn mục, sửa và lưu.
- Thêm trạng thái loading/empty/error nhất quán.
- Tách các action nguy hiểm hoặc ít dùng vào menu phụ.
- Chuẩn hóa form/input để giảm lỗi nhập liệu.
- Nếu có danh sách dữ liệu, thêm lọc/tìm kiếm/sắp xếp cơ bản.

## Kế hoạch triển khai
### Giai đoạn 1: Audit
- Liệt kê toàn bộ file trong `admin_tools`.
- Chốt màn hình chính, component dùng chung, helper và string.
- Tìm phụ thuộc sang module khác.

### Giai đoạn 2: Chỉnh cấu trúc
- Tách các phần có trách nhiệm lẫn nhau.
- Gom token/style/local string vào đúng chỗ.
- Đưa state phức tạp ra ViewModel hoặc helper nhỏ.

### Giai đoạn 3: Cải thiện chức năng
- Sửa các luồng chính đang thiếu rõ ràng.
- Thêm validate và phản hồi lỗi.
- Chuẩn hóa empty-state, loading-state, success-state.

### Giai đoạn 4: Polish
- Đồng bộ spacing, typography, radius, icon.
- Dọn text hardcode hoặc text lỗi mã hóa.
- Tối ưu khả năng thao tác trên mobile.

## Tiêu chí đánh giá
- Không còn phụ thuộc chéo không cần thiết.
- Luồng chính dễ hiểu hơn, ít thao tác thừa hơn.
- Không có text lỗi mã hóa hoặc label khó đọc.
- Màn hình quản trị chạy ổn trên kích thước nhỏ.

## Quy tắc làm việc
- Không build nếu chưa được user cho phép.
- Không sửa chéo sang module khác nếu chưa có chuyển giao rõ.
- Nếu cần thay đổi file ngoài `admin_tools`, phải báo trước.

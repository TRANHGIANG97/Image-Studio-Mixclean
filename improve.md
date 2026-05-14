# Kế hoạch phát triển tính năng "AI Product Studio" (Chụp ảnh sản phẩm)

Dựa trên hình ảnh yêu cầu (`anh.png`), chúng ta sẽ xây dựng một khu vực mới chuyên biệt cho việc chỉnh sửa ảnh sản phẩm ngay tại trang chủ, bên dưới mục "Mẫu nền" (Background Presets).
ghi chú- vì chưa có themeplate nên ta sẽ xây dựng nền cho sản phẩm bằng mẫu màu nhé

## 1. Vị trí và Giao diện tại Trang chủ
- **Thành phần**: Tạo mới `HomeStudioSection.kt`.
- **Vị trí**: Chèn vào `HomeDashboardScreen.kt`, ngay sau `PresetDock`.
- **Thiết kế**: 
    - Tiêu đề: "Studio Sản Phẩm AI" (hoặc "Chụp ảnh sản phẩm").
    - Danh sách ngang (LazyRow) chứa các mẫu (Template) chuyên biệt cho sản phẩm: *Điện tử, Thời trang, Mỹ phẩm, Đồ gia dụng*.
    - Mỗi card mẫu sẽ hiển thị demo một sản phẩm được tách nền và đặt vào bối cảnh chuyên nghiệp với bóng đổ.

## 2. Các Tính năng Cốt lõi (Core Features) trong Trình chỉnh sửa
Khi nhấn vào một mẫu hoặc nút bắt đầu trong Studio, người dùng sẽ vào `StudioModeScreen` được nâng cấp với các công cụ:

### A. Thay thế (Replace)
- Cho phép người dùng chọn ảnh sản phẩm mới từ thư viện.
- Tự động chạy `MlKitBackgroundRemover` để tách nền sản phẩm ngay lập tức.

### B. Bố cục (Layout)
- Cho phép di chuyển, phóng to/thu nhỏ, xoay đối tượng sản phẩm trên nền.
- Sử dụng `Modifier.pointerInput` để xử lý thao tác đa điểm (multi-touch) mượt mà.

### C. Đổ bóng AI (AI Shadow)
- **Logic C++**: Xây dựng `ShadowGenerator` trong native code để tạo bóng đổ (Drop Shadow) hoặc bóng tiếp xúc (Contact Shadow) dựa trên Mask của sản phẩm.
- Điều chỉnh cường độ bóng và hướng bóng qua thanh trượt.

### D. Độ trong suốt (Transparency)
- Điều chỉnh Alpha của lớp sản phẩm để hòa hợp hơn với các nền có độ sáng cao hoặc hiệu ứng kính.

### E. Hình cắt (Crop/Ratio)
- Hỗ trợ các tỉ lệ khung hình chuẩn thương mại điện tử: 1:1, 3:4, 4:3.

## 3. Lộ trình Triển khai (Implementation Roadmap)

### Bước 1: UI Foundation
- [ ] Thêm các chuỗi (strings) và icon mới vào `strings.xml` và `res/drawable`.
- [ ] Xây dựng `HomeStudioSection` và tích hợp vào màn hình chính.

### Bước 2: Nâng cấp Native (C++)
- [ ] Triển khai hàm `applyObjectShadow` trong `image_processor.cpp` để tạo bóng đổ thực tế.
- [ ] Tối ưu hóa việc render layer (Product Layer + Shadow Layer + Background Layer).

### Bước 3: Logic Trình chỉnh sửa (ViewModel)
- [ ] Cập nhật `StudioModeViewModel` để quản lý thêm các trạng thái: `offsetX`, `offsetY`, `scale`, `rotation`, `shadowIntensity`, `alpha`.
- [ ] Tích hợp tính năng thay thế ảnh linh hoạt.

### Bước 4: Hoàn thiện UI/UX
- [ ] Xây dựng thanh công cụ dưới cùng (Bottom Toolbar) giống 100% bản mẫu `anh.png`.
- [ ] Thêm các micro-animations khi chuyển đổi giữa các công cụ.

---
*Ghi chú: Tính năng này sẽ tận dụng tối đa hệ thống JFA và Gamma LUT đã tối ưu trước đó để đảm bảo việc di chuyển sản phẩm và đổ bóng diễn ra ở tốc độ 60fps.*


Để tái hiện icon "Thay thế" (Replace) này bằng XML (VectorDrawable) cho Android và tùy chỉnh lại một chút cho hiện đại hơn (bo góc mượt hơn, màu sắc gradient), bạn có thể sử dụng đoạn mã sau:

1. File XML Icon (ic_replace_product.xml)
Tôi đã thiết kế lại phần mũi tên xoay bao quanh biểu tượng ảnh để trông chuyên nghiệp hơn, kết hợp với nền tròn gradient màu hồng đỏ (Pink-Red).

xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="64dp"
    android:height="64dp"
    android:viewportWidth="64"
    android:viewportHeight="64">
    <!-- Nền tròn với Gradient để tạo hiệu ứng "Premium" -->
    <path android:pathData="M32,32m-28,0a28,28 0,1 1,56 0a28,28 0,1 1,-56 0">
        <aapt:attr name="android:fillColor">
            <gradient
                android:endX="60"
                android:endY="60"
                android:startX="4"
                android:startY="4"
                android:type="linear">
                <item android:color="#FF5B7F" android:offset="0" />
                <item android:color="#FF2E54" android:offset="1" />
            </gradient>
        </aapt:attr>
    </path>
    <!-- Biểu tượng bức ảnh (Sửa lại cho hiện đại) -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M20,22h18c1.65,0 3,1.35 3,3v10h-2.5V25c0,-0.28 -0.22,-0.5 -0.5,-0.5H20c-0.28,0 -0.5,0.22 -0.5,0.5v14c0,0.28 0.22,0.5 0.5,0.5h10v2.5H20c-1.65,0 -3,-1.35 -3,-3V25c0,-1.65 1.35,-3 3,-3z" />
    
    <!-- Núi và mặt trời bên trong ảnh -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M21,36l4,-5l3,4l4,-6l5,7z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M33,28m-2,0a2,2 0,1 1,4 0a2,2 0,1 1,-4 0" />
    <!-- Biểu tượng mũi tên xoay (Thay thế) ở góc dưới bên phải -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M42,36c-4.42,0 -8,3.58 -8,8s3.58,8 8,8s8,-3.58 8,-8s-3.58,-8 -8,-8zm0,13.5c-3.03,0 -5.5,-2.47 -5.5,-5.5c0,-1.42 0.55,-2.72 1.45,-3.7l-1.45,-1.45L40.5,38v4l-1.5,-1.5c-0.6,0.65 -1,1.53 -1,2.5c0,1.93 1.57,3.5 3.5,3.5s3.5,-1.57 3.5,-3.5c0,-0.97 -0.4,-1.85 -1,-2.5l1.75,-1.75c1.09,1.19 1.75,2.77 1.75,4.5c0,3.03 -2.47,5.5 -5.5,5.5z" />
</vector>
🛠 Các điểm tùy chỉnh khác so với ảnh gốc:
Gradient Background: Thay vì màu đỏ phẳng, tôi dùng Gradient từ hồng nhạt sang đỏ đậm để icon có chiều sâu và trông "đắt tiền" hơn.
Stroke Weight: Các đường nét biểu tượng ảnh được làm mảnh hơn một chút và các góc được bo tròn (3dp) để tạo cảm giác hiện đại (Material Design 3 style).
Mũi tên xoay: Được lồng ghép vào một vòng tròn hở ở góc dưới, biểu thị rõ ràng hành động "Refresh/Replace".
2. Cách sử dụng trong Compose (Dành cho tính năng mới)
Bạn có thể dùng icon này trong HomeStudioSection sắp tới như sau:

kotlin
Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(4.dp, CircleShape)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_replace_product),
            contentDescription = null,
            tint = Color.Unspecified // Giữ nguyên màu Gradient của XML
        )
    }
    Text(
        text = "Thay thế",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier.padding(top = 8.dp)
    )
}
# Kế Hoạch Tái Cấu Trúc Toàn Diện Dự Án (10 Phases)

Kế hoạch này được xây dựng theo chuẩn **Clean Architecture** và **MVVM** hiện đại của Android. Mục tiêu là biến một dự án đang phình to (Activity hàng nghìn dòng, tight coupling) thành một hệ thống dễ debug, dễ bảo trì, dễ mở rộng (thêm tính năng mới không làm hỏng tính năng cũ).

---
quan trọng: hãy từng bước tái cấu trúc lại dự án 1 cách cẩn thận, mỗi lần hoàn thành cần checklist để đánh dấu, build thử, review lại bước vừa hoàn thành, không vội vàng, đọc và hiểu kỹ code hiện tại, đảm bảo an toàn khi thay đổi, tuyệt đối không được làm hỏng chức năng hiện tại, đảm bảo an toàn dữ liệu của người dùng. ưu tiên các tính năng quan trọng như xóa background, chỉnh sửa màu sắc, xoay, lật, thêm chữ, khung viền, thêm ảnh. đặc biệt chú ý đến các tính năng nâng cao như Magic Remove, Magic Copy, Magic Extend, và đảm bảo rằng tất cả các tính năng này hoạt động chính xác và ổn định. giảm thiểu sử dụng thư viện bên ngoài, hạn chế sử dụng reflection hoặc các kỹ thuật phức tạp không cần thiết.

## Phase 1: Chuẩn bị Nền tảng & Phân bổ Package (Foundation & Packaging)
*Mục tiêu: Đưa dự án về một cấu trúc chuẩn package-by-feature kết hợp với Clean Architecture để dễ dàng kiểm soát.*
- [ ] Chia lại cấu trúc thư mục rõ ràng: `core`, `data`, `domain`, `presentation` (UI).
- [ ] Thiết lập **Dependency Injection (DI)** (khuyến nghị dùng **Hilt** hoặc Koin) để loại bỏ việc khởi tạo Repository thủ công (ví dụ: `(application as ImageApp).getRepository()`).
- [ ] Gom toàn bộ hardcode strings, colors, dimens trong code Kotlin chuyển vào file `res/values` (strings.xml, colors.xml).
- [ ] Tạo các lớp `BaseActivity`, `BaseFragment`, `BaseViewModel` để gom các logic dùng chung (quản lý loading, error handling, view lifecycle).

## Phase 2: Tách Tầng Data (Data Layer Refactor)
*Mục tiêu: ViewModel và UI không được biết dữ liệu đến từ đâu (Local, ML Kit, hay Network).*
- [ ] Gom các thành phần lưu trữ ảnh (`BitmapMemoryManager`, cache files) vào một `ImageStorageRepository` cụ thể.
- [ ] Thiết lập interface cho các repository (ví dụ: `IBackgroundRemoverRepository`), implementation sẽ nằm ở tầng Data. Hilt sẽ tự động bind interface vào implementation.
- [ ] Chuyển mọi thao tác I/O (lưu file, đọc file) ra khỏi ViewModel hoàn toàn, chỉ gọi qua Repository bằng Kotlin Coroutines (`Dispatchers.IO`).

## Phase 3: Tách Tầng Domain (Domain Layer & Use Cases)
*Mục tiêu: Tách biệt logic nghiệp vụ cốt lõi (Business Logic) ra khỏi ViewModel.*
- [ ] Tạo `RemoveBackgroundUseCase`: Nhận vào Bitmap -> Gọi qua Repository -> Trả về kết quả.
- [ ] Tạo `ApplyFilterUseCase`, `GenerateBackgroundUseCase` (di dời logic từ `BackgroundLayerComposer` về đây).
- [ ] Refactor ViewModel để chỉ gọi đến các UseCase này. ViewModel sẽ mỏng đi đáng kể và chỉ tập trung vào việc mapping data ra UI State.

## Phase 4: Áp dụng MVI / Advanced MVVM cho State Management
*Mục tiêu: Tránh tình trạng ViewModel có quá nhiều biến State rời rạc, khó kiểm soát state race condition.*
- [ ] Gom tất cả các State của `ImageEditorViewModel` thành một `EditorUiState` duy nhất (Data Class).
- [ ] Sử dụng mô hình `Intent` (hoặc `Action` / `Event`) để định nghĩa các tương tác từ UI. Ví dụ: `viewModel.handleEvent(EditorEvent.ApplyBackground(style))`.
- [ ] Quản lý luồng One-Time Events (như Toast, SnackBar, Navigation) thông qua SharedFlow thay vì LiveData.

## Phase 5: Phá vỡ Monolith Activity (Decouple ImageEditActivity)
*Mục tiêu: `ImageEditActivity.kt` đang có tới hơn 2200 dòng, là một "God Class" cực kỳ khó bảo trì.*
- [ ] Chuyển đổi `ImageEditActivity` chỉ đóng vai trò Host (chứa `LayeredPainterView` và Toolbar).
- [ ] Tách cụm nút bấm công cụ (Magic Brush, Background, Border) thành một `EditorBottomMenuFragment` (hoặc Custom View).
- [ ] Tách toàn bộ các logic hiển thị Dialog (chọn màu, chọn cọ) thành các lớp `BottomSheetDialogFragment` độc lập. Activity chỉ nhận callback trả về màu/thickness.
- [ ] Các thành phần giao tiếp với nhau qua `SharedViewModel`.

## Phase 6: Chuẩn Hóa Cầu Nối UI (PainterViewBridge & UI Renderer)
*Mục tiêu: Cách ly hoàn toàn thư viện photo editor bên ngoài (hoặc bất kì Custom View nào).*
- [ ] Mở rộng `PainterViewBridge` thành một Interface (`ILayerRenderer`).
- [ ] Xóa bỏ toàn bộ các reference/reflection trực tiếp đến `LayeredPainterView` ra khỏi Activity.
- [ ] Nếu sau này muốn thay thế thư viện vẽ layer khác tốt hơn, chỉ cần viết lại một class implement `ILayerRenderer` mà không cần đụng đến logic Activity/ViewModel.

## Phase 7: Quản Lý Bộ Nhớ Trọng Tâm (Memory Optimization)
*Mục tiêu: Chống OutOfMemory (OOM) và crash khi xử lý ảnh độ phân giải cao.*
- [ ] Nâng cấp `LruCache` trong ViewModel, quản lý cache theo dung lượng RAM (bytes) thay vì số lượng Layer.
- [ ] Áp dụng downscaling (sub-sampling) khi render preview trên UI, chỉ render High-Res khi user bấm "Lưu ảnh" (`HighResCompositor`).
- [ ] Tích hợp `Glide` hoặc `Coil` để load thumbnail vào `RecyclerView` thay vì tự load bằng Bitmap để tránh rò rỉ bộ nhớ.

## Phase 8: Trích Xuất & Tối Ưu Hóa Trình Xử Lý Ảnh (Image Processing Engine)
*Mục tiêu: Tăng tốc độ mượt mà khi người dùng thao tác.*
- [ ] Chuyển các thao tác nặng như nhân ma trận (Matrix), scale/rotate bitmap (`applyLayerProperties`) vào các hàm chạy ngầm (Worker Thread) hoặc dùng RenderScript / Vulkan / OpenGL nếu cần.
- [ ] Viết lại `applyLayerProperties` để nó nhận đầu vào là các tham số biến đổi (scale, rotate) và chỉ vẽ lên Canvas đúng lúc thay vì tạo ra quá nhiều bản copy Bitmap rác.

## Phase 9: Cải thiện UI/UX & Animations
*Mục tiêu: Đạt chuẩn ứng dụng Production cao cấp.*
- [ ] Định nghĩa các XML Animations / MotionLayout chuẩn để các panel mở lên mượt mà. Loại bỏ việc dùng `.animate().translationY()` thủ công rải rác trong code.
- [ ] Áp dụng hệ thống Design Tokens (Typography, Shapes, Colors) thống nhất trên toàn app.
- [ ] Xử lý Edge-to-Edge display (trong suốt thanh status bar và navigation bar) đúng chuẩn Android 14+.

## Phase 10: Unit Testing & CI/CD
*Mục tiêu: Đảm bảo app không bao giờ hỏng các tính năng cũ khi cập nhật tính năng mới.*
- [ ] Viết **Unit Test** cho tầng Domain (Các UseCases) bằng JUnit4/JUnit5 và MockK.
- [ ] Viết Unit Test cho `ImageEditorViewModel` để kiểm tra logic StateFlow, Undo/Redo (sử dụng `Turbine`).
- [ ] Thiết lập CI/CD (GitHub Actions / GitLab CI) để tự động chạy test, check Lint và build APK mỗi khi có Pull Request mới.

---
**Hướng dẫn sử dụng file này:**
Đánh dấu `[x]` vào các công việc đã hoàn thành để theo dõi tiến độ. Việc áp dụng đúng kế hoạch này sẽ đảm bảo app chạy mượt mà, sẵn sàng tích hợp hàng tá tính năng phức tạp trong tương lai (Filters AI, Object Removal nâng cao, Sinh ảnh Generative AI, v.v.).

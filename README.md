
-yêu cầu tiên quyết: kiểm tra cẩn thận mã nguồn" lập kế hoạch cẩn thật từng bước, build và sửa lỗi đẩm bảo phải build pass sau mỗi lần thực hiện các bước rồi mới bắt đầu bước tiếp theo:

Dưới đây là kế hoạch triển khai chi tiết cho từng bước, kèm theo chỉ dẫn cụ thể về những file cần sửa, logic cần thay đổi và mã nguồn tham khảo. Tôi sẽ trình bày tuần tự 7 bước để bạn (hoặc một AI code) có thể thực hiện mà không bỏ sót.

Bước 1: Loại bỏ quyền lưu trữ không cần thiết
Mục tiêu: Xoá hoàn toàn READ_EXTERNAL_STORAGE và WRITE_EXTERNAL_STORAGE. Ứng dụng sẽ chỉ dùng Photo Picker (tương thích ngược qua activity‑compose), FileProvider cho camera và MediaStore để lưu ảnh.

Các file cần thay đổi
File	Hành động
PermissionUtils.kt	Sửa getInternalStoragePermissions() trả về mảng rỗng. Xoá enum PermissionTypes nếu không còn dùng ở đâu khác.
MainScreen.kt	Xoá toàn bộ logic liên quan đến MainScreenViewModel, dialogQueue, storagePermissionLauncher, appSettingsLauncher. Chỉ cần MainScreenButtonsLayout. Bỏ permissionsGranted khỏi MainScreenLayout.
MainScreenLayout.kt	Xoá tham số permissionsGranted, appSettingsLauncher. Xoá MainScreenDefaultView. Luôn hiển thị MainScreenButtonsLayout. Xoá PermissionDeniedView.
MainScreenViewModel.kt	Có thể xoá hẳn file hoặc giữ lại nhưng xoá hết logic permission.
PermissionDialog.kt	Xoá file nếu không dùng cho permission nào khác.
StoragePermissionTextProvider	Xoá.
PermissionDeniedView.kt	Xoá.
AndroidManifest.xml	Xoá <uses‑permission android:name="android.permission.READ_EXTERNAL_STORAGE"/> và WRITE_EXTERNAL_STORAGE.
Mã tham khảo sau thay đổi
MainScreen.kt (rút gọn):

kotlin
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onImageSelected: (Bitmap) -> Unit,
    onRemoveBgSelected: (Bitmap) -> Unit = {},
    initialImageUri: Uri? = null,
) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isRemoveBgMode by remember { mutableStateOf(false) }
    var scaledBitmapStatus by remember { mutableStateOf<BitmapStatus>(BitmapStatus.None) }

    val cameraImageUri = remember {
        val imgFile = File(context.filesDir, "camera_photo.jpg")
        FileUtils.getUriForFile(context, imgFile)
    }

    // … (giữ nguyên LaunchedEffect xử lý bitmap)

    MainScreenLayout(
        modifier = modifier,
        scaledBitmapStatus = scaledBitmapStatus,
        cameraImageUri = cameraImageUri,
        onPhotoPicked = { isRemoveBgMode = false; imageUri = it },
        onPhotoCaptured = { imageUri = it },
        onRemoveBgPhotoPicked = { isRemoveBgMode = true; imageUri = it },
        onImageSelected = {}
    )
    // Không còn dialog permission nữa
}
MainScreenLayout.kt – xoá MainScreenDefaultView, giữ nguyên phần còn lại, bỏ permissionsGranted parameter.

PermissionUtils.kt – sửa thành:

kotlin
fun getInternalStoragePermissions(): Array<String> = emptyArray()
// Xoá enum PermissionTypes nếu không còn tham chiếu.
Sau bước này, ứng dụng sẽ không còn hiển thị dialog xin quyền lưu trữ, và vẫn hoạt động tốt trên mọi phiên bản Android nhờ Photo Picker backport.

Bước 2: Cải thiện quản lý bộ nhớ và tránh OOM
Mục tiêu: Giảm rủi ro OutOfMemoryError bằng cách dùng cache đĩa cho lịch sử bitmap và giới hạn kích thước xử lý.

2.1. MagicBrushViewModel dùng BitmapCache
Hiện tại MagicBrushViewModel có undoStack: Stack<Bitmap> và redoStack: Stack<Bitmap>.
Sửa thành: dùng BitmapCache (như SharedEditorViewModel) lưu trữ snapshot dưới dạng file PNG trong cacheDir.

Inject @ApplicationContext private val context: Context vào constructor của MagicBrushViewModel.

Tạo instance BitmapCache bên trong ViewModel.

Khi applyMagicErase, applyBlurResult, onMagicErase: snapshot hiện tại được lưu vào cache thay vì đẩy thẳng bitmap vào stack. Các hàm undo(), redo() sẽ load từ cache.

Ví dụ code thay đổi trong MagicBrushViewModel:

kotlin
@HiltViewModel
class MagicBrushViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val bitmapCache = BitmapCache(context)
    // Các stack hiện tại đổi thành Stack<String> (cache ID)
    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()

    private fun pushUndo(bitmap: Bitmap) {
        val id = bitmapCache.saveBitmap(bitmap)
        undoStack.push(id)
    }

    private fun popUndo(): Bitmap? {
        if (undoStack.empty()) return null
        val id = undoStack.pop()
        return bitmapCache.loadBitmap(id)
    }

    // Xoá stack redo cũng cần xoá file cache tương ứng
    private fun clearRedoStack() {
        while (redoStack.isNotEmpty()) {
            val id = redoStack.pop()
            bitmapCache.deleteBitmap(id)
        }
    }
}
Áp dụng tương tự cho bất kỳ ViewModel nào còn lưu Bitmap trực tiếp (kiểm tra StudioModeViewModel, BackgroundModeViewModel nếu có).

2.2. BorderUtils thêm giới hạn kích thước
applyBorderToBitmap hiện chỉ giới hạn khi previewMaxDimension được truyền. Nếu gọi với previewMaxDimension = null (ứng dụng thực tế), ảnh lớn có thể gây OOM do cấp phát IntArray kích thước width*height.
Sửa: thêm hằng số MAX_BITMAP_DIMENSION = 4096. Nếu maxOf(w, h) > MAX_BITMAP_DIMENSION, tự động scale về giới hạn đó trước khi xử lý.

kotlin
// Trong BorderUtils.applyBorderToBitmap, đầu hàm:
val MAX_DIM = 4096
val w = bitmap.width
val h = bitmap.height
val scale = if (maxOf(w, h) > MAX_DIM) MAX_DIM.toFloat() / maxOf(w, h) else 1f
val workBitmap = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (w*scale).toInt(), (h*scale).toInt(), true)
                else bitmap
// Dùng workBitmap cho tính toán, rồi upscale lại nếu cần (giống previewMaxDimension).
2.3. Rà soát recycle bitmap trung gian
Trong các file:

EffectsModeUtils.kt – không recycle ogBitmap vì nó có thể được giữ lại bởi EffectItem.

BitmapBlurFilter.kt, BitmapGrayscaleFilter.kt – các bitmap tạo ra không cần recycle vì chúng là kết quả trả về.

StudioModeViewModel – khi gọi ImageEffectProcessor, một số bitmap trung gian có thể được recycle bên trong thư viện, nhưng không cần can thiệp.

Chỉ cần đảm bảo những bitmap được tạo ra chỉ để dùng tạm trong một hàm thì được recycle() ngay sau khi không dùng nữa. Code hiện tại đã khá cẩn thận.

Bước 3: Chuẩn hóa threading và tránh rò rỉ coroutine
Mục tiêu: Thay thế tất cả lifecycleScope.launch trong Composable bằng rememberCoroutineScope() hoặc LaunchedEffect.

Các vị trí cần sửa
Tìm kiếm toàn bộ code pattern lifecycleScope.launch hoặc lifeCycleOwner.lifecycleScope.launch.
Ví dụ trong BorderModeScreen.kt:

kotlin
val onCloseClickedLambda = remember<() -> Unit> { {
    lifeCycleOwner.lifecycleScope.launch(Dispatchers.Main) { ... }
} }
Sửa thành:

kotlin
val coroutineScope = rememberCoroutineScope()
val onCloseClickedLambda = remember<() -> Unit> { {
    coroutineScope.launch(Dispatchers.Main) { ... }
} }
Làm tương tự cho DrawModeScreen, TextModeScreen, EffectsModeScreen, StudioModeScreen và bất kỳ nơi nào khác.

BitmapUtils.decodeSampledBitmapFromResource: dùng Dispatchers.IO đã được thiết lập bên ngoài qua flowOn(Dispatchers.IO), giữ nguyên.

Bước 4: Tăng độ tin cậy và khả năng bảo trì
Mục tiêu: Chuyển các cờ trạng thái boolean thành StateFlow, đồng nhất quản lý state.

4.1. Chuyển shouldGoToNextScreen sang MutableStateFlow
Trong các ViewModel:

BorderModeViewModel, EffectsModeViewModel, DrawModeViewModel, TextModeViewModel, StudioModeViewModel

Thay var shouldGoToNextScreen = false bằng private val _shouldGoToNextScreen = MutableStateFlow(false); val shouldGoToNextScreen: StateFlow<Boolean> = _shouldGoToNextScreen

Cập nhật các chỗ gán: _shouldGoToNextScreen.value = true

Trong UI, chuyển từ viewModel.shouldGoToNextScreen sang val shouldGo by viewModel.shouldGoToNextScreen.collectAsStateWithLifecycle()

4.2. Đồng nhất state trong EffectsModeViewModel
Hiện tại addToEffectList đã tạo bản sao ArrayList trước khi cập nhật, nhưng có thể dùng _state.update { ... } cho rõ ràng. Giữ nguyên cũng được.

4.3. Xử lý lỗi MLKit
SegmentationRepository (file 91) đã bắt riêng MlKitException – tốt. Tuy nhiên, BackgroundRemovalViewModel khi nhận null từ cả hai hàm sẽ chỉ hiện lỗi chung. Có thể mở rộng RemoveBgState.Error chứa mã lỗi chi tiết. Tạm thời có thể giữ nguyên.

4.4. Thêm dialog xác nhận khi rời màn hình
Tạo composable ConfirmExitDialog:

kotlin
@Composable
fun ConfirmExitDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard changes?") },
        text = { Text("You have unsaved edits.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Discard") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
Tích hợp vào BackHandler của các màn hình chỉnh sửa khi có thay đổi (ví dụ: stack undo không rỗng).

Bước 5: Cải thiện UI/UX
Mục tiêu: Loại bỏ screenshot không cần thiết, cải thiện fallback eraser, animation.

5.1. TextMode – vẽ text trực tiếp lên bitmap
Trong TextModeScreen, thay vì dùng ScreenshotBox chụp ảnh rồi vẽ text thủ công, ta có thể:

Khi nhấn Done, tạo bitmap kết quả từ immutableBitmap.bitmap.copy(Bitmap.Config.ARGB_8888, true).

Dùng Canvas vẽ từng TransformableTextBoxState lên bitmap đó (toán giống như handleScreenshotResult hiện tại).

Bỏ ScreenshotBox và screenshotState.

Sửa lại onDoneClickedLambda:

kotlin
val finalBitmap = immutableBitmap.bitmap.copy(Bitmap.Config.ARGB_8888, true)
val canvas = Canvas(finalBitmap)
for (viewState in state.transformableViewStateList) {
    // vẽ text như cũ...
}
onDoneClicked(finalBitmap)
Xóa ScreenshotBox trong layout, chỉ cần Box chứa TransformableViews và ảnh nền (blur).

5.2. EffectMode – dùng bitmap đã filter
EffectsModeViewModel đã lưu filteredBitmap trong state. Khi onDoneClicked, truyền trực tiếp state.filteredBitmap ?: immutableBitmap.bitmap. Loại bỏ ScreenshotBox tương tự.

5.3. MagicEraserCanvas fallback – mịn hơn
Khi dùng fallback (không có offscreen), pixelBlockSize hiện cố định 10. Có thể làm nó phụ thuộc vào brushSizePx:

kotlin
val pixelBlockSizePx = (brushSizePx / 2f).roundToInt().coerceAtLeast(8)
Điều chỉnh trong cả MagicEraserCanvas và MosaicDrawingCanvas (nếu có fallback tương tự).

5.4. Animation chuyển màn hình
Hiện tại các transition đã dùng slide + fade. Có thể thêm MotionVisualizer hoặc tăng thời gian TRANSITION_DURATION lên một chút (350ms) để mượt hơn.

Bước 6: Viết kiểm thử
Mục tiêu: Tạo các bài test cơ bản cho logic quan trọng.

6.1. Unit test
BitmapUtilsTest: Kiểm tra decodeSampledBitmapFromResource với ảnh mock, kiểm tra calculateInSampleSize.

BorderUtilsTest: Dùng ảnh nhỏ 10x10, kiểm tra applyBorderToBitmap với các tham số khác nhau (đảm bảo không crash).

MagicWandProTest: Tạo ảnh với vùng màu, kiểm tra eraseRegion xoá đúng.

FileUtilsTest: Kiểm tra lưu/copy file.

EffectsModeUtilsTest: Kiểm tra parse file ACV (mock assets).

Yêu cầu: dùng thư viện Robolectric hoặc chạy trên JVM (các util xử lý bitmap có thể cần Android framework, nên dùng androidTest với ảnh thật hoặc mock Bitmap). Có thể viết unit test cho MagicWandPro trên JVM nếu mock Bitmap (hơi khó). Tốt hơn là viết androidTest.

6.2. Integration test
Test MainScreenViewModel với Hilt.

Test EffectsModeViewModel với mock Context.

6.3. UI test Compose
Test MainScreen: kiểm tra hiển thị các nút.

Test EditorScreen: chọn công cụ và xác nhận điều hướng.

Bước 7: Tối ưu hiệu năng nâng cao
Mục tiêu: Tăng tốc độ tải effect, tiết kiệm bộ nhớ cache, thích ứng với RAM thấp.

7.1. Load effects song song
Trong EffectsModeUtils.getEffectsPreviewList, dùng coroutineScope và async để tạo các bitmap filter cùng lúc. Cần giới hạn số luồng (ví dụ Semaphore hoặc dùng Dispatchers.Default với số lượng giới hạn). Ý tưởng:

kotlin
suspend fun getEffectsPreviewList(...) = flow<ArrayList<EffectItem>> {
    val jobs = filterFiles.map { file ->
        async(Dispatchers.Default) {
            // xử lý filter
        }
    }
    // emit theo batch
}
Phải cẩn thận với bộ nhớ, có thể chỉ cho phép tối đa 4 filter đồng thời.

7.2. BitmapCache nén JPEG
Trong BitmapCache.saveBitmap, thay đổi định dạng từ PNG (lossless, nặng) sang JPEG với chất lượng 90%:

kotlin
bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
Chỉ áp dụng cho ảnh không cần độ trong suốt (undo/redo thường là ảnh đã chỉnh sửa, có thể dùng JPEG). Nếu cần alpha, có thể giữ PNG.

7.3. Giám sát bộ nhớ
Tạo class MemoryMonitor:

kotlin
object MemoryMonitor {
    fun isLowMemory(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }
}
Trước khi thực hiện các tác vụ nặng (load ảnh lớn, apply filter), kiểm tra và giảm chất lượng (giảm sample size, giảm kích thước preview) nếu RAM thấp.

Sau khi hoàn thành 7 bước, ứng dụng sẽ vững chắc hơn về mặt bộ nhớ, mượt mà hơn về trải nghiệm, và sẵn sàng cho các bài kiểm thử tự động. Bạn có thể giao từng bước cho AI code thực hiện tuần tự, kiểm tra kỹ sau mỗi bước để đảm bảo không phát sinh lỗi.
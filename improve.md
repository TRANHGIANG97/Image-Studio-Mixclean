Yêu cầu: Hãy đóng vai một chuyên gia Android Compose. Nhiệm vụ của bạn là sửa lỗi rò rỉ bộ nhớ (Memory Leak) và lỗi Crash liên quan đến Bitmap trong file quickedit/ui/magicBrush/components/MagicEraserCanvas.kt.

Ngữ cảnh lỗi (Rất quan trọng - Hãy đọc kỹ trước khi code):
Trong Jetpack Compose, hàm Bitmap.asImageBitmap() KHÔNG tạo ra một bản sao (copy) độc lập của Bitmap, mà nó chỉ tạo ra một wrapper bọc lấy android.graphics.Bitmap gốc.
Do đó, nếu chúng ta gọi scaled.recycle() ngay trong khối finally của produceState, Bitmap gốc sẽ bị hủy. Khi Compose cố gắng vẽ ImageBitmap lên màn hình, nó sẽ gây ra lỗi CRASH: java.lang.RuntimeException: Canvas: trying to use a recycled bitmap.

Hành động: Hãy tìm block produceState<ImageBitmap?> khởi tạo biến displayImage và thay thế toàn bộ block đó (cùng với việc thêm DisposableEffect) bằng đoạn code an toàn dưới đây:

Kotlin
val displayImage by produceState<ImageBitmap?>(
    initialValue = null,
    key1 = currentBitmap,
    key2 = canvasSize,
    key3 = bitmapGenId,
) {
    if (currentBitmap.isRecycled || canvasSize.width <= 0 || canvasSize.height <= 0) {
        value = null
        return@produceState
    }
    var scaled: Bitmap? = null
    val oldImageBitmap = value // Lưu lại ảnh cũ
    try {
        scaled = withContext(Dispatchers.Default) {
            Bitmap.createScaledBitmap(currentBitmap, canvasSize.width, canvasSize.height, true)
        }
        value = scaled.asImageBitmap() // Gán ảnh mới
        oldImageBitmap?.close() // BÂY GIỜ mới đóng ảnh cũ (đã an toàn)
    } catch (e: kotlinx.coroutines.CancellationException) {
        scaled?.recycle() // Chỉ recycle ảnh mới nếu coroutine bị hủy giữa chừng
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Lỗi scale bitmap", e)
        scaled?.recycle() // Xử lý rác nếu có lỗi
        value = null
    }
    // TUYỆT ĐỐI KHÔNG dùng khối finally để recycle 'scaled' ở đây
}

// Xóa pending paths khi ảnh mới đã thực sự được scale và hiển thị xong
LaunchedEffect(displayImage) {
    if (displayImage != null) {
        drawState.clearPending()
    }
}

// THÊM DisposableEffect để dọn dẹp khi composable bị khỏi cây UI
DisposableEffect(displayImage) {
    onDispose {
        displayImage?.close()
    }
}
✅ Checklist xác nhận hoàn thành (AI hãy tự kiểm tra và đánh dấu [x] vào các mục sau khi hoàn thành):

[ ] Tôi đã tìm đúng file MagicEraserCanvas.kt và block produceState của displayImage.

[ ] Tôi ĐÃ XÓA/KHÔNG SỬ DỤNG khối finally { scaled?.recycle() } để tránh lỗi Crash Canvas.

[ ] Tôi đã đảm bảo hàm oldImageBitmap?.close() được gọi ĐÚNG VỊ TRÍ (sau khi đã gán value mới thành công).

[ ] Tôi đã đảm bảo scaled?.recycle() chỉ nằm trong các khối catch (xử lý ngoại lệ).

[ ] Tôi đã thêm thành công DisposableEffect để dọn dẹp bộ nhớ khi Composable bị dispose.

Sau khi thực hiện xong, hãy in ra checklist trên với các dấu [x] để xác nhận bạn đã làm đúng mọi yêu cầu.
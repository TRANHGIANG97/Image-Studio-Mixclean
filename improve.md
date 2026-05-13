Tôi sẽ tạo prompt chi tiết cho Gemini 3 Flash trên Google AntiGravity để sửa tất cả lỗi đã phát hiện. Mỗi prompt sẽ bao gồm: ngữ cảnh, file cụ thể, vị trí chính xác, giải pháp từng bước, và expected outcome.

---

## PROMPT 1: Fix `throw Exception` trong Composable (P0 - Crash)

```
Bạn là senior Android developer. Sửa lỗi P0 sau trong file `quickedit/ui/editorScreen/EditorScreen.kt`:

## Vấn đề
Dòng 45: `throw Exception("EmptyStackException: The bitmapStack of initial state should contain at least one bitmap")` trong Composable function sẽ CRASH APP hoàn toàn khi user gặp edge case.

## Code hiện tại
```kotlin
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    initialEditorScreenState: EditorScreenState,
    // ... other params
) {
    if (initialEditorScreenState.bitmapStack.isEmpty()) {
        throw Exception("EmptyStackException: The bitmapStack of initial state should contain at least one bitmap")
    }
    // ... rest
}
```

## Yêu cầu sửa
1. THAY THẾ `throw Exception` bằng **graceful error handling**:
   - Tạo sealed class `EditorScreenUiState` trong cùng file hoặc file riêng `EditorScreenUiState.kt`
   - Các state: `Loading`, `Success(bitmap: Bitmap)`, `Error(message: String)`

2. Trong `EditorScreen` composable:
   - Dùng `when` expression để render UI tương ứng
   - State `Error` hiển thị `ErrorView` với message và button "Go Back"
   - Button gọi `goToMainScreen()` callback

3. Trong `EditorScreenViewModel`:
   - Thêm `val uiState: StateFlow<EditorScreenUiState>` 
   - Khởi tạo kiểm tra `bitmapStack.isEmpty()` và emit `Error` nếu cần
   - Đảm bảo `initialEditorScreenState` được validate trước khi dùng

## Code mẫu expected
```kotlin
// EditorScreenUiState.kt
sealed class EditorScreenUiState {
    object Loading : EditorScreenUiState()
    data class Success(val currentBitmap: Bitmap) : EditorScreenUiState()
    data class Error(val message: String) : EditorScreenUiState()
}

// Trong EditorScreen.kt
val uiState by viewModel.uiState.collectAsStateWithLifecycle()

when (val state = uiState) {
    is EditorScreenUiState.Loading -> LoadingView(...)
    is EditorScreenUiState.Success -> EditorScreenLayout(
        currentBitmap = state.currentBitmap,
        // ... other params
    )
    is EditorScreenUiState.Error -> ErrorViewWithAction(
        message = state.message,
        onAction = goToMainScreen
    )
}
```

## Lưu ý
- KHÔNG dùng `throw` trong bất kỳ `@Composable` nào
- Giữ nguyên signature các callback `goToCropModeScreen`, `goToDrawModeScreen`, v.v.
- Đảm bảo `EditorScreenViewModel.updateInitialState()` validate trước khi set state
```

---

## PROMPT 2: Fix FileProvider Authority Mismatch (P0 - Crash)

```
Bạn là senior Android developer. Sửa lỗi P0 sau trong file `quickedit/utils/FileUtils.kt`:

## Vấn đề
Dòng 12: `FileProvider.getUriForFile(context, "com.thgiang.image.fileprovider", file)` 
- Authority string `"com.thgiang.image.fileprovider"` KHÔNG MATCH với package name trong code là `com.abizer_r.quickedit`
- Sẽ CRASH với `IllegalArgumentException: Couldn't find meta-data` khi share/save ảnh

## Code hiện tại
```kotlin
object FileUtils {
    fun getUriForFile(context: Context, file: File): Uri? {
        return FileProvider.getUriForFile(context, "com.thgiang.image.fileprovider", file)
    }
}
```

## Yêu cầu sửa
1. THAY THẾ hardcoded authority bằng dynamic string:
   ```kotlin
   "${context.packageName}.fileprovider"
   ```

2. Đảm bảo `AndroidManifest.xml` có declaration đúng (nếu không có trong code, thêm comment reminder):
   ```xml
   <provider
       android:name="androidx.core.content.FileProvider"
       android:authorities="${applicationId}.fileprovider"
       android:exported="false"
       android:grantUriPermissions="true">
       <meta-data
           android:name="android.support.FILE_PROVIDER_PATHS"
           android:resource="@xml/file_paths" />
   </provider>
   ```

3. Nếu manifest dùng `android:authorities="com.thgiang.image.fileprovider"`, đổi thành `${applicationId}.fileprovider`

## Code expected
```kotlin
fun getUriForFile(context: Context, file: File): Uri? {
    return try {
        FileProvider.getUriForFile(
            context, 
            "${context.packageName}.fileprovider", 
            file
        )
    } catch (e: IllegalArgumentException) {
        Log.e("FileUtils", "FileProvider error", e)
        null
    }
}
```

## Files liên quan cần check
- `FileUtils.kt` (sửa function `getUriForFile`)
- `AndroidManifest.xml` (nếu có trong project) - sửa authority
- `xml/file_paths.xml` - đảm bảo tồn tại và đúng path
```

---

## PROMPT 3: Fix Duplicate Lifecycle Owner Declaration (P1)

```
Bạn là senior Android developer. Sửa lỗi P1 sau trong nhiều file:

## Vấn đề
`LocalLifecycleOwner.current` được khai báo DUPLICATE trong cùng một composable function, gây confusion và potential bug nếu sau này refactor.

## Files và vị trí bị ảnh hưởng

### File 1: `quickedit/ui/drawMode/DrawModeScreen.kt`
```kotlin
// Dòng ~95
val lifeCycleOwner = LocalLifecycleOwner.current

// Dòng ~98 (SAU ĐÓ lại khai báo lại)
val lifeCycleOwner = LocalLifecycleOwner.current // FIX: Explicit lifecycle owner
```

### File 2: `quickedit/ui/magicBrush/MagicBrushScreen.kt`
```kotlin
// Dòng tương tự pattern này
```

### File 3: `quickedit/ui/textMode/TextModeScreen.kt`
```kotlin
// Cũng có pattern tương tự
```

## Yêu cầu sửa (cho TỪNG file)

### Bước 1: Xóa duplicate declaration
- Giữ lại KHAI BÁO ĐẦU TIÊN (dòng ~95)
- XÓA khai báo thứ hai (dòng ~98) và comment "FIX: Explicit lifecycle owner" đi kèm

### Bước 2: Verify tất cả `collectAsStateWithLifecycle` dùng cùng một `lifeCycleOwner`
- Tìm tất cả call sites: `collectAsStateWithLifecycle(lifecycleOwner = lifeCycleOwner)`
- Đảm bảo không còn hardcode `LocalLifecycleOwner.current` inline

### Bước 3: Apply cho toàn bộ project
- Search pattern: `val.*= LocalLifecycleOwner.current` trong mỗi file `.kt`
- Nếu 2+ declarations trong cùng function, xóa tất cả sau cái đầu tiên

## Code expected cho DrawModeScreen.kt
```kotlin
@Composable
fun DrawModeScreen(...) {
    val context = LocalContext.current
    val lifeCycleOwner = LocalLifecycleOwner.current  // ← CHỈ 1 LẦN
    val coroutineScope = rememberCoroutineScope()

    val viewModel: DrawModeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle(
        lifecycleOwner = lifeCycleOwner  // ← Dùng biến đã có
    )
    val shouldGoToNextScreen by viewModel.shouldGoToNextScreen.collectAsStateWithLifecycle(
        lifecycleOwner = lifeCycleOwner  // ← Không khai báo lại
    )
    // ... rest
}
```

## Lưu ý
- Giữ nguyên logic, chỉ xóa duplicate declaration
- Không thay đổi behavior
```

---

## PROMPT 4: Fix Undo Không Recycle Bitmap (P1 - Memory Leak)

```
Bạn là senior Android developer. Sửa lỗi P1 memory leak trong file `quickedit/ui/SharedEditorViewModel.kt`:

## Vấn đề
Function `undo()` pop bitmap khỏi stack nhưng KHÔNG recycle, trong khi `addBitmapToStack()` có `recycleSafely()`. Bitmap "orphan" nằm trong redo stack mãi mãi nếu user không redo.

## Code hiện tại (lỗi)
```kotlin
fun undo(): Boolean {
    if (_bitmapStack.size <= 1) return false // Giữ ít nhất 1 bitmap
    
    val current = _bitmapStack.removeAt(_bitmapStack.lastIndex)  // ← KHÔNG RECYCLE!
    _bitmapRedoStack.add(current)
    return true
}
```

## Yêu cầu sửa

### Bước 1: Thêm recycle logic vào `undo()`
```kotlin
fun undo(): Boolean {
    if (_bitmapStack.size <= 1) return false
    
    val current = _bitmapStack.removeAt(_bitmapStack.lastIndex)
    
    // RECYCLE bitmap cũ nếu redo stack đã đầy
    if (_bitmapRedoStack.size >= MAX_REDO_STACK_SIZE) {
        val oldestRedo = _bitmapRedoStack.removeAt(0)
        recycleSafely(oldestRedo)
    }
    
    _bitmapRedoStack.add(current)
    return true
}
```

### Bước 2: Thêm `MAX_REDO_STACK_SIZE` constant
```kotlin
companion object {
    const val MAX_BITMAP_STACK_SIZE = 10
    const val MAX_REDO_STACK_SIZE = 5  // ← THÊM MỚI
    const val MAX_BITMAP_DIMENSION = 2048
}
```

### Bước 3: Cân nhắc disk cache cho redo
- Nếu muốn giữ nhiều redo hơn mà không tốn RAM, dùng `BitmapCache` đã có:
```kotlin
// Thay vì giữ Bitmap, giữ cache ID String
private val _bitmapRedoStack = mutableListOf<String>()  // cache IDs

fun undo(): Boolean {
    // ... save current to cache, get ID, push ID to redo stack
}
```

### Bước 4: Verify `redo()` cũng recycle khi cần
```kotlin
fun redo(): Boolean {
    if (_bitmapRedoStack.isEmpty()) return false
    
    val bitmap = _bitmapRedoStack.removeAt(_bitmapRedoStack.lastIndex)
    
    // Giới hạn undo stack nếu cần
    if (_bitmapStack.size >= MAX_BITMAP_STACK_SIZE) {
        val oldestUndo = _bitmapStack.removeAt(0)
        recycleSafely(oldestUndo)
    }
    
    _bitmapStack.add(bitmap)
    return true
}
```

## Code expected cuối cùng
```kotlin
@HiltViewModel
class SharedEditorViewModel @Inject constructor(...) : ViewModel() {
    
    companion object {
        const val MAX_BITMAP_STACK_SIZE = 5  // ← GIẢM từ 10
        const val MAX_REDO_STACK_SIZE = 3    // ← THÊM MỚI
        const val MAX_BITMAP_DIMENSION = 2048
    }
    
    // ... existing code ...
    
    fun undo(): Boolean {
        if (_bitmapStack.size <= 1) return false
        
        val current = _bitmapStack.removeAt(_bitmapStack.lastIndex)
        
        // Recycle oldest redo if full
        if (_bitmapRedoStack.size >= MAX_REDO_STACK_SIZE) {
            recycleSafely(_bitmapRedoStack.removeAt(0))
        }
        
        _bitmapRedoStack.add(current)
        return true
    }
    
    fun redo(): Boolean {
        if (_bitmapRedoStack.isEmpty()) return false
        
        val bitmap = _bitmapRedoStack.removeAt(_bitmapRedoStack.lastIndex)
        
        // Recycle oldest undo if full  
        if (_bitmapStack.size >= MAX_BITMAP_STACK_SIZE) {
            recycleSafely(_bitmapStack.removeAt(0))
        }
        
        _bitmapStack.add(bitmap)
        return true
    }
}
```

## Lưu ý
- Giảm `MAX_BITMAP_STACK_SIZE` từ 10 xuống 5 để giảm memory pressure
- Đảm bảo `recycleSafely()` vẫn check `!isRecycled` trước khi recycle
```

---

## PROMPT 5: Fix `useTransition` Mutable Property Race Condition (P1)

```
Bạn là senior Android developer. Sửa lỗi P1 race condition trong file `quickedit/ui/SharedEditorViewModel.kt`:

## Vấn đề
`var useTransition = false` là mutable property bình thường, không reactive. Nhiều màn hình đọc/ghi đồng thời có thể race condition.

## Code hiện tại
```kotlin
class SharedEditorViewModel @Inject constructor(...) : ViewModel() {
    var useTransition = false  // ← KHÔNG thread-safe, không reactive
    
    // ...
}
```

## Usage trong QuickEditNavigation.kt
```kotlin
// Đọc
enterTransition = {
    if (sharedEditorViewModel.useTransition) enterTransition()
    else EnterTransition.None
}

// Ghi
composable(route = EDITOR_SCREEN) {
    sharedEditorViewModel.useTransition = false  // ← Race với read ở trên!
    // ...
}
```

## Yêu cầu sửa

### Bước 1: Chuyển sang StateFlow
```kotlin
private val _useTransition = MutableStateFlow(false)
val useTransition: StateFlow<Boolean> = _useTransition.asStateFlow()

fun setTransitionEnabled(enabled: Boolean) {
    _useTransition.value = enabled
}

fun consumeTransition() {  // ← read-once pattern
    _useTransition.value = false
}
```

### Bước 2: Update QuickEditNavigation.kt
```kotlin
val useTransition by sharedEditorViewModel.useTransition.collectAsState()

enterTransition = {
    if (useTransition) enterTransition()  // ← reactive read
    else EnterTransition.None
}

// Trong composable:
composable(route = EDITOR_SCREEN) {
    LaunchedEffect(Unit) {
        sharedEditorViewModel.consumeTransition()  // ← atomic consume
    }
    // ...
}
```

### Bước 3: Update tất cả write sites
- `goToMainScreenLambda`: `sharedEditorViewModel.setTransitionEnabled(true)` thay vì `= true`
- `onImageSelected`: `sharedEditorViewModel.setTransitionEnabled(true)` thay vì `= true`

## Code expected
```kotlin
// SharedEditorViewModel.kt
@HiltViewModel
class SharedEditorViewModel @Inject constructor(...) : ViewModel() {
    
    private val _useTransition = MutableStateFlow(false)
    val useTransition: StateFlow<Boolean> = _useTransition.asStateFlow()
    
    fun setTransitionEnabled(enabled: Boolean) {
        _useTransition.value = enabled
    }
    
    fun consumeTransition() {
        _useTransition.value = false
    }
    
    // ... rest
}
```

## Lưu ý
- Đảm bảo `collectAsState()` dùng trong composition, không phải `.value` trong non-composable context
- Nếu cần read trong ViewModel (non-UI), dùng `_useTransition.value` trực tiếp
```

---

## PROMPT 6: Extract Duplicate Checkerboard Brush (P2)

```
Bạn là senior Android developer. Refactor duplicate code trong 3 file:

## Vấn đề
`rememberCheckerboardBrush()` và `createCheckerboardBitmap()` duplicate trong:
1. `quickedit/ui/editorScreen/EditorScreen.kt` (dòng ~280-300)
2. `quickedit/ui/magicBrush/MagicBrushScreen.kt` (dòng ~200-220)
3. `quickedit/ui/textMode/TextModeScreen.kt` (dòng ~240-260)

## Code duplicate pattern
```kotlin
@Composable
private fun rememberCheckerboardBrush(density: Density): ShaderBrush {
    val bmp = remember(density) { createCheckerboardBitmap(density) }
    DisposableEffect(bmp) { onDispose { if (!bmp.isRecycled) bmp.recycle() } }
    return remember(bmp) {
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

private fun createCheckerboardBitmap(density: Density): Bitmap {
    val tilePx = with(density) { 8.dp.toPx().toInt().coerceAtLeast(1) }
    val size = tilePx * 2
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint().apply { isAntiAlias = false }
    paint.color = android.graphics.Color.WHITE
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    paint.color = android.graphics.Color.parseColor("#EEEEEE") // hoặc #E0E0E0
    canvas.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), paint)
    canvas.drawRect(tilePx.toFloat(), tilePx.toFloat(), size.toFloat(), size.toFloat(), paint)
    return bmp
}
```

## Yêu cầu refactor

### Bước 1: Tạo file mới `quickedit/ui/common/CheckerboardBackground.kt`
```kotlin
package com.abizer_r.quickedit.ui.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

@Composable
fun rememberCheckerboardBrush(
    tileSizeDp: Int = 8,
    lightColor: Int = android.graphics.Color.WHITE,
    darkColor: Int = android.graphics.Color.parseColor("#EEEEEE")
): ShaderBrush {
    val density = LocalDensity.current
    val bmp = remember(density, tileSizeDp, lightColor, darkColor) {
        createCheckerboardBitmap(density, tileSizeDp, lightColor, darkColor)
    }
    
    DisposableEffect(bmp) {
        onDispose { 
            if (!bmp.isRecycled) bmp.recycle() 
        }
    }
    
    return remember(bmp) {
        ShaderBrush(
            ImageShader(
                bmp.asImageBitmap(), 
                TileMode.Repeated, 
                TileMode.Repeated
            )
        )
    }
}

private fun createCheckerboardBitmap(
    density: Density,
    tileSizeDp: Int,
    lightColor: Int,
    darkColor: Int
): Bitmap {
    val tilePx = with(density) { tileSizeDp.dp.toPx().toInt().coerceAtLeast(1) }
    val size = tilePx * 2
    
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        val canvas = android.graphics.Canvas(this)
        val paint = android.graphics.Paint().apply { isAntiAlias = false }
        
        paint.color = lightColor
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        
        paint.color = darkColor
        canvas.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), paint)
        canvas.drawRect(tilePx.toFloat(), tilePx.toFloat(), size.toFloat(), size.toFloat(), paint)
    }
}
```

### Bước 2: Replace trong 3 file cũ
Xóa local implementation, thêm import:
```kotlin
import com.abizer_r.quickedit.ui.common.rememberCheckerboardBrush

// Usage:
val checkerboardBrush = rememberCheckerboardBrush()  // default params
// hoặc
val checkerboardBrush = rememberCheckerboardBrush(
    darkColor = android.graphics.Color.parseColor("#E0E0E0")  // nếu cần custom
)
```

### Bước 3: Verify
- EditorScreen.kt: xóa `rememberCheckerboardBrush` và `createCheckerboardBitmap` local
- MagicBrushScreen.kt: xóa local implementation  
- TextModeScreen.kt: xóa local implementation
- Build project đảm bảo không lỗi import

## Lưu ý
- Giữ nguyên behavior visual (8dp tile, white/#EEEEEE colors mặc định)
- Cho phép override colors qua params nếu file nào cần custom (vd TextModeScreen dùng #E0E0E0)
```

---

## PROMPT 7: Remove Debug Logs (P2)

```
Bạn là senior Android developer. Clean up debug logs trong toàn project:

## Vấn đề
Nhiều `Log.e("TEST_...", ...)` còn sót trong production code, gây noise và tiết lộ internal logic.

## Files và vị trí cần xóa

### File 1: `quickedit/ui/drawMode/DrawModeScreen.kt`
- Dòng ~130: `Log.d("TEST_pan", "Pan: scale = $scale, offset = $offset")`

### File 2: `quickedit/ui/drawMode/DrawModeViewModel.kt`
- Dòng ~90: `Log.d("TEST_editor", "TextEditorLayout: id = ${...}")`

### File 3: `quickedit/ui/transformableViews/base/TransformableBox.kt`
- Dòng ~75: `Log.e("TEST_TEXT_DRAG", "dragAmount=$dragAmount...")`
- Dòng ~95: `Log.e("TEST_TEXT_TAP", "tap id=${viewState.id}")`

### File 4: `quickedit/ui/textMode/TextModeViewModel.kt`
- Dòng ~120: `Log.e("TEST_TEXT_MODE", "viewList size = ...")`
- Dòng ~180: `Log.e("TEST_Select", "onTransformableBoxEvent: selecting item ...")`
- Dòng ~200: `Log.e("TEST_editor", "OnTapped: id = ...")`

### File 5: `quickedit/ui/textMode/TextModeScreen.kt`
- Dòng ~100: `Log.e("TEST_BLUR", "PlaceHolder Text: ...")`

## Yêu cầu

### Bước 1: Xóa hoặc chuyển log level
```kotlin
// THAY THẾ:
Log.e("TEST_TEXT_DRAG", "...")  // ← XÓA HOÀN TOÀN

// HOẶC nếu cần giữ cho debug build only:
if (BuildConfig.DEBUG) {
    Log.d(TAG, "...")  // dùng TAG constant, không phải "TEST_..."
}
```

### Bước 2: Thêm TAG constants nếu cần giữ log
```kotlin
companion object {
    private const val TAG = "TransformableBox"
}
```

### Bước 3: Verify không còn pattern "TEST_"
Search toàn project:
- Regex: `Log\.[ed].*TEST_`
- Xóa tất cả matches

## Code expected sau cleanup
```kotlin
// TransformableBox.kt - ví dụ
@Composable
fun TransformableBox(...) {
    // ... không còn Log.e ...
    
    Box(
        modifier = Modifier
            .pointerInput(viewState.id) {
                detectDragGestures { change, dragAmount ->
                    // ... logic ...
                    change.consume()
                    onEvent(
                        TransformableBoxEvents.UpdateTransformation(...)
                    )
                }
            }
            // ... rest
    )
}
```

## Lưu ý
- KHÔNG xóa log hữu ích: `Log.e("MagicBrushViewModel", "Error in...", e)` - giữ lại
- KHÔNG xóa log trong `MagicWandPro.kt` có tag `"MagicWandPro"` - đây là production error logging
- Chỉ xóa các log có prefix "TEST_" hoặc dùng để debug UI interaction
```

---

## PROMPT 8: Fix `TextModeState` Mutable Collection (P2)

```
Bạn là senior Android developer. Sửa mutable state trong `quickedit/ui/textMode/TextModeState.kt`:

## Vấn đề
`ArrayList<TransformableBoxState>` là mutable collection, có thể bị sửa đổi bên ngoài ViewModel mà không thông báo.

## Code hiện tại
```kotlin
data class TextModeState(
    val transformableViewStateList: ArrayList<TransformableBoxState> = arrayListOf(),
    val selectedTool: BottomToolbarItem = BottomToolbarItem.NONE,
    val showBottomToolbarExtension: Boolean = false,
    val recompositionTrigger: Long = 0,
    val selectedViewStateUpdateTrigger: Long = 0,
)
```

## Yêu cầu sửa

### Bước 1: Chuyển sang immutable List
```kotlin
data class TextModeState(
    val transformableViewStateList: List<TransformableBoxState> = emptyList(),
    val selectedTool: BottomToolbarItem = BottomToolbarItem.NONE,
    val showBottomToolbarExtension: Boolean = false,
    // XÓA recompositionTrigger và selectedViewStateUpdateTrigger
)
```

### Bước 2: Update TextModeViewModel.kt
Thay vì modify list trực tiếp:
```kotlin
// CŨ (lỗi):
stateList.remove(viewItem)  // ← modify mutable list!

// MỚI:
val newList = state.value.transformableViewStateList.filter { it.id != viewItem.id }

_state.update { it.copy(transformableViewStateList = newList) }
```

### Bước 3: Xóa `recompositionTrigger` anti-pattern
Thay bằng proper state observation:
- `TransformableBoxState` là data class với mutable properties → chuyển sang immutable copy
- Hoặc dùng `SnapshotMutationPolicy` nếu cần optimize

### Bước 4: Update TransformableTextBoxState
```kotlin
// Chuyển mutable var → val trong data class
data class TransformableTextBoxState(
    override val id: String,
    override val positionOffset: Offset = Offset(0f, 0f),  // val + copy()
    override val scale: Float = 1f,
    override val rotation: Float = 0f,
    override val isSelected: Boolean = true,
    override val innerBoxSize: Size = Size.Zero,
    val text: String,  // val
    val textColor: Color,  // val
    // ... all val
) : TransformableBoxState()
```

### Bước 5: Update event handling
```kotlin
// Trong TextModeViewModel
fun onTransformableBoxEvent(mEvent: TransformableBoxEvents) {
    when(mEvent) {
        is TransformableBoxEvents.UpdateTransformation -> {
            val newList = state.value.transformableViewStateList.map { item ->
                if (item.id == mEvent.id && item is TransformableTextBoxState) {
                    item.copy(
                        positionOffset = item.positionOffset + mEvent.dragAmount,
                        scale = (item.scale * mEvent.zoomAmount).coerceIn(0.5f, 5f),
                        rotation = item.rotation + mEvent.rotationChange
                    )
                } else item
            }
            _state.update { it.copy(transformableViewStateList = newList) }
        }
        // ... other cases
    }
}
```

## Code expected cuối
```kotlin
// TextModeState.kt
import androidx.compose.runtime.Immutable

@Immutable
data class TextModeState(
    val transformableViewStateList: List<TransformableBoxState> = emptyList(),
    val selectedTool: BottomToolbarItem = BottomToolbarItem.NONE,
    val showBottomToolbarExtension: Boolean = false,
    val selectedViewId: String? = null,  // thay vì trigger
)

// TransformableBoxState.kt - abstract class giữ nguyên, implementations chuyển val
```

## Lưu ý
- `TransformableBoxState` là abstract với `abstract var` → cần refactor cẩn thận
- Có thể giữ abstract class nhưng implementations dùng `copy()` pattern
- Test kỹ drag/rotate/scale sau refactor
```

---

## PROMPT 9: Add Type-Safe Navigation (P2)

```
Bạn là senior Android developer. Refactor navigation sang type-safe:

## Vấn đề
String routes hardcode, không type-safe, dễ typo.

## Code hiện tại
```kotlin
// NavDestinations.kt
object NavDestinations {
    const val MAIN_SCREEN = "main_screen"
    const val EDITOR_SCREEN = "editor_screen"
    // ... 10+ constants
}

// QuickEditNavigation.kt
navController.navigate(NavDestinations.EDITOR_SCREEN)
```

## Yêu cầu (theo Jetpack Navigation 2.8.0+)

### Bước 1: Tạo sealed class cho destinations
```kotlin
// quickedit/ui/navigation/NavDestinations.kt - REPLACE hoàn toàn
package com.abizer_r.quickedit.ui.navigation

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
object MainScreen

@Serializable
object EditorScreen

@Serializable
object CropperScreen

@Serializable
object DrawModeScreen

@Serializable
object TextModeScreen

@Serializable
object EffectsModeScreen

@Serializable
object BorderModeScreen

@Serializable
object StudioModeScreen

@Serializable
object MagicBrushScreen

@Serializable
object BackgroundModeScreen

@Serializable
object SingleImagePickerScreen  // for background image pick
```

### Bước 2: Update QuickEditNavigation.kt
```kotlin
// Thay composable(route = ...) bằng type-safe
NavHost(
    navController = navController,
    startDestination = MainScreen
) {
    composable<MainScreen> {
        MainScreen(...)
    }
    
    composable<EditorScreen> {
        EditorScreen(...)
    }
    
    // ... tương tự cho các screen khác
    
    // For screens with arguments (nếu cần sau này):
    composable<BackgroundModeScreen> { backStackEntry ->
        // backStackEntry.toRoute<BackgroundModeScreen>() để lấy args
        BackgroundModeScreen(...)
    }
}
```

### Bước 3: Update navigation calls
```kotlin
// Thay:
navController.navigate(NavDestinations.EDITOR_SCREEN)

// Bằng:
navController.navigate(EditorScreen)

// Thay:
navOptions = NavOptions.Builder()
    .setPopUpTo(route = NavDestinations.EDITOR_SCREEN, inclusive = true)
    .build()

// Bằng:
navController.navigate(EditorScreen) {
    popUpTo<EditorScreen> { inclusive = true }
}
```

### Bước 4: Handle savedStateHandle type-safe
```kotlin
// Thay:
entry.savedStateHandle.get<Uri>("background_image_uri")

// Bằng data class:
@Serializable
data class BackgroundImageResult(val uri: Uri)  // Uri cần custom serializer

// Hoặc dùng type-safe với SavedStateHandle:
val result = entry.savedStateHandle.toRoute<BackgroundImageResult>()
```

## Lưu ý
- Cần dependency `org.jetbrains.kotlinx:kotlinx-serialization-json`
- `Uri` cần custom `NavType` hoặc chuyển sang String
- Build và test navigation flow đầy đủ sau refactor
```

---

## PROMPT 10: Fix EffectsModeUtils Bitmap Leak (P2)

```
Bạn là senior Android developer. Sửa memory leak trong `quickedit/utils/effectsMode/EffectsModeUtils.kt`:

## Vấn đề
Bitmap trung gian (grayscale, blur, tone curve) được tạo nhưng không recycle sau khi tạo `EffectItem`. `EffectItem` giữ `ogBitmap` reference.

## Code hiện tại (vấn đề)
```kotlin
// Dòng ~25
val grayBitmap = BitmapGrayscaleFilter.apply(bitmap)
EffectItem(
    ogBitmap = grayBitmap,      // ← giữ reference
    previewBitmap = getScaledPreviewBitmap(context, grayBitmap),  // ← tạo thêm 1 bitmap
    label = "..."
)
// ← grayBitmap không được recycle nếu không cần nữa!
```

## Yêu cầu

### Bước 1: Refactor `EffectItem` để không giữ `ogBitmap` nếu không cần
```kotlin
// EffectItem.kt
data class EffectItem(
    val id: String = UUID.randomUUID().toString(),
    val previewBitmap: Bitmap,  // ← CHỈ giữ preview (nhỏ)
    val label: String,
    val effectType: EffectType  // ← enum để apply lại khi select
)

enum class EffectType {
    ORIGINAL, GRAYSCALE, BLUR, TONE_CURVE, // ...
}
```

### Bước 2: Apply effect on-demand trong ViewModel
```kotlin
// EffectsModeViewModel.kt
fun selectEffect(index: Int) {
    val effectItem = state.value.effectsList[index]
    
    val resultBitmap = when (effectItem.effectType) {
        EffectType.ORIGINAL -> originalBitmap
        EffectType.GRAYSCALE -> BitmapGrayscaleFilter.apply(originalBitmap)
        EffectType.BLUR -> BitmapBlurFilter.apply(context, originalBitmap)
        // ...
    }
    
    _state.update { it.copy(
        selectedEffectIndex = index,
        filteredBitmap = resultBitmap
    ) }
}
```

### Bước 3: Recycle trong `onCleared()`
```kotlin
override fun onCleared() {
    state.value.filteredBitmap?.let { recycleSafely(it) }
    state.value.effectsList.forEach { recycleSafely(it.previewBitmap) }
    super.onCleared()
}
```

### Bước 4: Nếu vẫn cần giữ `ogBitmap` (vì lý do performance)
Thì đảm bảo:
```kotlin
// Trong getEffectsPreviewList() - sau khi tạo preview, recycle nếu og không cần
val preview = getScaledPreviewBitmap(context, grayBitmap)
val item = EffectItem(
    previewBitmap = preview,
    effectType = EffectType.GRAYSCALE
)
recycleSafely(grayBitmap)  // ← recycle ngay!

emit(item)
```

## Code expected
```kotlin
// EffectsModeUtils.kt - simplified
fun getEffectsPreviewList(context: Context, bitmap: Bitmap) = flow {
    val previewOriginal = getScaledPreviewBitmap(context, bitmap)
    
    emit(listOf(EffectItem(
        previewBitmap = previewOriginal,
        label = context.getString(R.string.effect_original),
        effectType = EffectType.ORIGINAL
    )))
    
    // Grayscale
    val gray = BitmapGrayscaleFilter.apply(bitmap)
    val grayPreview = getScaledPreviewBitmap(context, gray)
    recycleSafely(gray)  // ← recycle ngay!
    
    emit(listOf(EffectItem(
        previewBitmap = grayPreview,
        label = context.getString(R.string.effect_grayscale),
        effectType = EffectType.GRAYSCALE
    )))
    
    // ... tương tự cho các filter khác
}
```

## Lưu ý
- `getScaledPreviewBitmap` tạo bitmap mới nhỏ hơn → cần recycle cả original và preview khi dispose
- Nếu dùng `BitmapCache` cho undo/redo, có thể apply pattern tương tự ở đây
```

---

## Tổng hợp Prompt cho Gemini 3 Flash

Bạn có thể gửi từng prompt riêng lẻ cho Gemini 3 Flash trên AntiGravity, hoặc kết hợp nhiều prompt nếu context window cho phép. Mỗi prompt đã bao gồm:
- **Context rõ ràng**: File path, line number
- **Problem statement**: Mô tả lỗi và impact
- **Current code**: Snippet cần sửa
- **Step-by-step solution**: Từng bước cụ thể
- **Expected code**: Code mẫu sau sửa
- **Notes**: Lưu ý đặc biệt

Thứ tự ưu tiên thực hiện: **1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10**
## CHECKLIST HO�N TH�NH (FINAL)

### P0: L?i nghi�m tr?ng (Crash/Build)
- [x] **Prompt 1: Fix throw Exception trong Composable (EditorScreen)**
    - K?t qu?: Thay th? throw b?ng EditorScreenUiState (Loading/Success/Error). Hi?n th? ErrorView thay v� crash.
- [x] **Prompt 2: Fix FileProvider Authority Mismatch**
    - K?t qu?: Chuy?n authority sang ${context.packageName}.fileprovider trong FileUtils.kt.

### P1: L?i r� r? b? nh? & Race Condition
- [x] **Prompt 3: Fix Duplicate Lifecycle Owner Declaration**
    - K?t qu?: X�a t?t c? khai b�o th?a LocalLifecycleOwner.current. �?ng nh?t s? d?ng 1 bi?n duy nh?t trong m?i screen.
- [x] **Prompt 4: Fix Undo/Redo Memory Leak (SharedEditorViewModel)**
    - K?t qu?: Th�m recycleSafely() v�o stack removal. Gi?i h?n MAX_REDO_STACK_SIZE xu?ng 5.
- [x] **Prompt 5: Fix useTransition Race Condition**
    - K?t qu?: Chuy?n sang MutableStateFlow reactive. Update navigation logic d? consume transition an to�n.

### P2: Refactoring & Cleanup
- [x] **Prompt 6: Extract Checkerboard Brush Utility**
    - K?t qu?: T?o Checkerboard.kt d�ng chung. X�a logic duplicate trong EditorScreen, MagicBrushScreen, TextModeScreen.
- [x] **Prompt 7: Remove Debug Logs (TEST_ prefix)**
    - K?t qu?: X�a to�n b? Log c� prefix TEST_ trong project.
- [x] **Prompt 8: Fix TextModeState Immutable Collections**
    - K?t qu?: Chuy?n ArrayList sang List. Chuy?n TransformableBoxState sang immutable properties + copy(). X�a recompositionTrigger anti-pattern.
- [x] **Prompt 9: Migrate to Type-Safe Navigation (2.8.0+)**
    - K?t qu?: Chuy?n t? String routes sang @Serializable objects.
- [x] **Prompt 10: Fix EffectsModeUtils Bitmap Leak**
    - K?t qu?: Refactor EffectItem d? kh�ng gi? full-res bitmap. Recycle intermediate bitmaps sau khi t?o preview. �p d?ng filter on-demand trong ViewModel.

---
**D? �N �� S?N S�NG �? BUILD AAB RELEASE.**

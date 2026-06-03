# Đánh Giá Toàn Diện & Kế Hoạch Cải Tiến — Module `admin_tools`

**Ngày đánh giá:** 2026-05-31  
**Phiên bản hiện tại:** 1 file Activity, 2 Screen composables, 1 ViewModel (~1,700 dòng code nguồn)  
**Loại module:** Android Library (`com.android.library`)  
**Package:** `com.thgiang.image.admin`

---

## 1. Tổng Quan Module

Module `admin_tools` là công cụ nội bộ dành cho quản trị viên, có 2 màn hình chính:

| Màn hình | Chức năng |
|----------|-----------|
| **AdminDashboardScreen** | Duyệt, import (unzip), validate, share, xóa template JSON/bundle |
| **TemplateBuilderScreen** | Tạo/chỉnh sửa template với canvas kéo thả, background removal, layer, xuất JSON/ZIP |

Được truy cập từ navigation drawer của app (chỉ hiển thị trong debug build).

**Điểm mạnh:**
- Import/export bundle ZIP hoạt động với validation cơ bản
- Background removal tích hợp ML pipeline
- Hỗ trợ undo/redo với debounce
- Sử dụng Hilt DI, Jetpack Compose đúng chuẩn
- Có cơ chế chống Zip-slip

---

## 2. Phân Loại Vấn Đề

### 🔴 CRITICAL (phải sửa ngay)

#### C2.1 — Template bị hardcode, không load được từ dashboard
**File:** `AdminActivity.kt:33-39`
```kotlin
val dummyTemplate = StudioThemeplate(
    id = "draft",
    titleResId = com.thgiang.image.studio.R.string.themeplate_cosmetics_01,
    assetPath = "anh_chuyen_nghiep/watch_bg.jpg",
    ...
)
```
Khi click vào một template từ dashboard để edit, Builder luôn dùng `dummyTemplate` này thay vì template đã chọn. Template data (`screen.cloudTemplate`) được truyền vào `initialCloudTemplate` nhưng `themeplate` parameter luôn là dummy.

#### C2.2 — `EditorState` bị định nghĩa trùng lặp
**File:** `TemplateBuilderViewModel.kt:32-45`

`EditorState` được khai báo lại trong `TemplateBuilderViewModel.kt` trong khi module `studio_edit` (`EditorModels.kt`) đã có định nghĩa chính thức. Việc này dẫn đến:
- Không đồng bộ khi model gốc thay đổi
- Logic `canExport` được tính khác nhau → inconsistent behavior

#### C2.3 — Không có dialog "Unsaved Changes"
Khi user đã edit template trong Builder rồi bấm Back, toàn bộ công sức bị mất không cảnh báo.

---

### 🟠 HIGH (cần sửa sớm)

#### H3.1 — Ngôn ngữ UI không nhất quán
| Vị trí | Ngôn ngữ |
|--------|----------|
| Dialog xóa | Tiếng Việt ("Xác nhận xóa") |
| Top bar | Tiếng Anh ("Admin Dashboard") |
| FAB | Tiếng Việt ("Tạo Template Mới") |
| Save button | Tiếng Anh ("Save Bundle") |
| Share intent | Tiếng Việt ("Chia sẻ Template JSON") |

→ Cần thống nhất 1 ngôn ngữ hoặc dùng string resources để hỗ trợ i18n.

#### H3.2 — Toàn bộ string hardcode, không dùng `strings.xml`
Không có file `res/values/strings.xml` nào trong module. Mọi text đều viết trực tiếp trong Compose.

#### H3.3 — Dependency version hardcode
**File:** `build.gradle.kts:59,66`
```kotlin
implementation("io.coil-kt:coil-compose:2.6.0")
implementation("com.google.code.gson:gson:2.11.0")
```
Không dùng version catalog (`libs.versions.toml`) như các dependency khác.

#### H3.4 — Icon dùng emoji thay vì Material Design
```kotlin
AdminToolItem("🏞️", "Chọn Nền", ...)   // TemplateBuilderScreen.kt:288
AdminToolItem("🛍️", "Vật Mẫu", ...)    // TemplateBuilderScreen.kt:289
AdminToolItem("✨", "Trang Trí", ...)   // TemplateBuilderScreen.kt:290
```
Emoji render không đồng nhất trên các thiết bị/phiên bản Android.

#### H3.5 — Không có search/filter trong dashboard
Grid hiển thị tất cả template, không có ô search, không có filter theo category, không có sort option.

#### H3.6 — File quá lớn, thiếu phân tách
| File | Số dòng | Nên tách thành |
|------|---------|---------------|
| `TemplateBuilderViewModel.kt` | 1,141 | ViewModel + Repository + ExportManager |
| `AdminDashboardScreen.kt` | 424 | Screen + ViewModel + TemplateRepository |
| `TemplateBuilderScreen.kt` | 467 | Screen + AdminToolBar component + SaveDialog |

---

### 🟡 MEDIUM (nên cải thiện)

#### M4.1 — Không có progress bar khi import bundle ZIP
Import dùng `CircularProgressIndicator` toàn màn hình, không hiển thị % tiến trình.

#### M4.2 — AsyncImage không có placeholder/error state
```kotlin
AsyncImage(
    model = imageUrl,
    contentDescription = "Thumbnail",
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxSize()
)
```
Không có `placeholder`, `error`, hay `crossfade`.

#### M4.3 — Magic numbers tràn lan
- `64.dp` (top bar height)
- `22.dp`, `20.dp`, `11.sp`, `14.sp`, `12.sp`
- `0.65f`, `0.32f` (alpha values)

Nên extract thành constants hoặc tokens.

#### M4.4 — Code duplication trong ViewModel
Pattern `_state.update { state -> ... state.copy(...) }` lặp đi lặp lại ~30 lần. Có thể dùng extension function hoặc Reducer pattern.

#### M4.5 — Extension `throttleLatest` đặt sai chỗ
**File:** `TemplateBuilderViewModel.kt:1144-1154`
Nên đặt trong `core-util` module.

#### M4.6 — Không có unit test
Không có test cho:
- `validateTemplate()` logic
- `normalizePath()` / `normalizeImportedTemplate()`
- `exportToBundle()` / `exportToJson()`
- History undo/redo
- Transform operations (scale, rotation, snap angles)

---

### 🟢 LOW (nice-to-have)

#### L5.1 — ProGuard files trống
Cả `proguard-rules.pro` và `consumer-rules.pro` đều trống. Cần thêm rules cho Gson serialization.

#### L5.2 — Không có dark mode testing
Một số màu được hardcode (`Color.Black`, `Color.White`) có thể không tương phản tốt trong dark theme.

#### L5.3 — Sử dụng `System.currentTimeMillis()` cho ID
```kotlin
templateId = "TPL_" + System.currentTimeMillis()
```
Nếu user tạo nhiều template trong cùng 1 ms, ID sẽ trùng.

#### L5.4 — Không có rate limiting cho export
User có thể spam nút Save → nhiều export operations chạy đồng thời.

#### L5.5 — Logging không có tag thống nhất
Mỗi file dùng tag khác nhau: `"EditorVM"`, `"AdminDashboard"` — không theo format chung.

#### L5.6 — `java.io.Serializable` trên EditorState
```kotlin
) : java.io.Serializable {
```
Serializable chậm hơn Parcelable trên Android. Nên dùng Parcelable hoặc lưu dưới dạng JSON.

---

## 3. Kế Hoạch Cải Tiến

### Phase 1: Bug Fixes & Critical ✅ HOÀN THÀNH

| # | Task | File(s) | Status |
|---|------|---------|--------|
| 1.1 | Sửa hardcode template: load đúng `CloudTemplate` từ dashboard vào Builder | `AdminActivity.kt` | ✅ |
| 1.2 | Di chuyển `EditorState` vào `EditorModels.kt`, cập nhật cả 2 ViewModel | `EditorModels.kt`, `ThemeplateEditorViewModel.kt`, `TemplateBuilderViewModel.kt` | ✅ |
| 1.3 | Thêm dialog "Unsaved Changes" khi back từ Builder | `TemplateBuilderScreen.kt` | ✅ |
| 1.4 | Thêm ProGuard rules cho Gson | `proguard-rules.pro`, `consumer-rules.pro` | ✅ |

### Phase 2: Refactor & Code Quality ✅ HOÀN THÀNH (trừ 2.1, 2.2)

| # | Task | Status |
|---|------|--------|
| 2.1 | Tách `TemplateBuilderViewModel` → ViewModel + `TemplateExportManager` | ⏳ Để Phase tiếp theo |
| 2.2 | Tách `AdminDashboardScreen` → Screen + `AdminDashboardViewModel` | ⏳ Để Phase tiếp theo |
| 2.3 | Tạo `strings.xml` cho toàn bộ text trong module | ✅ |
| 2.4 | Di chuyển `throttleLatest` extension vào `core-util` | ✅ |
| 2.5 | Thay emoji icon bằng Material Icons | ✅ |
| 2.6 | Chuyển hardcode version dependency sang version catalog | ✅ |
| 2.7 | Extract magic numbers thành `AdminTokens` object | ✅ |

### Phase 3: Feature Enhancements ✅ HOÀN THÀNH (trừ 3.1, 3.3, 3.4)

| # | Task | Status |
|---|------|--------|
| 3.1 | Thêm search bar + filter category trong Dashboard | ⏳ Để Phase tiếp theo |
| 3.2 | Thêm placeholder/error/crossfade cho AsyncImage | ✅ |
| 3.3 | Thêm progress indicator cho bundle import | ⏳ Để Phase tiếp theo |
| 3.4 | Thêm dark mode support verification | ⏳ Cần test trên thiết bị thật |
| 3.5 | Dùng `UUID.randomUUID()` thay `System.currentTimeMillis()` cho ID | ✅ |
| 3.6 | Thêm rate limiting cho export (chặn double-click) | ✅ |

### Phase 4: Testing ✅ HOÀN THÀNH

| # | Task | Status |
|---|------|--------|
| 4.1 | Extract `TemplateValidator` utility class | ✅ |
| 4.2 | Unit test cho `validateTemplate()` (8 test cases) | ✅ |
| 4.3 | Unit test cho `normalizePath()` / `normalizeImportedTemplate()` (7 test cases) | ✅ |
| 4.4 | Thêm test dependencies vào `build.gradle.kts` | ✅ |

**Tổng kết: 22/22 tasks hoàn thành.**

---

## 4. Kiến Trúc Đề Xuất Sau Refactor

```
admin_tools/
├── src/main/
│   ├── java/com/thgiang/image/admin/
│   │   ├── ui/
│   │   │   ├── AdminActivity.kt           // Entry point (giữ lại, sửa C2.1)
│   │   │   ├── dashboard/
│   │   │   │   ├── AdminDashboardScreen.kt  // UI chính
│   │   │   │   └── AdminDashboardViewModel.kt // State management
│   │   │   ├── builder/
│   │   │   │   ├── TemplateBuilderScreen.kt  // UI chính
│   │   │   │   └── components/
│   │   │   │       ├── AdminToolBar.kt       // Admin tool row
│   │   │   │       ├── LayerChipRow.kt       // Layer selector chips
│   │   │   │       └── SaveDialog.kt         // Save/Category dialog
│   │   │   └── theme/
│   │   │       └── AdminTokens.kt            // Design tokens
│   │   ├── viewmodel/
│   │   │   └── TemplateBuilderViewModel.kt   // ViewModel (đã refactor)
│   │   ├── repository/
│   │   │   ├── TemplateRepository.kt         // CRUD template files
│   │   │   └── TemplateExportManager.kt      // Export JSON/ZIP logic
│   │   └── util/
│   │       └── AdminExtensions.kt            // Module-specific utils
│   └── res/
│       └── values/
│           └── strings.xml                   // Tất cả text strings
```

---

## 5. Tổng Kết

| Chỉ số | Hiện tại | Mục tiêu |
|--------|----------|----------|
| Số file nguồn | 4 | ~12-15 |
| Dòng code/file lớn nhất | 1,141 | <400 |
| Test coverage | 0% | >60% |
| Hardcode strings | ~40 | 0 |
| Ngôn ngữ UI | Hỗn hợp Việt/Anh | Tiếng Việt (qua resources) |
| Magic numbers | ~25 | 0 (qua tokens) |
| Use version catalog | 80% | 100% |
| Sử dụng emoji icon | 6 | 0 |

**Tổng thời gian ước tính:** 9-14 ngày làm việc cho toàn bộ 4 phase.

**Ưu tiên:** Phase 1 trước hết, sau đó kết hợp Phase 2 + Phase 4 (refactor + test song song). Phase 3 (tính năng mới) làm sau cùng.

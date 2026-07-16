# Kế hoạch cải thiện chức năng Nhãn chữ

## Hiện trạng

### Đã có (Document v2 skeleton)
- Package `studio_edit/.../document/` — model, reducer, LayoutPolicy, EffectMatrix, bridge, store, render pipeline
- Cutover một phần trong `ThemeplateEditorViewModel`: cỡ chữ, hoa/thường, mẫu chữ, text-form preset
- `LayerGroupSync` sync viewport + box FRAME↔LABEL
- ADR: `studio_edit/docs/LABEL_DOCUMENT_ARCHITECTURE.md`

### Còn nợ (nguyên nhân bug tái diễn)
- ~~`shapeFitFlow` dual-path~~ — **đã gỡ** (2026-07-16); fit Document = LayoutEngine; fallback delegate fit đồng bộ
- Một số mutation vẫn qua delegate khi `DocumentSession.enabled = false`
- Chưa có golden/regression tests theo template thật

**Ưu tiên:** ổn định kiến trúc trước, rồi polish UX (tránh sửa đi sửa lại).

### Tiến độ triển khai (2026-07-16)
- [x] Sprint 1–2: DocumentSession cutover cho content/typography/fill/stroke/effects; ViewModel `applyDocument`
- [x] Sprint 3: tắt dual-path `shapeFitFlow` debounce; display luôn `DocumentRenderPipeline`
- [x] Sprint 4: tab **Mẫu chữ** lên gần đầu; thêm 5 template; ẩn Elevation khi TextForm active
- [x] P0 gesture: `CommitTransform` / offset / rotate / flip nhãn qua `commitLabelGesture` + `BakeCornerScale`
- [x] P0 caret: `TextEditTapMapper` full-width vs wrap-content + paddingX; unit tests
- [x] P2 rich text: `EditorTextSpan` / `TextRunOps`, selection-scoped B/I/color, AnnotatedString + Spannable render
- [x] P2 panel: primary tabs + **Thêm**; mẫu Retro/Ocean; TextForm path hint
- [x] Bugfix (2026-07-16): caret không restyle cả chuỗi; inline reflow spans; BakeCornerScale giữ multi-run; hugHeight chỉ khi resize width; DocumentStore history off
- [x] Xóa `shapeFitFlow` / `skipShapeFit`; `withShapeFitted*` chỉ còn LayoutEngine + fallback sync
- [ ] P2 còn lại: path drag handles; polish font cloud UX

---

## Tầng 1 — Ổn định triệt để (P0)

### 1.1 Cutover toàn bộ mutation Nhãn → DocumentCommand

| Nhóm | Events | LayoutIntent |
|------|--------|--------------|
| Nội dung | `UpdateShapeText`, `InsertTextNewline`, Start/Finish text edit | EditText / InlineGrow |
| Typography | Bold/Italic/Underline/Align/FontFamily/CharSpacing/LineHeight | StyleOrCaseChange (giữ width) |
| Màu chữ / gradient chữ | `UpdateTextColor`, `UpdateTextColorGradient` | StyleOrCaseChange |
| Màu nền / fill opacity / stroke label | shape fill trên TEXT_ONLY | StyleOrCaseChange, **không** đổi box |
| Effects | shadow, elevation, elevation blur | StyleOrCaseChange + EffectMatrix |
| Gesture | edge resize, corner bake, commit transform | ResizeEdge / CornerScaleBake |
| Fit debounce | thay `shapeFitFlow` bằng measure sau command | — |

Gate: mỗi nhóm có unit test invariant (width preserve / height hug / group sync).

### 1.2 Xóa dual-path fit — DONE
- [x] Gỡ `shapeFitFlow` / `skipShapeFit` / `requestShapeFit`
- [x] `withShapeFitted*` chỉ còn `LayoutEngine` + sync trong LabelViewModelDelegate fallback
- [x] `DocumentArchitectureLint` cấm tái đưa dual-path

### 1.3 TextInShape binding cứng
- Gesture/bake → một command trên node nhóm
- Tool Khung → `NodePart.FRAME`, tool Nhãn → `NodePart.TEXT`
- Object list: 1 row cho TextInShape

### 1.4 Một metric render + caret
- Display luôn `DocumentRenderPipeline` (bỏ Compose `Text()` display path)
- Inline: `BasicTextField` + caret map align với engine
- Parity preview ≈ export

### 1.5 Regression pack
- Scenario: hoa/thường không đổi width; mẫu màu không đổi bbox; TextForm ⊥ Elevation; kéo khung không lệch chữ; inline grow đúng
- (Tuỳ chọn) 3–5 golden template load → export smoke

---

## Tầng 2 — UX & tính năng (P1, sau P0)

### 2.1 Panel gọn hơn
Thứ tự tab đề xuất:

`Sửa | Mẫu chữ | Phông | Cỡ | Định dạng | Căn | Màu | Nền | Hiệu ứng | Nghệ thuật | Khung nhãn`

- Đưa **Mẫu chữ** lên gần đầu; mở rộng 8–12 preset
- Ẩn tab nâng cao sau “Thêm” nếu cần

### 2.2 Inline edit Canva-like
- Caret đúng điểm chạm (regression test)
- Toolbar 55px / B / I ổn định
- Height hug khi commit; width preserve khi format
- Double-tap = edit; tap ngoài = commit

### 2.3 Hiệu ứng có rule trên UI
- TextForm bật → disable/ẩn Elevation + tooltip
- Độ mờ bóng / đổ bóng tách rõ trên copy UI
- Cường độ nghệ thuật + khoảng cách chữ giữ hành vi đã chốt

### 2.4 Mẫu chữ atomic
- Mọi preset → `ApplyStyleTemplate` (StyleBag full replace)
- Preview chip Canvas mini
- Không đổi text / box size

---

## Lộ trình sprint

| Sprint | Deliverable | DoD |
|--------|-------------|-----|
| **1** | Content + typography + transform qua DocumentSession | Case/font/align không phá width; tests xanh |
| **2** | Fill/color/effects/gestures qua commands | Màu nền không đổi bbox; TextForm⊥Elevation; group sync gesture |
| **3** | Xóa `shapeFitFlow`; render/caret unified | Không còn 2 nguồn fit; preview≈export |
| **4** | Panel reorder, thêm mẫu chữ, polish inline | UX checklist pass trên device |

---

## Việc không làm trong phase này
- Không big-bang xóa `EditorLayer`
- Không hotfix bbox rời rạc ngoài `LayoutIntent`
- Không mở rộng rich-text multi-run (để sau)

## Định nghĩa xong
- Mọi edit Nhãn đi `DocumentCommand` (I1)
- Box size chỉ từ `LayoutEngine` (I2)
- FRAME+LABEL luôn cùng transform/size (I3)
- Template/effect atomic + matrix (I4–I5)
- Một draw/caret metric (I6)
- Không còn class bug “đổi A → nhảy bbox / mất effect B” trên checklist regression

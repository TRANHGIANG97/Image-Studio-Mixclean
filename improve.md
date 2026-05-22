<!-- # Lo trinh nang app len muc 8.5/10

## 1. Danh gia hien trang

App hien da co bo chuc nang kha day du cho nhom chinh sua anh va xoa nen:

- Quick Tools tu Home.
- Xoa nen tu dong.
- Phan loai nguoi/vat trong luong Quick Tools de chon ModNet hoac ML Kit Subject Segmentation.
- Chi tiet toc.
- Nen anh voi mau, gradient, preset, anh tu thu vien.
- Crop, Text, Draw, Effects, Rotate, Magic Brush.
- Batch remove, Drafts, Studio.

Ve mat san pham, app da dat muc co the dung duoc va co diem khac biet. Tuy nhien, de dat muc 8.5/10 tren CH Play, app can nang manh phan ky thuat production: do on dinh, hieu nang, memory, test thiet bi that, release hygiene va chat luong UI/UX o cac luong bien.

Muc diem hien tai uoc tinh: 6.5/10.

Muc tieu: 8.5/10.

## 2. Tieu chi dat 8.5/10

App chi nen duoc xem la dat muc 8.5/10 khi thoa cac dieu kien sau:

- Crash rate muc tieu noi bo duoi 0.5%.
- User-perceived ANR rate muc tieu duoi 0.2%.
- Khong crash tren cac anh lon pho bien: 12MP, 24MP, anh portrait, anh PNG alpha, anh tu camera.
- Xoa nen khong bi treo loading, khong giu preview cu, khong mat toolbar.
- Cac tac vu nang khong block main thread.
- Su dung bo nho co kiem soat, khong tao nhieu bitmap trung gian khong can thiet.
- Co test thiet bi that tren may RAM thap va Android version cu.
- Co Crashlytics hoac cong cu tuong duong de bat crash/ANR production.
- Release config sach: khong hardcode signing password trong repo, ProGuard/R8 duoc test.
- UI cac luong chinh on dinh, khong co text tran, loading thua, dialog sai ngu canh.

## 3. Uu tien 1: Do on dinh va crash/ANR

Day la nhom quan trong nhat. Chuc nang AI/xoa nen cua app nang, neu khong kiem soat se de gay crash tren may yeu.

Viec can lam:

- Tich hop Firebase Crashlytics.
- Ghi custom keys cho cac luong nang:
  - tool dang dung: quick_tools_remove_bg, manual_remove_bg, hair_detail, background_mode, batch_remove, studio.
  - kich thuoc bitmap dau vao.
  - kich thuoc bitmap dau ra.
  - remover duoc chon: modnet, mlkit_subject, mlkit_fallback.
  - thiet bi, RAM class neu co.
- Bat va phan loai loi:
  - OutOfMemoryError.
  - IllegalStateException tu bitmap recycled/config sai.
  - ML Kit model unavailable.
  - ONNX session error.
  - Uri decode fail.
- Tao helper an toan cho cac tac vu bitmap:
  - kiem tra bitmap recycled.
  - ep ARGB_8888 khi can.
  - scale dau vao theo nguong bo nho.
  - recycle bitmap trung gian dung luc.
- Dam bao moi tac vu nang chay o Dispatchers.Default hoac IO, khong chay tren main thread.

Nghiem thu:

- Mo app, chon anh, xoa nen, chi tiet toc, nen anh, save/share khong ANR tren may RAM 3GB.
- Chay 20 lan lien tiep Quick Tools xoa nen khong crash.
- Chay 10 anh lien tiep batch remove khong treo UI.

## 4. Uu tien 2: Toi uu memory bitmap

Rui ro lon nhat hien tai la OOM do tao nhieu ban copy bitmap. Cac luong can soi ky:

- QuickEditActivity khi load anh va add vao stack.
- Xoa nen ML Kit.
- Xoa nen ModNet.
- BackgroundMode khi composite foreground/background.
- Studio effects.
- BatchRemove.
- Save/share.

Viec can lam:

- Dinh nghia nguong xu ly theo MemoryUtil:
  - preview max side.
  - processing max side.
  - export max side neu can.
- Giam copy bitmap khong can thiet:
  - chi copy ARGB_8888 khi config khong phu hop.
  - tranh copy roi lai trim roi lai copy tiep.
- Quan ly stack undo/redo:
  - gioi han so luong bitmap trong stack.
  - can nhac luu cache file thay vi giu tat ca bitmap full trong RAM.
- Kiem tra cac noi tao Bitmap.createBitmap lon.
- Them log memory truoc/sau cac tac vu nang trong debug build.
- Voi batch remove, xu ly tung anh mot, giai phong bitmap ngay sau khi save/cache.

Nghiem thu:

- Anh 4000x3000 khong crash khi xoa nen.
- Anh PNG alpha lon khong crash khi vao BackgroundMode.
- Undo/redo nhieu lan khong tang RAM vo han.
- Batch 10 anh khong OOM tren may RAM 4GB.

## 5. Uu tien 3: Kiem thu luong chinh

Can co checklist test tay va test tu dong toi thieu.

Luong Quick Tools Home:

- Chon anh chan dung ro mat: hien "Dang tim khuon mat", xac dinh co khuon mat, dung ModNet.
- Chon anh vat/san pham: xac dinh khong co khuon mat, dung ML Kit Subject.
- Chon anh nguoi che mat/quay lung: fallback Subject, khong fail.
- ML Kit Face Detection loi: fallback Subject.
- ModNet loi: fallback Subject.
- Sau khi xoa nen thanh cong: preview QuickEdit hien dung anh moi.
- Back sau khi thanh cong: khong hien dialog sai ngu canh neu undo stack dang hop ly.

Luong QuickEdit:

- Manual RemoveBg khong bi anh huong boi logic Quick Tools.
- Chi tiet toc xu ly xong tu dong cap nhat preview.
- Nen anh keo/zoom/xoay khop preview va final.
- Crop/Text/Draw/Effects/Rotate/Magic Brush van navigate dung.
- Toolbar khong bien mat sau cac tool xu ly truc tiep.

Luong save/share/draft:

- Save anh co alpha.
- Save anh nen mau/gradient.
- Share file sau edit.
- Tao draft, mo lai draft, edit tiep.

Nghiem thu:

- Tao test matrix gom toi thieu 30 case.
- Moi release candidate phai chay het matrix.
- Bat loi regression bang issue/task rieng, khong sua lan trong luc release.

## 6. Uu tien 4: UX va loading

App da dung Lottie cho cac trang thai loading. Can lam cho loading ro rang hon nhung khong gay nhieu chu thua.

Viec can lam:

- Chi hien text loading o cac luong can feedback theo buoc, vi du Quick Tools xoa nen.
- Cac loading ngan hoac khong can giai thich chi hien Lottie.
- Them timeout bao loi neu tac vu ML/ONNX qua lau.
  - Quick Tools xoa nen da co timeout 10 giay cho face detection va 45 giay cho remove/fallback.
- Nut back trong luc dang xu ly:
  - Hoac chan back co thong bao.
  - Hoac cancel tac vu neu co the.
- Loading overlay phai chan thao tac nen tranh user bam nhieu lan.
- Toast loi can ro nghia hon:
  - "Khong the nhan dien anh nay, vui long thu anh khac."
  - "Thiet bi khong ho tro tac vu nay, da thu cach xu ly khac."

Nghiem thu:

- Khong con text vo nghia nhu "Dang xu ly..." o cac noi khong can.
- Quick Tools hien dung 3 giai doan: tim khuon mat, ket qua khuon mat, xoa phong.
- Loi khong lam loading treo mai.

## 7. Uu tien 5: Release hygiene va bao mat

Day la phan bat buoc neu muon day len CH Play nghiem tuc.

Viec can lam:

- Xoa signing password hardcode khoi repo.
- Dua signing config sang local properties, environment variable hoac CI secret.
- Kiem tra ProGuard/R8 release build voi:
  - ML Kit.
  - ONNX Runtime.
  - Hilt.
  - Lottie.
  - Navigation Compose.
- Tao file keystore rieng, khong commit vao repo.
- Kiem tra Data Safety tren Play Console:
  - quyen doc anh.
  - camera neu co chup anh.
  - ads.
  - billing.
  - analytics/crash reporting neu them Crashlytics.
- Kiem tra privacy policy khop voi SDK dang dung.

Nghiem thu:

- Build release thanh cong.
- Cai release APK/AAB len may that va test cac luong chinh.
- Khong co secret trong git diff.

## 8. Uu tien 6: Hieu nang va kich thuoc app

App dung nhieu SDK nang. Can do va toi uu.

Viec can lam:

- Do cold start time.
- Do thoi gian xoa nen:
  - ML Kit Subject.
  - ModNet.
  - Face Detection.
- Do dung luong AAB/APK.
- Kiem tra native libs va ABI.
- Lazy initialize cac model nang:
  - ModNet chi init khi can.
  - ML Kit client dung singleton neu hop ly.
- Cache model/session co kiem soat.
- Giai phong session khi app destroy neu can.

Muc tieu:

- Home hien trong duoi 2 giay tren may trung binh.
- Quick Tools xoa nen anh 1080p trong khoang chap nhan duoc.
- Khong giat UI khi model dang xu ly.

## 9. Uu tien 7: Chat luong UI/UX san pham

Chuc nang tot nhung can lam trai nghiem gon va dang tin hon.

Viec can lam:

- Dong bo ngon ngu, tranh hardcoded string trong Kotlin.
- Sua encoding loi trong comment/text cu neu anh huong hien thi.
- Review tat ca bottom toolbar label/icon.
- Dam bao text khong tran tren man hinh nho.
- Kiem tra dark mode/light mode.
- Cai thien empty/error states.
- Them huong dan nhe cho cac tool kho dung, nhung khong lam man hinh day chu.

Nghiem thu:

- Test UI tren 360x800, 390x844, tablet nho.
- Khong co text overlap.
- Khong co toolbar che noi dung quan trong.

## 10. Uu tien 8: Kien truc va no ky thuat

Mot so file dang gom nhieu logic. Can tach dan de de bao tri.

Viec can lam:

- Tach logic auto remove Quick Tools khoi QuickEditActivity.
- Tao use case rieng:
  - DetectPortraitUseCase.
  - SmartRemoveBackgroundUseCase.
  - PrepareBitmapForEditUseCase.
- Chuyen hardcoded status text sang resources.
- Giam log debug trong release.
- Chuan hoa error handling bang sealed class.
- Tao module/interface cho background remover:
  - ML Kit Subject remover.
  - ModNet remover.
  - Smart router remover.

Nghiem thu:

- QuickEditActivity ngan hon, chi dieu phoi UI/navigation.
- Unit test duoc SmartRemoveBackgroundUseCase.
- Khong lap logic trim/copy alpha o nhieu noi.

## 11. Ke hoach 4 giai doan

### Giai doan 1: On dinh truoc khi release rong

Thoi gian du kien: 3-5 ngay.

- Tich hop Crashlytics.
- Doi signing config ra khoi repo.
- Them timeout/cancel cho tac vu xoa nen.
- Tao checklist test tay.
- Test Quick Tools, RemoveBg, HairDetail, BackgroundMode tren may that.

Ket qua mong doi: giam rui ro crash/treo ro rang.

### Giai doan 2: Memory va performance

Thoi gian du kien: 5-8 ngay.

- Audit toan bo bitmap copy.
- Gioi han undo/redo stack.
- Toi uu batch remove.
- Do RAM, thoi gian xu ly, APK/AAB size.
- Sua cac diem OOM tiem an.

Ket qua mong doi: app chay duoc tren may RAM 3-4GB on dinh hon.

### Giai doan 3: UX polish

Thoi gian du kien: 4-6 ngay.

- Chuyen hardcoded string sang resources.
- Review loading/error states.
- Kiem tra UI tren nhieu kich thuoc man hinh.
- Sua cac text/toolbar/dialog sai ngu canh.
- Lam icon/label dong bo hon.

Ket qua mong doi: app cam giac gan san pham thuong mai hon.

### Giai doan 4: Test release va Play readiness

Thoi gian du kien: 3-5 ngay.

- Build release.
- Chay pre-launch report.
- Test tren Firebase Test Lab hoac may that.
- Kiem tra Data Safety, privacy policy, permissions.
- Chay regression matrix lan cuoi.

Ket qua mong doi: san sang dua len CH Play voi rui ro thap hon.

## 12. Checklist truoc khi cham 8.5/10

- [x] Crashlytics da hoat dong. app/google-services.json da duoc dat dung cho package com.thgiang.image, build da chay processDebugGoogleServices va injectCrashlytics.
- [x] Khong co signing password trong repo.
- [ ] Release build cai duoc tren may that.
- [ ] Quick Tools xoa nen test du 4 case nguoi/vat/fallback/loi.
- [ ] Manual RemoveBg khong bi anh huong.
- [ ] HairDetail cap nhat preview dung.
- [ ] BackgroundMode preview khop final.
- [ ] Batch remove khong OOM voi 10 anh.
- [ ] Undo/redo khong lam RAM tang vo han.
- [ ] Save/share anh co alpha thanh cong.
- [ ] Draft tao va mo lai dung.
- [ ] Khong co loading treo qua lau.
- [ ] Khong co text loading vo nghia o cac luong khong can.
- [ ] Khong co crash tren may RAM 3GB trong test tay.
- [ ] Pre-launch report khong co loi nghiem trong.
- [ ] Data Safety va privacy policy khop SDK dang dung.

## 13. Ket luan

App hien co nen tang chuc nang tot, nhung diem yeu nam o do ben ky thuat khi xu ly anh va AI. De len 8.5/10, khong nen them nhieu feature moi ngay lap tuc. Nen uu tien on dinh, memory, crash/ANR, release hygiene va test thiet bi that.

Huong di dung la: giu feature hien co, lam chung chac hon, do duoc loi, toi uu bitmap, va kiem thu cac luong nang den khi app khong con mong manh tren may yeu. -->

@echo off
set BUNDLETOOL=%~dp0bundletool-all-1.18.3.jar
set AAB_PATH=%~dp0app-release.aab
set APKS_PATH=%~dp0app-release.apks
set KEYSTORE_PATH=%~dp0..\app\my-release-key.jks
set KEYSTORE_PASS=135798
set KEY_ALIAS=my-key-alias
set KEY_PASS=135798

echo ========================================================
echo   MIXCLEAN - TU DONG CAI DAT AAB LEN MAY THAT
echo ========================================================
echo.

:: 1. Kiem tra moi truong Java
java -version >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong tim thay moi truong Java [JRE/JDK] tren may tinh!
    echo Vui long cai dat Java va them vao bien moi truong PATH de chay duoc bundletool.
    echo.
    if not defined ORCHESTRATOR pause
    exit /b 1
)

:: 2. Kiem tra file AAB nguon
echo [1/3] Kiem tra file AAB...
if not exist "%AAB_PATH%" (
    echo [ERROR] Khong tim thay file AAB tai: %AAB_PATH%
    echo [HUONG DAN] Vui long nhap dup file 'build_aab_release.bat' truoc de tao file AAB.
    echo.
    if not defined ORCHESTRATOR pause
    exit /b 1
)

:: 3. Kiem tra Keystore
if not exist "%KEYSTORE_PATH%" (
    echo [ERROR] Khong tim thay file Keystore tai: %KEYSTORE_PATH%
    echo.
    if not defined ORCHESTRATOR pause
    exit /b 1
)

:: 4. Xay dung file APKS tu AAB
echo [2/3] Dang trich xuat file APKS tu AAB...
if exist "%APKS_PATH%" del "%APKS_PATH%"

java -jar "%BUNDLETOOL%" build-apks ^
  --bundle="%AAB_PATH%" ^
  --output="%APKS_PATH%" ^
  --ks="%KEYSTORE_PATH%" ^
  --ks-pass=pass:%KEYSTORE_PASS% ^
  --ks-key-alias=%KEY_ALIAS% ^
  --key-pass=pass:%KEY_PASS% ^
  --overwrite

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Khong the trich xuat file APKS tu file AAB!
    echo Nguyen nhan co the do:
    echo   - File Keystore hoac mat khau sai.
    echo   - Phien ban Java khong tuong thich.
    echo   - File AAB bi loi.
    echo.
    if not defined ORCHESTRATOR pause
    exit /b 1
)

:: 5. Cai dat APKS len may that qua adb install-multiple (de hien thi phan tram)
echo [3/3] Dang tien hanh cai dat len thiet bi [Hien thi phan tram]...
echo (Dam bao dien thoai da bat USB Debugging va dang ket noi)
echo.

:: Lay device-spec tu thiet bi dang ket noi qua ADB
java -jar "%BUNDLETOOL%" get-device-spec --output="%~dp0device.json"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong the ket noi voi thiet bi qua ADB de lay thong tin cau hinh.
    echo Vui long kiem tra lai cap ket noi hoac Wifi Debugging.
    echo.
    if not defined ORCHESTRATOR pause
    exit /b 1
)

:: Tao thu muc tam de extract cac split APKs tuong thich
if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
md "%~dp0extracted_apks"

java -jar "%BUNDLETOOL%" extract-apks ^
  --apks="%APKS_PATH%" ^
  --output-dir="%~dp0extracted_apks" ^
  --device-spec="%~dp0device.json"

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong the trich xuat split APKs phu hop cho thiet bi.
    if exist "%~dp0device.json" del "%~dp0device.json"
    if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
    if not defined ORCHESTRATOR pause
    exit /b 1
)

:: Don dep device.json tam thoi
if exist "%~dp0device.json" del "%~dp0device.json"

:: Quet tat ca cac file apk da trich xuat de tao danh sach truyen vao adb
set APK_LIST=
for %%f in ("%~dp0extracted_apks\*.apk") do (
    call set APK_LIST=%%APK_LIST%% "%%f"
)

:: Thuc thi adb install-multiple de hien thi % tien trinh gui file sang dien thoai
adb install-multiple %APK_LIST%

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Cai dat ung dung that bai!
    echo.
    echo CAC NGUYEN NHAN PHO BIEN:
    echo   - Xung dot chu ky [Signature Conflict]: Tren may da co phien ban voi chu ky khac.
    echo   - Chua bat USB Debugging / Wifi Debugging.
    echo   - Het dung luong bo nho thiet bi.
    echo.

    set /p UNINSTALL_CHOICE="Ban co muon go cai dat phien ban cu com.thgiang.image de thu lai? [Y/N]: "
    if /i "%UNINSTALL_CHOICE%"=="Y" (
        echo.
        echo Dang tien hanh go cai dat com.thgiang.image...
        adb uninstall com.thgiang.image
        echo.
        echo Dang tien hanh cai dat lai ung dung...
        adb install-multiple %APK_LIST%
        if %ERRORLEVEL% equ 0 (
            echo.
            echo ========================================================
            echo   === CAI DAT THANH CONG SAU KHI GO BAN CU! ===
            echo ========================================================
            echo.
            if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
            if not defined ORCHESTRATOR pause
            exit /b 0
        ) else (
            echo [ERROR] Van khong the cai dat ung dung sau khi go.
        )
    )
    
    if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
    echo.
    if not defined ORCHESTRATOR pause
    exit /b 1
)

:: Xoa thu muc tam sau khi cai dat hoan tat thanh cong
if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"

echo.
echo ========================================================
echo   === CAI DAT THANH CONG! ===
echo   Da cai dat ban gia lap tuong thich CH Play len may.
echo ========================================================
echo.
if not defined ORCHESTRATOR pause

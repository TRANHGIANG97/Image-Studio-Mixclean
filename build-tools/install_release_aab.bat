@echo off
set BUNDLETOOL=%~dp0bundletool-all-1.18.3.jar
set AAB_PATH=%~dp0app-release.aab
set APKS_PATH=%~dp0app-release.apks
set KEYSTORE_PATH=%~dp0..\app\my-release-key.jks
set KEYSTORE_PASS=135798
set KEY_ALIAS=my-key-alias
set KEY_PASS=135798

:START_INSTALL
cls
echo ========================================================
echo   MIXCLEAN - TU DONG CAI DAT AAB LEN MAY THAT
echo ========================================================
echo.

:: 1. Build AAB moi ghi de
echo [1/5] Dang tu dong build AAB moi...
set ORCHESTRATOR=1
call "%~dp0build_aab_release.bat"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Build AAB moi that bai! Khong the tiep tuc.
    goto ERROR_HANDLER
)

:: 2. Kiem tra moi truong Java
echo [2/5] Kiem tra moi truong Java...
java -version >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong tim thay moi truong Java [JRE/JDK] tren may tinh!
    echo Vui long cai dat Java va them vao bien moi truong PATH de chay duoc bundletool.
    echo.
    goto ERROR_HANDLER
)

:: 3. Kiem tra file AAB nguon
echo [3/5] Kiem tra file AAB va Keystore...
if not exist "%AAB_PATH%" (
    echo [ERROR] Khong tim thay file AAB tai: %AAB_PATH%
    echo.
    goto ERROR_HANDLER
)

:: Kiem tra Keystore
if not exist "%KEYSTORE_PATH%" (
    echo [ERROR] Khong tim thay file Keystore tai: %KEYSTORE_PATH%
    echo.
    goto ERROR_HANDLER
)

:: 4. Xay dung file APKS tu AAB
echo [4/5] Dang trich xuat file APKS tu AAB...
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
    goto ERROR_HANDLER
)

:: 5. Cai dat APKS len may that qua adb install-multiple
echo [5/5] Dang tien hanh cai dat len thiet bi [Hien thi phan tram]...
echo (Dam bao dien thoai da bat USB Debugging va dang ket noi)
echo.

java -jar "%BUNDLETOOL%" get-device-spec --output="%~dp0device.json"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong the ket noi voi thiet bi qua ADB de lay thong tin cau hinh.
    echo Vui long kiem tra lai cap ket noi hoac Wifi Debugging.
    echo.
    goto ERROR_HANDLER
)

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
    goto ERROR_HANDLER
)

if exist "%~dp0device.json" del "%~dp0device.json"

set APK_LIST=
for %%f in ("%~dp0extracted_apks\*.apk") do (
    call set APK_LIST=%%APK_LIST%% "%%f"
)

adb install-multiple -r %APK_LIST%

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Cai dat ung dung that bai!
    echo.
    set /p UNINSTALL_CHOICE="Ban co muon go cai dat phien ban cu com.thgiang.image de thu lai? [Y/N]: "
    if /i "%UNINSTALL_CHOICE%"=="Y" (
        echo.
        echo Dang tien hanh go cai dat com.thgiang.image...
        adb uninstall com.thgiang.image
        echo.
        echo Dang tien hanh cai dat lai ung dung...
        adb install-multiple -r %APK_LIST%
        if %ERRORLEVEL% equ 0 (
            echo.
            echo ========================================================
            echo   === CAI DAT THANH CONG SAU KHI GO BAN CU! ===
            echo ========================================================
            echo.
            if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
            goto SUCCESS_HANDLER
        ) else (
            echo [ERROR] Van khong the cai dat ung dung sau khi go.
        )
    )
    
    if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
    goto ERROR_HANDLER
)

if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"

echo.
echo ========================================================
echo   === CAI DAT THANH CONG! ===
echo   Da cai dat ban gia lap tuong thich CH Play len may.
echo ========================================================
echo.

:SUCCESS_HANDLER
if not defined ORCHESTRATOR (
    echo [1] Quay lai thu lai / cai dat lai
    echo [2] Thoat
    echo ========================================================
    set /p SUCCESS_CHOICE="Vui long chon [1-2]: "
    if "!SUCCESS_CHOICE!"=="1" goto START_INSTALL
)
exit /b 0

:ERROR_HANDLER
if defined ORCHESTRATOR (
    exit /b 1
)
echo.
echo ========================================================
echo   [MENU LUA CHON KHI LOI]
echo --------------------------------------------------------
echo   [1] Thu lai
echo   [2] Thoat
echo ========================================================
echo.
set /p ERROR_CHOICE="Vui long chon hanh dong [1-2]: "
if "%ERROR_CHOICE%"=="1" (
    goto START_INSTALL
)
exit /b 1

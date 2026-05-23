@echo off
setlocal enabledelayedexpansion

set BUNDLETOOL=%~dp0bundletool-all-1.18.3.jar
set AAB_PATH=%~dp0app-release.aab
set APKS_PATH=%~dp0app-release.apks
set KEYSTORE_PATH=%~dp0..\app\my-release-key.jks
set KEYSTORE_PASS=135798
set KEY_ALIAS=my-key-alias
set KEY_PASS=135798

:MENU
cls
echo ========================================================
echo   MIXCLEAN - TRINH DIEU KHIEN DONG GOI VA CAI DAT AAB/APK
echo ========================================================
echo   [1] Build AAB Release (Ghi de file hien tai)
echo   [2] Trich xuat APKS tu AAB (Ghi de file hien tai)
echo   [3] Cai dat APKS hien co len thiet bi (Clean Install)
echo   [4] Tu dong (Chay toan bo quy trinh tu 1 den 3)
echo   [5] Thoat
echo ========================================================
echo.
set /p MENU_CHOICE="Vui long chon so [1-5]: "

if "%MENU_CHOICE%"=="1" goto RUN_BUILD
if "%MENU_CHOICE%"=="2" goto RUN_EXTRACT
if "%MENU_CHOICE%"=="3" goto RUN_INSTALL
if "%MENU_CHOICE%"=="4" goto RUN_AUTO
if "%MENU_CHOICE%"=="5" exit /b 0
goto MENU

:RUN_BUILD
cls
echo ========================================================
echo   [1/3] DANG TIEN HANH BUILD AAB RELEASE MOI...
echo ========================================================
echo.
set ORCHESTRATOR=1
call "%~dp0build_aab_release.bat"
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build AAB moi that bai!
    echo.
    pause
    goto MENU
)
echo.
echo === BUILD AAB RELEASE THANH CONG! ===
echo.
pause
goto MENU

:RUN_EXTRACT
cls
echo ========================================================
echo   [2/3] DANG TIEN HANH TRICH XUAT APKS TU FILE AAB...
echo ========================================================
echo.
:: Kiem tra Java
java -version >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong tim thay moi truong Java [JRE/JDK]!
    echo Vui long cai dat Java va them vao bien moi truong PATH.
    echo.
    pause
    goto MENU
)
:: Kiem tra file AAB
if not exist "%AAB_PATH%" (
    echo [ERROR] Khong tim thay file AAB tai: %AAB_PATH%
    echo Vui long chay lua chon [1] de tao file AAB truoc.
    echo.
    pause
    goto MENU
)
:: Kiem tra Keystore
if not exist "%KEYSTORE_PATH%" (
    echo [ERROR] Khong tim thay file Keystore tai: %KEYSTORE_PATH%
    echo.
    pause
    goto MENU
)

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
    echo.
    pause
    goto MENU
)
echo.
echo === TRICH XUAT APKS THANH CONG! ===
echo File APKS moi luu tai: %APKS_PATH%
echo.
pause
goto MENU

:RUN_INSTALL
cls
echo ========================================================
echo   [3/3] DANG TIEN HANH CAI DAT APKS LEN THIET BI...
echo ========================================================
echo.
:: Kiem tra APKS hien tai
if not exist "%APKS_PATH%" (
    echo [ERROR] Khong tim thay file APKS tai: %APKS_PATH%
    echo Vui long chay lua chon [2] de tao file APKS truoc.
    echo.
    pause
    goto MENU
)

:: Xoa file device.json cu neu ton tai de tranh loi ghi de cua bundletool
if exist "%~dp0device.json" del "%~dp0device.json"

:: Lay thong so thiet bi qua ADB
java -jar "%BUNDLETOOL%" get-device-spec --output="%~dp0device.json"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong the ket noi voi thiet bi qua ADB de lay cau hinh.
    echo Vui long kiem tra lai cap ket noi USB / WiFi Debugging.
    echo.
    if exist "%~dp0device.json" del "%~dp0device.json"
    pause
    goto MENU
)

if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
md "%~dp0extracted_apks"

java -jar "%BUNDLETOOL%" extract-apks ^
  --apks="%APKS_PATH%" ^
  --output-dir="%~dp0extracted_apks" ^
  --device-spec="%~dp0device.json"

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Trich xuat Split APKs tuong thich cho thiet bi that bai.
    if exist "%~dp0device.json" del "%~dp0device.json"
    if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
    echo.
    pause
    goto MENU
)

if exist "%~dp0device.json" del "%~dp0device.json"

set APK_LIST=
for %%f in ("%~dp0extracted_apks\*.apk") do (
    call set APK_LIST=%%APK_LIST%% "%%f"
)

echo Dang dung tien trinh cu cua com.thgiang.image...
adb shell am force-stop com.thgiang.image >nul 2>nul

echo Go bo sach phien ban cu cho tat ca nguoi dung (Clean Uninstall)...
adb uninstall --user all com.thgiang.image

echo Dang tien hanh cai dat bo APKs moi...
adb install-multiple %APK_LIST%

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Cai dat cac tep APK that bai!
    if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
    echo.
    pause
    goto MENU
)

if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
echo.
echo ========================================================
echo   === CAI DAT THANH CONG! ===
echo   Ung dung moi da duoc nap sach se len thiet bi cua ban.
echo ========================================================
echo.
pause
goto MENU

:RUN_AUTO
cls
echo ========================================================
echo   CHAY TU DONG TOAN BO QUY TRINH [BUILD -> TRICH -> CAI]
echo ========================================================
echo.

:: 1. Build AAB moi
set ORCHESTRATOR=1
call "%~dp0build_aab_release.bat"
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Buoc 1 [Build AAB] that bai! dung quy trinh.
    echo.
    pause
    goto MENU
)

:: 2. Kiem tra Java & Trich xuat APKS
java -version >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong tim thay Java de chay bundletool.
    echo.
    pause
    goto MENU
)
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
    echo [ERROR] Buoc 2 [Trich xuat APKS] that bai! dung quy trinh.
    echo.
    pause
    goto MENU
)

:: 3. Ket noi & Cai dat APKs
if exist "%~dp0device.json" del "%~dp0device.json"
java -jar "%BUNDLETOOL%" get-device-spec --output="%~dp0device.json"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Buoc 3 [Ket noi thiet bi qua ADB] that bai! dung quy trinh.
    echo.
    if exist "%~dp0device.json" del "%~dp0device.json"
    pause
    goto MENU
)
if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
md "%~dp0extracted_apks"
java -jar "%BUNDLETOOL%" extract-apks ^
  --apks="%APKS_PATH%" ^
  --output-dir="%~dp0extracted_apks" ^
  --device-spec="%~dp0device.json"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Buoc 3 [Giai nen APKs tuong thich] that bai! dung quy trinh.
    if exist "%~dp0device.json" del "%~dp0device.json"
    if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
    echo.
    pause
    goto MENU
)
if exist "%~dp0device.json" del "%~dp0device.json"

set APK_LIST=
for %%f in ("%~dp0extracted_apks\*.apk") do (
    call set APK_LIST=%%APK_LIST%% "%%f"
)

echo Dang dung tien trinh cu cua com.thgiang.image...
adb shell am force-stop com.thgiang.image >nul 2>nul
echo Go bo sach phien ban cu cho tat ca nguoi dung (Clean Uninstall)...
adb uninstall --user all com.thgiang.image
echo Dang tien hanh cai dat bo APKs moi...
adb install-multiple %APK_LIST%
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Buoc 3 [Cai dat APKs len may] that bai!
    if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"
    echo.
    pause
    goto MENU
)
if exist "%~dp0extracted_apks" rd /s /q "%~dp0extracted_apks"

echo.
echo ========================================================
echo   === TAT CA QUY TRINH HOAN THANH TU DONG THANH CONG! ===
echo ========================================================
echo.
pause
goto MENU

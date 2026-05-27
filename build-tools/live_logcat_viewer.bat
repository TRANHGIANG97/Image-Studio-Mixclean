@echo off
setlocal enabledelayedexpansion

:CHECK_ADB
cls
echo ========================================================
echo   MIXCLEAN - TRINH XEM NHAT KY DONG HOC LOI [LIVE LOGCAT]
echo ========================================================
echo.

:: 1. Kiem tra adb co san trong he thong hay khong
adb version >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong tim thay cong cu ADB trong bien moi truong PATH!
    echo Vui long dam bao ban da cai dat Android SDK va cau hinh bien moi truong cho adb.exe.
    echo.
    echo ========================================================
    echo   [LUA CHON KHI LOI]
    echo --------------------------------------------------------
    echo   [1] Kiem tra lai - Thu lai
    echo   [2] Thoat
    echo ========================================================
    set /p ADB_ERROR_CHOICE="Vui long chon [1-2, Mac dinh la 1]: "
    if "!ADB_ERROR_CHOICE!"=="" set ADB_ERROR_CHOICE=1
    if "!ADB_ERROR_CHOICE!"=="1" goto CHECK_ADB
    exit /b 1
)

:CHECK_DEVICE
:: 2. Kiem tra thiet bi ket noi
adb get-state >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [THONG BAO] Khong phat hien dien thoai Android nao dang ket noi.
    echo Dang cho thiet bi Android duoc ket noi va kich hoat USB Debugging...
    adb wait-for-device
    echo [OK] Thiet bi da ket noi thanh cong!
    timeout /t 1 >nul
)

:MENU
cls
echo ========================================================
echo   MIXCLEAN - TRINH XEM NHAT KY DONG HOC LOI [LIVE LOGCAT]
echo ========================================================
echo  Package ID: com.thgiang.image
echo --------------------------------------------------------
echo  [1] Xem LOG LOI [Crash / Errors Only] cua MixClean
echo  [2] Xem TOAN BO LOG [All Levels] cua MixClean
echo  [3] Xem LOG loc theo tag tinh nang Xoa nen [AI / UI Editor]
echo  [4] Xoa sach bo nho dem log cu tren dien thoai [Clear Buffer]
echo  [5] Thoat
echo ========================================================
echo.

set /p CHOICE="Vui long chon chuc nang [1-5]: "

if "%CHOICE%"=="1" goto VIEW_ERROR
if "%CHOICE%"=="2" goto VIEW_ALL
if "%CHOICE%"=="3" goto VIEW_TAGS
if "%CHOICE%"=="4" goto CLEAR_LOGS
if "%CHOICE%"=="5" exit /b 0

echo [ERROR] Lua chon khong hop le. Vui long nhap tu 1 den 5.
pause
goto MENU

:GET_PID
:: Lay Process ID (PID) hien tai cua app MixClean tren thiet bi
set PID=
for /f "tokens=*" %%p in ('adb shell pidof -s com.thgiang.image') do set PID=%%p

if "%PID%"=="" (
    echo.
    echo [THONG BAO] App MixClean hien dang khong chay tren dien thoai.
    set /p LAUNCH_CHOICE="Ban co muon tu dong mo app de tiep tuc theo doi? [Y/N, Mac dinh la Y]: "
    if "!LAUNCH_CHOICE!"=="" set LAUNCH_CHOICE=Y
    if /i "!LAUNCH_CHOICE!"=="Y" (
        echo Dang gui tin hieu khoi dong app MixClean...
        adb shell monkey -p com.thgiang.image -c android.intent.category.LAUNCHER 1 >nul 2>nul
        timeout /t 2 >nul
        for /f "tokens=*" %%p in ('adb shell pidof -s com.thgiang.image') do set PID=%%p
    )
)
goto :EOF

:VIEW_ERROR
cls
echo ========================================================
echo   LOGCAT: XEM LOG LOI [CRASH/ERROR] DANG TRUYEN LIVE...
echo   [Script se tu dong doi va ket noi lai neu mat ket noi]
echo   [Nhan Ctrl+C de dung logcat va quay lai Menu bat ky luc nao]
echo ========================================================
echo.

:LOOP_ERROR
call :GET_PID
if "!PID!"=="" (
    echo [CANH BAO] Khong the lay PID cua app. Chuyen sang loc chuoi com.thgiang.image...
    adb logcat *:E | findstr /i "com.thgiang.image"
) else (
    adb logcat *:E --pid=!PID! -v color
)
echo.
echo [!] Mat ket noi hoac thiet bi ngat. Dang doi thiet bi ket noi lai...
adb wait-for-device
echo [!] Thiet bi da online tro lai! Dang lay lai PID va tiep tuc logcat...
echo.
goto LOOP_ERROR

:VIEW_ALL
cls
echo ========================================================
echo   LOGCAT: XEM TOAN BO LOG DANG TRUYEN LIVE...
echo   [Script se tu dong doi va ket noi lai neu mat ket noi]
echo   [Nhan Ctrl+C de dung logcat va quay lai Menu bat ky luc nao]
echo ========================================================
echo.

:LOOP_ALL
call :GET_PID
if "!PID!"=="" (
    echo [CANH BAO] Khong the lay PID cua app. Chuyen sang loc chuoi com.thgiang.image...
    adb logcat | findstr /i "com.thgiang.image"
) else (
    adb logcat --pid=!PID! -v color
)
echo.
echo [!] Mat ket noi hoac thiet bi ngat. Dang doi thiet bi ket noi lai...
adb wait-for-device
echo [!] Thiet bi da online tro lai! Dang lay lai PID va tiep tuc logcat...
echo.
goto LOOP_ALL

:VIEW_TAGS
cls
echo ========================================================
echo   LOGCAT: LOC LOG THEO CAC TAG EDITING / AI / REMOVER...
echo   [Script se tu dong doi va ket noi lai neu mat ket noi]
echo   [Nhan Ctrl+C de dung logcat va quay lai Menu bat ky luc nao]
echo ========================================================
echo.

:LOOP_TAGS
adb logcat -v color *:S MlKitBackgroundRemoverRepository:V ModNetBackgroundRemoverRepository:V BoundingBoxOverlay:V ThemeplateEditorViewModel:V BatchRemoveViewModel:V
echo.
echo [!] Mat ket noi hoac thiet bi ngat. Dang doi thiet bi ket noi lai...
adb wait-for-device
echo [!] Thiet bi da online tro lai! Tiep tuc logcat...
echo.
goto LOOP_TAGS

:CLEAR_LOGS
cls
echo ========================================================
echo   DANG XOA SACH BO NHO DEM LOG TREN THIET BI...
echo ========================================================
echo.
adb logcat -c
if %ERRORLEVEL% equ 0 (
    echo [OK] Da xoa sach lich su log trong he thong dien thoai.
) else (
    echo [ERROR] Xoa buffer log that bai. Vui long kiem tra lai thiet bi.
)
echo.
echo ========================================================
echo   [1] Quay lai Menu chinh
echo   [2] Thoat
echo ========================================================
set /p CLEAR_CHOICE="Vui long chon [1-2, Mac dinh la 1]: "
if "!CLEAR_CHOICE!"=="" set CLEAR_CHOICE=1
if "!CLEAR_CHOICE!"=="1" goto MENU
exit /b 0

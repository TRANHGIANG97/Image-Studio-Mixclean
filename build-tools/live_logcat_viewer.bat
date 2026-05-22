@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo   MIXCLEAN - TRINH XEM NHAT KY DONG HOC LOI [LIVE LOGCAT]
echo ========================================================
echo.

:: 1. Kiem tra adb co san trong he thong hay khong
where adb >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong tim thay cong cu ADB trong bien moi truong PATH!
    echo Vui long dam bao ban da cai dat Android SDK va cau hinh bien moi truong cho adb.exe.
    echo.
    pause
    exit /b 1
)

:: 2. Kiem tra thiet bi ket noi bang adb get-state
adb get-state >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong phat hien dien thoai Android nao dang ket noi qua ADB!
    echo Vui long kiem tra:
    echo   - Cap ket noi USB hoac Wifi Debugging da bat.
    echo   - Da cap quyen USB Debugging tren man hinh dien thoai.
    echo.
    pause
    exit /b 1
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
    set /p LAUNCH_CHOICE="Ban co muon tu dong mo app de tiep tuc theo doi? [Y/N]: "
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
call :GET_PID
echo ========================================================
echo   LOGCAT: XEM LOG LOI [CRASH/ERROR] DANG TRUYEN LIVE...
echo   PID: !PID!
echo   [Nhan Ctrl+C de dung theo doi va ve Menu]
echo ========================================================
echo.
if "!PID!"=="" (
    echo [CANH BAO] Khong the lay PID cua app. Chuyen sang loc chuoi tam thoi...
    adb logcat *:E | findstr /i "com.thgiang.image"
) else (
    adb logcat *:E --pid=!PID! -v color
)
pause
goto MENU

:VIEW_ALL
cls
call :GET_PID
echo ========================================================
echo   LOGCAT: XEM TOAN BO LOG DANG TRUYEN LIVE...
echo   PID: !PID!
echo   [Nhan Ctrl+C de dung theo doi va ve Menu]
echo ========================================================
echo.
if "!PID!"=="" (
    echo [CANH BAO] Khong the lay PID cua app. Chuyen sang loc chuoi tam thoi...
    adb logcat | findstr /i "com.thgiang.image"
) else (
    adb logcat --pid=!PID! -v color
)
pause
goto MENU

:VIEW_TAGS
cls
echo ========================================================
echo   LOGCAT: LOC LOG THEO CAC TAG EDITING / AI / REMOVER...
echo   [Nhan Ctrl+C de dung theo doi va ve Menu]
echo ========================================================
echo.
adb logcat -v color *:S MlKitBackgroundRemoverRepository:V ModNetBackgroundRemoverRepository:V BoundingBoxOverlay:V ThemeplateEditorViewModel:V BatchRemoveViewModel:V
pause
goto MENU

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
pause
goto MENU

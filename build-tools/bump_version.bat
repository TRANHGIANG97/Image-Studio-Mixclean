@echo off
setlocal enabledelayedexpansion

:MENU
if defined ORCHESTRATOR goto RUN_BUMP
cls
echo ========================================================
echo   MIXCLEAN - TU DONG NANG PHIEN BAN APP (BUMP VERSION)
echo ========================================================
echo.
echo  [1] Bat dau nang phien ban (+1 versionCode, +1 patch versionName)
echo  [2] Thoat
echo ========================================================
echo.
set /p CHOICE="Vui long chon [1-2]: "
if "%CHOICE%"=="2" exit /b 0
if "%CHOICE%" neq "1" (
    echo [ERROR] Lua chon khong hop le!
    pause
    goto MENU
)

:RUN_BUMP
:: Kiem tra moi truong Python
python --version >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong tim thay Python tren may tinh cua ban!
    echo Vui long cai dat Python va them vao bien moi truong PATH.
    echo.
    goto ERROR_HANDLER
)

:: Chay script python de xu ly phien ban
python "%~dp0bump_version.py"
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Gap loi trong qua trinh nang phien ban!
    goto ERROR_HANDLER
)

echo.
echo ========================================================
echo   === NANG PHIEN BAN THANH CONG! ===
echo ========================================================
echo.

if not defined ORCHESTRATOR (
    echo [1] Quay lai Menu
    echo [2] Thoat
    set /p SUCCESS_CHOICE="Vui long chon [1-2]: "
    if "!SUCCESS_CHOICE!"=="1" goto MENU
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
set /p ERROR_CHOICE="Vui long chon [1-2]: "
if "%ERROR_CHOICE%"=="1" goto MENU
exit /b 1

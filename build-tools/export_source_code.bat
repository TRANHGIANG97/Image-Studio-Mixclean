@echo off
setlocal enabledelayedexpansion

:MENU
cls
:: Kiem tra moi truong Python
python --version >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong tim thay Python tren may tinh cua ban!
    echo Vui long cai dat Python va them vao bien moi truong PATH.
    echo.
    pause
    exit /b 1
)

:: Chay script Python de trich xuat ma nguon
python "%~dp0export_source_code.py"

echo.
echo ========================================================
echo   [LUA CHON SAU KHI THUC THI]
echo --------------------------------------------------------
echo   [1] Quay lai Menu de tiep tuc trich xuat
echo   [2] Thoat
echo ========================================================
echo.
set /p FINAL_CHOICE="Vui long chon hanh dong [1-2]: "
if "%FINAL_CHOICE%"=="1" (
    goto MENU
)
exit /b 0

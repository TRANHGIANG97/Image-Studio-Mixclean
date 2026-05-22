@echo off
setlocal enabledelayedexpansion
set REPO_ROOT=%~dp0..
set GRADLEW_PATH=%REPO_ROOT%\gradlew.bat

echo ========================================================
echo   MIXCLEAN - DON GEP BO NHO DEM ^& SUA LOI GRADLE
echo ========================================================
echo.

cd /d "%REPO_ROOT%"

:: 1. Chay Gradle Clean
echo [1/4] Dang thuc hien Gradle Clean...
if exist "%GRADLEW_PATH%" (
    call "%GRADLEW_PATH%" clean
    if !ERRORLEVEL! neq 0 (
        echo [CANH BAO] Lenh Gradle Clean gap loi. Tiep tuc xoa cache thu cong...
    )
) else (
    echo [CANH BAO] Khong tim thay gradlew.bat, chuyen sang xoa cache thu cong...
)
echo.

:: 2. Xoa cac thu muc build va file tam thoi
echo [2/4] Dang xoa cac thu muc build va file tam thoi...
set DIRS_TO_DELETE=.gradle build app\build .cxx app\.cxx

for %%d in (%DIRS_TO_DELETE%) do (
    if exist "%%d" (
        echo   - Dang xoa: %%d...
        rd /s /q "%%d" 2>nul
        if exist "%%d" (
            echo     [CANH BAO] Khong the xoa hoan toan %%d (Co the dang bi khoa boi Android Studio).
        )
    )
)
echo.

:: 3. Reset ket noi thiet bi ADB
echo [3/4] Dang khoi dong lai dich vu ket noi thiet bi [ADB]...
where adb >nul 2>nul
if %ERRORLEVEL% equ 0 (
    adb kill-server
    adb start-server
    echo   - Da reset adb-server thanh cong.
) else (
    echo   - [INFO] Khong tim thay adb trong PATH, bo qua buoc nay.
)
echo.

:: 4. Hoan tat quy trinh
echo ========================================================
echo   === HOAN TAT DON DEP BO NHO DEM! ===
echo   Hay thu mo lai Android Studio va bien dich lai du an.
echo ========================================================
echo.
pause

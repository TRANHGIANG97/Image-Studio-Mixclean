@echo off
set GRADLEW_PATH=%~dp0..\gradlew.bat
set SRC_AAB_PATH=%~dp0..\app\build\outputs\bundle\release\app-release.aab
set DEST_AAB_PATH=%~dp0app-release.aab

echo ========================================================
echo   MIXCLEAN - DONG GOI VA COPY RELEASE AAB
echo ========================================================
echo.

echo [1/3] Kiem tra Gradle Wrapper...
if not exist "%GRADLEW_PATH%" (
    echo [ERROR] Khong tim thay gradlew.bat tai: %GRADLEW_PATH%
    pause
    exit /b 1
)

echo [2/3] Dang thuc hien build Release AAB...
echo Vui long cho trong giay lat...
echo.

call "%GRADLEW_PATH%" :app:bundleRelease

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build AAB that bai. Vui long kiem tra code hoac log build.
    pause
    exit /b 1
)

echo.
echo [3/3] Dang sao chep file AAB vao thu muc build-tools...
copy /y "%SRC_AAB_PATH%" "%DEST_AAB_PATH%" >nul

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong the sao chep file AAB vao: %DEST_AAB_PATH%
    pause
    exit /b 1
)

echo.
echo ========================================================
echo   === DONG GOI VA SAO CHEP THANH CONG! ===
echo   File AAB moi da duoc dua vao:
echo   %DEST_AAB_PATH%
echo ========================================================
echo.
pause

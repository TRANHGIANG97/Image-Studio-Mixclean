@echo off
set GRADLEW_PATH=%~dp0..\gradlew.bat
set SRC_AAB_PATH=%~dp0..\app\build\outputs\bundle\release\app-release.aab
set DEST_AAB_PATH=%~dp0app-release.aab

:START_BUILD
cls
:: Chuyen thu muc lam viec ve goc du an de Gradle xac dinh dung file settings.gradle.kts
cd /d "%~dp0.."

echo ========================================================
echo   MIXCLEAN - DONG GOI VA COPY RELEASE AAB
echo ========================================================
echo.

echo [1/3] Kiem tra Gradle Wrapper...
if not exist "%GRADLEW_PATH%" (
    echo [ERROR] Khong tim thay gradlew.bat tai: %GRADLEW_PATH%
    goto ERROR_HANDLER
)

echo [2/3] Dang thuc hien build Release AAB...
echo Vui long cho trong giay lat...
echo.

call "%GRADLEW_PATH%" :app:bundleRelease

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build AAB that bai. Vui long kiem tra code hoac log build.
    goto ERROR_HANDLER
)

echo.
echo [3/3] Dang sao chep file AAB vao thu muc build-tools...
copy /y "%SRC_AAB_PATH%" "%DEST_AAB_PATH%" >nul

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong the sao chep file AAB vao: %DEST_AAB_PATH%
    goto ERROR_HANDLER
)

echo.
echo ========================================================
echo   === DONG GOI VA SAO CHEP THANH CONG! ===
echo   File AAB moi da duoc dua vao:
echo   %DEST_AAB_PATH%
echo ========================================================
echo.
if not defined ORCHESTRATOR pause
exit /b 0

:ERROR_HANDLER
if defined ORCHESTRATOR (
    exit /b 1
)
echo.
echo ========================================================
echo   [MENU LUA CHON KHI LOI]
echo --------------------------------------------------------
echo   [1] Thu lai (Build lai)
echo   [2] Thoat
echo ========================================================
echo.
set /p ERROR_CHOICE="Vui long chon hanh dong [1-2]: "
if "%ERROR_CHOICE%"=="1" (
    goto START_BUILD
)
exit /b 1

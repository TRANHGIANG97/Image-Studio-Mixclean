@echo off
set GRADLEW_PATH=%~dp0..\gradlew.bat
set KEY_PATH=%~dp0..\play-service-account.json

:START_PUBLISH
cls
:: Chuyen thu muc lam viec ve goc du an de chay Gradle dung cach
cd /d "%~dp0.."

echo ========================================================
echo   MIXCLEAN - DANG TAI APP BUNDLE (AAB) LEN CH PLAY
echo ========================================================
echo.

:: Kiem tra file Credentials JSON
echo [1/2] Kiem tra khoa xac thuc Google Play API...
if not exist "%KEY_PATH%" (
    echo [ERROR] Khong tim thay file khoa xac thuc: play-service-account.json tai goc du an!
    echo.
    echo HUONG DAN THIET LAP:
    echo   1. Truy cap Google Play Console - Muc API Access.
    echo   2. Tao mot Service Account - Tai khoan dich vu - lien ket tren Google Cloud Console.
    echo   3. Tao và tai file Credentials khoa rieng tu dang JSON.
    echo   4. Dat ten file la 'play-service-account.json' va luu tai:
    echo      %KEY_PATH%
    echo   5. Cap quyen Release Manager cho Service Account do tren Play Console.
    echo.
    goto ERROR_HANDLER
)
echo [OK] Da tim thay file play-service-account.json.
echo.

:: Thuc hien build va day len CH Play qua Gradle Play Publisher
echo [2/2] Dang tien hanh bien dich va upload len CH Play...
echo Vui long cho trong giay lat (Qua trinh nay co the mat 1-3 phut)...
echo.

call "%GRADLEW_PATH%" :app:publishReleaseBundle

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Dang tai len CH Play that bai!
    echo.
    echo CAC NGUYEN NHAN PHO BIEN:
    echo   - Ma phien ban versionCode da ton tai tren CH Play - can tang versionCode truoc.
    echo   - File khoa JSON bi sai hoac het han, hoac chua cap du quyen tren Play Console.
    echo   - Mat ket noi mang.
    echo.
    goto ERROR_HANDLER
)

echo.
echo ========================================================
echo   === UPLOAD LEN GOOGLE PLAY STORE THANH CONG! ===
echo   Phien ban moi da duoc dua len track Internal Testing.
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
echo   [1] Thu lai (Build va tai lai)
echo   [2] Thoat
echo ========================================================
echo.
set /p ERROR_CHOICE="Vui long chon hanh dong [1-2]: "
if "%ERROR_CHOICE%"=="1" (
    goto START_PUBLISH
)
exit /b 1

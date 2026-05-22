@echo off
setlocal enabledelayedexpansion

set ORCHESTRATOR=1
set BUMP_SCRIPT=%~dp0bump_version.bat
set BUILD_SCRIPT=%~dp0build_aab_release.bat
set INSTALL_SCRIPT=%~dp0install_release_aab.bat
set GIT_SCRIPT=%~dp0git_push_helper.bat

:MENU
cls
echo ========================================================
echo   MIXCLEAN - QUY TRINH DONG GOI VA PHAT HANH 1-CLICK
echo ========================================================
echo.
echo  [1] QUY TRINH PHAT HANH DAY DU [Full Release Flow]
echo      - Nang cap phien ban [+1 versionCode, tang patch versionName]
echo      - Tu dong build Release AAB moi
echo      - Trich xuat & Cai dat len may that qua ADB
echo      - Commit & Dong bo toan bo code len Github
echo.
echo  [2] Chi nang phien ban va Build Release AAB
echo  [3] Chi Build Release AAB va Cai dat test tren thiet bi
echo  [4] Chi Dong bo code len Github
echo  [5] Thoat
echo.
echo ========================================================
echo.

set /p CHOICE="Vui long chon quy trinh [1-5]: "

if "%CHOICE%"=="1" goto FLOW_FULL
if "%CHOICE%"=="2" goto FLOW_BUMP_BUILD
if "%CHOICE%"=="3" goto FLOW_BUILD_INSTALL
if "%CHOICE%"=="4" goto FLOW_GIT
if "%CHOICE%"=="5" exit /b 0

echo [ERROR] Lua chon khong hop le. Vui long nhap tu 1 den 5.
pause
goto MENU

:FLOW_FULL
echo.
echo ========================================================
echo   BAT DAU: QUY TRINH PHAT HANH DAY DU (FULL RELEASE FLOW)
echo ========================================================
echo.

:: Buoc 1: Nang phien ban
echo [BUOC 1/4] Dang thuc hien nang phien ban app...
call "%BUMP_SCRIPT%"
if !ERRORLEVEL! neq 0 (
    echo.
    echo [ERROR] Buoc 1 [Nang phien ban] gap loi! Dung quy trinh.
    pause
    goto MENU
)

:: Buoc 2: Build AAB
echo.
echo [BUOC 2/4] Dang thuc hien build Release AAB...
call "%BUILD_SCRIPT%"
if !ERRORLEVEL! neq 0 (
    echo.
    echo [ERROR] Buoc 2 [Build Release AAB] gap loi! Dung quy trinh.
    pause
    goto MENU
)

:: Buoc 3: Cai dat test
echo.
echo [BUOC 3/4] Dang tien hanh cai dat test len may...
call "%INSTALL_SCRIPT%"
if !ERRORLEVEL! neq 0 (
    echo.
    echo [ERROR] Buoc 3 [Cai dat len thiet bi] gap loi! Dung quy trinh.
    pause
    goto MENU
)

:: Buoc 4: Dong bo Github
echo.
echo [BUOC 4/4] Dang dong bo code len Github...
call "%GIT_SCRIPT%"
if !ERRORLEVEL! neq 0 (
    echo.
    echo [ERROR] Buoc 4 [Dong bo Github] gap loi!
    pause
    goto MENU
)

echo.
echo ========================================================
echo   === HOAN THANH QUY TRINH PHAT HANH 1-CLICK! ===
echo ========================================================
echo   - Phien ban da duoc nang cap.
echo   - File Release AAB moi da duoc tao va copy ra.
echo   - App da duoc cai dat va kiem thu tren thiet bi cua ban.
echo   - Code moi nhat da duoc day an toan len Github.
echo ========================================================
echo.
pause
goto MENU

:FLOW_BUMP_BUILD
echo.
echo ========================================================
echo   BAT DAU: NANG PHIEN BAN ^& BUILD RELEASE AAB
echo ========================================================
echo.
call "%BUMP_SCRIPT%"
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Nang phien ban gap loi!
    pause
    goto MENU
)
call "%BUILD_SCRIPT%"
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Build Release AAB gap loi!
    pause
    goto MENU
)
echo.
echo [INFO] Da hoan thanh nang phien ban va build Release AAB!
pause
goto MENU

:FLOW_BUILD_INSTALL
echo.
echo ========================================================
echo   BAT DAU: BUILD RELEASE AAB ^& CAI DAT TEST
echo ========================================================
echo.
call "%BUILD_SCRIPT%"
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Build Release AAB gap loi!
    pause
    goto MENU
)
call "%INSTALL_SCRIPT%"
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Cai dat len thiet bi gap loi!
    pause
    goto MENU
)
echo.
echo [INFO] Da hoan thanh build Release AAB va cai dat test thiet bi!
pause
goto MENU

:FLOW_GIT
echo.
echo ========================================================
echo   BAT DAU: DONG BO CODE LEN GITHUB
echo ========================================================
echo.
call "%GIT_SCRIPT%"
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Dong bo Github gap loi!
    pause
    goto MENU
)
pause
goto MENU

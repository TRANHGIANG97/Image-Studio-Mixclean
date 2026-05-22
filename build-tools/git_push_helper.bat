@echo off
setlocal enabledelayedexpansion
set REPO_ROOT=%~dp0..

:CHECK_ENVIRONMENT
cls
echo ========================================================
echo   MIXCLEAN - CONG CU DONG BO GITHUB CHUYEN NGHIEP [PRO]
echo ========================================================
echo.

:: 1. Kiem tra Git da duoc cai dat chua
git --version >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong tim thay cong cu Git tren he thong!
    echo Vui long:
    echo   1. Tai Git tai: https://git-scm.com/downloads
    echo   2. Cai dat va dam bao tick chon "Add to PATH".
    echo.
    if defined ORCHESTRATOR exit /b 1
    
    echo ========================================================
    echo   [LUA CHON KHI LOI]
    echo --------------------------------------------------------
    echo   [1] Kiem tra lai - Thu lai
    echo   [2] Thoat
    echo ========================================================
    set /p ENV_CHOICE="Vui long chon [1-2]: "
    if "!ENV_CHOICE!"=="1" goto CHECK_ENVIRONMENT
    exit /b 1
)

cd /d "%REPO_ROOT%"

:: 2. Kiem tra va khoi tao Git Repo
if not exist ".git" (
    echo [CANH BAO] Thu muc nay chua duoc khoi tao Git.
    if defined ORCHESTRATOR (
        git init
        echo [INFO] Tu dong khoi tao repository Git.
    ) else (
        echo ========================================================
        echo   [CHU Y] THU MUC CHUA KHOI TAO GIT
        echo --------------------------------------------------------
        echo   [1] Khoi tao Repository Git moi cho du an
        echo   [2] Thoat
        echo ========================================================
        set /p INIT_CHOICE="Vui long chon [1-2]: "
        if "!INIT_CHOICE!"=="1" (
            git init
            if !ERRORLEVEL! neq 0 (
                echo [ERROR] Khoi tao Git repository that bai!
                pause
                goto CHECK_ENVIRONMENT
            )
            echo [INFO] Khoi tao repository thanh cong.
        ) else (
            exit /b 0
        )
    )
)

:: 3. Kiem tra cau hinh remote origin
git remote get-url origin >nul 2>nul
if %ERRORLEVEL% neq 0 (
    if defined ORCHESTRATOR (
        echo [ERROR] Project chua duoc cau hinh remote link Github! Khong the tu dong day code.
        exit /b 1
    )
    echo [CANH BAO] Project chua ket noi voi Github Repository [Chua co remote origin].
    echo ========================================================
    echo   [CAU HINH REMOTE ORIGIN]
    echo --------------------------------------------------------
    echo   [1] Nhap link Repository Github de lien ket
    echo   [2] Thoat
    echo ========================================================
    set /p REMOTE_CHOICE="Vui long chon [1-2]: "
    if "!REMOTE_CHOICE!"=="1" (
        set /p REMOTE_URL="Dan link Repository Github cua ban [e.g., https://github.com/user/repo.git]: "
        if not "!REMOTE_URL!"=="" (
            git remote add origin !REMOTE_URL!
            if !ERRORLEVEL! neq 0 (
                echo [ERROR] Khong the lien ket voi URL duoc cung cap!
                pause
                goto CHECK_ENVIRONMENT
            )
            echo [INFO] Da lien ket thanh cong Remote origin.
        ) else (
            echo [ERROR] URL khong duoc de trong.
            pause
            goto CHECK_ENVIRONMENT
        )
    ) else (
        exit /b 0
    )
)

:: Neu chay tu Orchestrator, tu dong lay nhanh dang active va chay luon
if defined ORCHESTRATOR (
    for /f "tokens=*" %%a in ('git branch --show-current') do set BRANCH=%%a
    if "!BRANCH!"=="" set BRANCH=main
    goto CHECK_LOCAL_BRANCH
)

:MENU
cls
echo ========================================================
echo   MIXCLEAN - CONG CU DONG BO GITHUB CHUYEN NGHIEP [PRO]
echo ========================================================
echo  Thu muc goc: %CD%
echo  Github Link: 
git remote get-url origin
echo --------------------------------------------------------
echo  [1] Dong bo [Commit + Push] len nhanh 'master'
echo  [2] Dong bo [Commit + Push] len nhanh 'main'
echo  [3] Dong bo [Commit + Push] len nhanh tuy chon [Custom]
echo  [4] Xem trang thai thay doi [Git Status]
echo  [5] Xem lich su cac ban commit [Git Log - 10 ban gan nhat]
echo  [6] Xem danh sach nhanh hien tai [Git Branch]
echo  [7] Thoat
echo ========================================================
echo.

set /p CHOICE="Vui long chon chuc nang [1-7]: "

if "%CHOICE%"=="1" goto PUSH_MASTER
if "%CHOICE%"=="2" goto PUSH_MAIN
if "%CHOICE%"=="3" goto PUSH_CUSTOM
if "%CHOICE%"=="4" goto SHOW_STATUS
if "%CHOICE%"=="5" goto SHOW_LOG
if "%CHOICE%"=="6" goto SHOW_BRANCHES
if "%CHOICE%"=="7" exit /b 0

echo [ERROR] Lua chon khong hop le. Vui long nhap tu 1 den 7.
pause
goto MENU

:PUSH_MASTER
set BRANCH=master
goto CHECK_LOCAL_BRANCH

:PUSH_MAIN
set BRANCH=main
goto CHECK_LOCAL_BRANCH

:PUSH_CUSTOM
set /p BRANCH="Nhap ten nhanh ban muon day code len: "
if "!BRANCH!"=="" (
    echo [ERROR] Ten nhanh khong duoc de trong.
    pause
    goto MENU
)
goto CHECK_LOCAL_BRANCH

:CHECK_LOCAL_BRANCH
:: Kiem tra nhanh cuc bo co ton tai hay khong de tranh crash/loi push nhanh khong ton tai
git rev-parse --verify !BRANCH! >nul 2>nul
if !ERRORLEVEL! neq 0 (
    echo.
    echo ========================================================
    echo   [ERROR] NHANH CUC BO [!BRANCH!] KHONG TON TAI!
    echo ========================================================
    echo Nhanh hien tai cua ban la:
    git branch --show-current
    echo.
    echo Goi y khac phuc:
    echo   - Chon nhanh dung ban dang code [vi du: chon [2] neu ban dang o nhanh 'main'].
    echo   - Hoac tao nhanh '!BRANCH!' tren may cua ban truoc.
    echo.
    if defined ORCHESTRATOR exit /b 1
    pause
    goto MENU
)

:CHECK_IDENTITY
:: 4. Kiem tra Thong tin nguoi dung [De tranh loi Commit nac danh tren may moi]
git config user.name >nul 2>nul
set HAS_NAME=%ERRORLEVEL%
git config user.email >nul 2>nul
set HAS_EMAIL=%ERRORLEVEL%

if %HAS_NAME% neq 0 (
    echo.
    echo [CANH BAO] Git chua duoc thiet lap Ho ten nguoi dung [user.name].
    if defined ORCHESTRATOR (
        git config --global user.name "MixClean Developer"
    ) else (
        set /p GIT_NAME="Vui long nhap Ho ten de ky ten vao Commit [e.g. Nguyen Van A]: "
        git config --global user.name "!GIT_NAME!"
    )
)

if %HAS_EMAIL% neq 0 (
    echo [CANH BAO] Git chua duoc thiet lap Email nguoi dung [user.email].
    if defined ORCHESTRATOR (
        git config --global user.email "developer@mixclean.com"
    ) else (
        set /p GIT_EMAIL="Vui long nhap Email cua ban: "
        git config --global user.email "!GIT_EMAIL!"
    )
)
goto RUN_PUSH

:RUN_PUSH
echo.
echo === KIEM TRA TRANG THAI DU AN... ===

:: Kiem tra co thay doi nao de commit hay khong
git status --porcelain | findstr /r "." >nul
if %ERRORLEVEL% neq 0 (
    echo [INFO] Khong co tap tin nao thay doi de commit.
    if defined ORCHESTRATOR (
        goto PUSH_ONLY
    )
    echo ========================================================
    echo   [KONG CO THAY DOI]
    echo --------------------------------------------------------
    echo   [1] Tiep tuc thuc hien day [PUSH] local len remote
    echo   [2] Quay lai Menu
    echo ========================================================
    set /p FORCE_PUSH="Vui long chon [1-2]: "
    if "!FORCE_PUSH!"=="1" (
        goto PUSH_ONLY
    ) else (
        goto MENU
    )
)

:: Nhap thong diep commit
if defined ORCHESTRATOR (
    set COMMIT_MSG=Release: Auto-deploy release on %date% %time%
) else (
    set /p COMMIT_MSG="Nhap thong diep commit [Commit Message]: "
    if "!COMMIT_MSG!"=="" (
        set COMMIT_MSG=Update: Auto-commit on %date% %time%
    )
)

echo.
echo [1/3] Dang them cac tap tin thay doi [git add .]...
git add .
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Qua trinh 'git add .' gap loi!
    if defined ORCHESTRATOR exit /b 1
    pause
    goto MENU
)

echo [2/3] Dang thiet lap commit...
git commit -m "!COMMIT_MSG!"
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Khoi tao commit that bai!
    if defined ORCHESTRATOR exit /b 1
    pause
    goto MENU
)

:PUSH_ONLY
echo.
echo [3/3] Dang day code len Github [git push origin !BRANCH!]...
git push -u origin !BRANCH!

if !ERRORLEVEL! equ 0 (
    echo.
    echo ========================================================
    echo   === DONG BO GITHUB THANH CONG! ===
    echo ========================================================
    if defined ORCHESTRATOR exit /b 0
    echo.
    echo   [1] Quay lai Menu chinh
    echo   [2] Thoat
    echo ========================================================
    set /p SUCCESS_CHOICE="Vui long chon [1-2]: "
    if "!SUCCESS_CHOICE!"=="1" goto MENU
    exit /b 0
) else (
    echo.
    echo ========================================================
    echo   [ERROR] DANG CO LOI XAY RA KHI PUSH CODE LEN GITHUB!
    echo ========================================================
    echo.
    echo CAC NGUYEN NHAN PHO BIEN VA PHUONG AN KHAC PHUC:
    echo.
    echo   [1] LOI CONFLICT [Code tren Github hien dang moi hon code local cua ban]:
    echo       =^> Huong dan: Ban can dong bo du lieu tren remote ve truoc [chay pull].
    echo.
    echo   [2] LOI QUYEN TRUY CAP [Authentication/Permission Error]:
    echo       =^> Huong dan: Kiem tra tai khoan Github, SSH Key/Personal Access Token.
    echo.
    echo   [3] LOI KET NOI [Network Error]:
    echo       =^> Huong dan: Kiem tra ket noi Internet/Wifi.
    echo.
    echo --------------------------------------------------------
    
    if defined ORCHESTRATOR exit /b 1
    
    :: Tich hop engine tu dong sua loi Conflict / Rebase tren nhanh hien tai
    set /p PULL_CHOICE="Ban co muon tu dong dong bo code moi tu Github ve [git pull --rebase] va push lai luon? [Y/N]: "
    if /i "!PULL_CHOICE!"=="Y" (
        echo.
        echo Dang tien hanh dong bo code tu Github ve...
        git pull origin !BRANCH! --rebase
        if !ERRORLEVEL! equ 0 (
            echo.
            echo [INFO] Dong bo thanh cong! Tu dong thuc hien push lai len Github...
            git push -u origin !BRANCH!
            if !ERRORLEVEL! equ 0 (
                echo.
                echo ========================================================
                echo   === HOAN TAT DONG BO SAU KHI GIAI QUYET XUNG DOT! ===
                echo ========================================================
                pause
                goto MENU
            ) else (
                echo [ERROR] Van gap loi khi day code len. Vui long kiem tra thong so dang nhap thu cong.
                pause
                goto MENU
            )
        ) else (
            echo.
            echo [ERROR] Qua trinh Pull code gap xung dot nang [Conflict file].
            echo Vui long mo IDE de giai quyet cac phan xung dot code thu cong truoc khi push.
            pause
            goto MENU
        )
    )
    goto MENU
)

:SHOW_STATUS
cls
echo ========================================================
echo   TRANG THAI THAY DOI CUA DU AN [GIT STATUS]
echo ========================================================
echo.
git status
echo.
echo ========================================================
echo   [1] Quay lai Menu chinh
echo   [2] Thoat
echo ========================================================
set /p STATUS_CHOICE="Vui long chon [1-2]: "
if "!STATUS_CHOICE!"=="1" goto MENU
exit /b 0

:SHOW_LOG
cls
echo ========================================================
echo   LICH SU COMMIT [GIT LOG - 10 PHIEN BAN GAN NHAT]
echo ========================================================
echo.
git log -n 10 --oneline --decorate --graph
if !ERRORLEVEL! neq 0 (
    echo [INFO] Kho luu tru chua co ban commit nao de hien thi lich su.
)
echo.
echo ========================================================
echo   [1] Quay lai Menu chinh
echo   [2] Thoat
echo ========================================================
set /p LOG_CHOICE="Vui long chon [1-2]: "
if "!LOG_CHOICE!"=="1" goto MENU
exit /b 0

:SHOW_BRANCHES
cls
echo ========================================================
echo   DANH SACH CAC NHANH HIEN TAI [GIT BRANCH]
echo ========================================================
echo.
git branch -a
echo.
echo ========================================================
echo   [1] Quay lai Menu chinh
echo   [2] Thoat
echo ========================================================
set /p BRANCH_CHOICE="Vui long chon [1-2]: "
if "!BRANCH_CHOICE!"=="1" goto MENU
exit /b 0

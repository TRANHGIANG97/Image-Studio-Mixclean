@echo off
setlocal enabledelayedexpansion

:: Kiem tra moi truong Python
where python >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Khong tim thay Python tren may tinh cua ban!
    echo Vui long cai dat Python va them vao bien moi truong PATH.
    echo.
    pause
    exit /b 1
)

:: Chay script python de xu ly phien ban
python "%~dp0bump_version.py"
if %ERRORLEVEL% neq 0 (
    if not defined ORCHESTRATOR pause
    exit /b 1
)
if not defined ORCHESTRATOR pause


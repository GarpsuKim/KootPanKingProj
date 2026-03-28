rem  **********************************************************
rem 
rem   ■ 끝판왕 프로그램 자동 다운로드 / 압축풀기 / 실행  ■
rem 
rem  **********************************************************
@echo off
cd /d "%~dp0"
SET "ROOT=%CD%"
echo  현재폴더 = %ROOT%
rem chcp 65001 > nul
cls
echo ============================================
echo   KootPanKing 다운로드 및 실행
echo ============================================
set "URL=https://github.com/GarpsuKim/KootPanKing/releases/download/v1.0.0/KootPanKing.zip"
set "ZIP=%ROOT%/KootPanKingUpgrade.zip"
set "DIR=%ROOT%/KootPanKing"
if exist  "%ZIP%"   del "%ZIP%"
echo.
echo [1/3] 다운로드 중...
timeout /t 10 /nobreak
where curl.exe >nul 2>&1
if %errorlevel% == 0 (
    echo [curl 사용 - 고속]
    curl.exe -L -o "%ZIP%" "%URL%"
) else (
    echo [PowerShell 사용 - 호환모드]
    powershell -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%ZIP%'"
)
if not exist "%ZIP%" (
    echo [오류] 다운로드 실패
    timeout /t 10 /nobreak
    exit /b
)
echo 완료
if exist  KootPanKing.exe   del KootPanKing.exe 
if exist  app\KootPanKing.jsa   del app\KootPanKing.jsa 
if exist  app\settings\KootPanKing.lock   del app\settings\KootPanKing.lock 
echo.
echo [2/3] 압축 해제 중...
rem powershell -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%DIR%' -Force"
powershell -Command "Expand-Archive -Path '%ZIP%' -DestinationPath . -Force"
echo 완료
echo.
echo [3/3] 실행...
timeout /t 10 /nobreak
rem start "" "%DIR%\KootPanKing.exe"
start "" KootPanKing.exe



@echo off
chcp 949 >nul
setlocal EnableDelayedExpansion
SET "EXE_DIR=%CD%"

:: ===== 설정 =====
set "APP_NAME=끝판王"
set "APP_DESC=아나로그 시계 끝판王"
set "EXE_PATH=%EXE_DIR%\KootPanKing.exe"

:: ===== EXE 존재 확인 =====
if not exist "%EXE_PATH%" (
    echo [오류] 실행 파일을 찾을 수 없습니다: %EXE_PATH%
    pause
    exit /b 1
)

:: ===== 바탕화면 경로 =====
set "DESKTOP=%USERPROFILE%\Desktop\%APP_NAME%.lnk"

:: ===== 시작메뉴 경로 =====
set "STARTMENU=%APPDATA%\Microsoft\Windows\Start Menu\Programs\%APP_NAME%.lnk"

:: ===== 바탕화면 바로가기 =====
powershell -NoProfile -NonInteractive -Command "$WshShell = New-Object -ComObject WScript.Shell; $Shortcut = $WshShell.CreateShortcut('%DESKTOP%');     $Shortcut.TargetPath = '%EXE_PATH%'; $Shortcut.WorkingDirectory = '%EXE_DIR%'; $Shortcut.IconLocation = '%EXE_PATH%,0'; $Shortcut.Description = '%APP_DESC%';  $Shortcut.Save()"

if errorlevel 1 (
    echo [오류] 바탕화면 바로가기 생성 실패
    pause
    exit /b 1
)
echo [완료] 바탕화면 바로가기 생성됨

:: ===== 시작메뉴 바로가기 =====
powershell -NoProfile -NonInteractive -Command "$WshShell = New-Object -ComObject WScript.Shell; $Shortcut = $WshShell.CreateShortcut('%STARTMENU%');   $Shortcut.TargetPath = '%EXE_PATH%'; $Shortcut.WorkingDirectory = '%EXE_DIR%'; $Shortcut.IconLocation = '%EXE_PATH%,0'; $Shortcut.Description = '%APP_DESC%';  $Shortcut.Save()"

if errorlevel 1 (
    echo [오류] 시작메뉴 바로가기 생성 실패
    pause
    exit /b 1
)
echo [완료] 시작메뉴 바로가기 생성됨

echo.
echo [바로가기] 성공적으로 생성되었습니다.
pause




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
timeout /t 3 /nobreak
if exist  "%ZIP%"   del "%ZIP%"
echo.
echo [1/3] 다운로드 중...
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
    timeout /t 3 /nobreak
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
timeout /t 3 /nobreak
rem start "" "%DIR%\KootPanKing.exe"
start "" KootPanKing.exe
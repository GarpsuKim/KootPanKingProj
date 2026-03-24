@echo off
chcp 65001 > nul

echo ============================================
echo   KootPanKing 다운로드 및 실행
echo ============================================

set "URL=https://github.com/GarpsuKim/KootPanKing/releases/download/v1.0.0/KootPanKing.zip"
set "ZIP=KootPanKing.zip"
set "DIR=KootPanKing"

echo.
echo [1/3] 다운로드 중...
powershell -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%ZIP%'"

if not exist "%ZIP%" (
    echo [오류] 다운로드 실패
    pause
    exit /b
)

echo 완료

echo.
echo [2/3] 압축 해제 중...
powershell -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%DIR%' -Force"

echo 완료

echo.
echo [3/3] 실행...
start "" "%DIR%\KootPanKing.exe"

echo.
echo 완료
pause
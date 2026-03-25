rem  **********************************************************
rem 
rem   ■ 끝판왕 프로그램 Quick 압축풀기 / 실행  ■
rem 
rem  **********************************************************
@echo off
chcp 65001 > nul
set "URL=https://github.com/GarpsuKim/KootPanKing/releases/download/v1.0.0/KootPanKing.zip"
set "ZIP=appUpGrade.zip"
set "DIR=KootPanKing"
if not exist "%ZIP%" (
    echo [오류] 다운로드 실패
    pause
    exit /b
)
echo ============================================
echo   KootPanKing 다운로드 완료하였습니다.
echo   [엔터]키를 누르세요
echo ============================================
pause
if exist  KootPanKing.exe   del KootPanKing.exe 
if exist  app\KootPanKing.jsa   del app\KootPanKing.jsa 
if exist  app\settings\KootPanKing.lock   del app\settings\KootPanKing.lock 
echo [2/3] 압축 해제 중...
rem powershell -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%DIR%' -Force"
powershell -Command "Expand-Archive -Path '%ZIP%' -DestinationPath . -Force"
echo 완료
echo.
echo [3/3] 실행...
rem start "" "%DIR%\KootPanKing.exe"
start "" KootPanKing.exe

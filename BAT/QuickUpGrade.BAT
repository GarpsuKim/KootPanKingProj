rem  **********************************************************
rem 
rem   ■ 끝판왕 프로그램 자동 다운로드 / 압축풀기 / 실행  ■
rem 
rem  [1] 이 파일 내용 전체를 PC 메모장(새 파일)으로 복사/붙여넣기 
rem 
rem  [2] PC 아무폴더에나 "aaa.BAT" 라고 저장
rem 
rem  [3] PC에서 "aaa.BAT" 실행 (익스플러로 또는 파일검색기에서 더블클릭)
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
if exist  KootPanKing.exe   del KootPanKing.exe 
if exist  app\KootPanKing.jsa   del app\KootPanKing.jsa 
if exist  app\settings\KootPanKing.lock   del app\settings\KootPanKing.lock 
echo ============================================
echo   KootPanKing 다운로드 완료하였습니다.
echo   [엔터]키를 누르세요
echo ============================================
pause
echo [2/3] 압축 해제 중...
rem powershell -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%DIR%' -Force"
powershell -Command "Expand-Archive -Path '%ZIP%' -DestinationPath . -Force"
echo 완료
echo.
echo [3/3] 실행...
rem start "" "%DIR%\KootPanKing.exe"
start "" KootPanKing.exe

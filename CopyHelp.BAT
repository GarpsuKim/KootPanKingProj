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

echo ============================================
echo   KootPanKing 다운로드 및 실행
echo ============================================

set "URL1=https://github.com/GarpsuKim/KootPanKing"
set "URL2=releases/download/v1.0.0/KootPanKing.zip"
set "URL=%URL1%/%URL2%" 

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
rem   ■
rem   ■   여기까지 복사 / 붙여넣기 하세요
rem  **********************************************************

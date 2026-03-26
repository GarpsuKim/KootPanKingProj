rem
rem  1) 이 파일의 내용을 PC 메모장에 복사/붙여넣기
rem  2) PC의 아무 폴더에  aaa.BAT으로 저장
rem  3) aaa.BAT 실행
rem
rem  약 5분정도 기다리면 아나로그 시계가 PC화면에서 작동하기 시작합니다.
rem

@echo off
cd /d "%~dp0"
SET "ROOT=%CD%"
echo  현재폴더 = %ROOT%
PAUSE

set "URL1=https://raw.githubusercontent.com"
set "URL2=GarpsuKim/KootPanKing/main/BAT/DownLoad_Release.zip"
set URL="%URL1%/%URL2%"
set "ZIP=DownLoad_Release.zip"

echo [1/2] Downloading...
powershell -Command "(New-Object Net.WebClient).DownloadFile('%URL%', '%ZIP%')"

if not exist "%ZIP%" (
    echo [ERROR] Download failed
    pause & exit /b 1
)

echo [2/2] Extracting...
powershell -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '.' -Force"

echo.
echo ---- 압축 해제된 파일 목록 ----
dir /b
echo.

rem type  DownLoad_Release.BAT
call  DownLoad_Release.BAT
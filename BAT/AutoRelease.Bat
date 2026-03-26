@echo off
cd /d "%~dp0"
SET "ROOT=%CD%"
echo  현재폴더 = %ROOT%
PAUSE

set "URL=https://raw.githubusercontent.com/GarpsuKim/KootPanKing/main/BAT/DownLoad_Release.zip"
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
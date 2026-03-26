@echo off
REM GitHub에서 DownLoad_Release.BAT 다운로드 후 실행

SET URL=https://raw.githubusercontent.com/GarpsuKim/KootPanKing/main/BAT/DownLoad_Release.BAT
SET FILE_NAME=DownLoad_Release.BAT

echo 다운로드 중...
powershell -Command "Invoke-WebRequest -Uri %URL% -OutFile %FILE_NAME%"

echo 실행 중...
call %FILE_NAME%

pause
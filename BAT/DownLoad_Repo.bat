@echo off
chcp 65001 > nul

REM ── 설정 ───────────────────────────────
set "REPO_URL=https://github.com/GarpsuKim/KootPanKing/archive/refs/heads/main.zip"
set "ZIP_FILE=%TEMP%\KootPanKing.zip"
set "TARGET_DIR=%USERPROFILE%\KootPanKing"
set "RUN_BAT=%TARGET_DIR%\BAT\AutoStart.bat"

echo 대상 폴더: %TARGET_DIR%

REM ── 기존 폴더 삭제 ─────────────────────
if exist "%TARGET_DIR%" (
    echo 기존 폴더 삭제 중...
    rmdir /s /q "%TARGET_DIR%"
)

REM ── 다운로드 ───────────────────────────
echo [1] 다운로드 중...
powershell -Command "Invoke-WebRequest -Uri '%REPO_URL%' -OutFile '%ZIP_FILE%'"

if not exist "%ZIP_FILE%" (
    echo 다운로드 실패
    pause
    exit /b
)

REM ── 압축 해제 ─────────────────────────
echo [2] 압축 해제 중...
powershell -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%USERPROFILE%' -Force"

REM ── 폴더 이름 정리 ───────────────────
for /d %%i in ("%USERPROFILE%\KootPanKing-*") do (
    ren "%%i" "KootPanKing"
)

REM ── zip 삭제 ──────────────────────────
del "%ZIP_FILE%"

REM ── 실행 ─────────────────────────────
if exist "%RUN_BAT%" (
    echo [3] AutoStart 실행 중...
    call "%RUN_BAT%"
) else (
    echo AutoStart.bat 파일이 없습니다:
    echo %RUN_BAT%
)

pause
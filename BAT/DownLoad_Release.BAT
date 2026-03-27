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
cls
@echo off
cd /d "%~dp0"
SET "ROOT=%CD%"
echo  현재폴더 = %ROOT%

echo ============================================
echo   KootPanKing 다운로드 및 실행
echo ============================================
set "URL=https://github.com/GarpsuKim/KootPanKing/releases/download/v1.0.0/KootPanKing.zip"
set "ZIP=KootPanKingRelease.zip"
set "DIR=KootPanKing"
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

cd /d "%~dp0KootPanKing"
if errorlevel 1 (
echo [ERROR] KootPanKing 폴더 없음
pause & exit /b 1
)
KootPanKing.exe

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

rem   ■
rem   ■   여기까지 복사 / 붙여넣기 하세요
rem  **********************************************************
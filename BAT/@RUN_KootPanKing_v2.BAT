@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

REM ── ROOT 경로 안전하게 설정 ───────────────────────────────
cd /d "%~dp0.."
set "ROOT_DIR=%CD%"

echo ROOT_DIR = %ROOT_DIR%

REM ── 기본 설정 ─────────────────────────────────────────────
set "PROJECT=KootPanKing"
set "JarFile=%PROJECT%.jar"
set "OUT=%ROOT_DIR%\Class"
set "LIB=%ROOT_DIR%\Lib"
set "SOURCE_DIR=%ROOT_DIR%\Source"
set "ERROR_DIR=%ROOT_DIR%\ERROR_OF_COMPILE"

if not exist "%ERROR_DIR%" mkdir "%ERROR_DIR%"
set "ERROR_FILE=%ERROR_DIR%\compile_error.txt"

REM ── JAR 존재 확인 ─────────────────────────────────────────
echo [STEP 1] JAR 존재 확인 중...
set MISSING=0

set FILES_CHECK=^
jackson-core-2.21.1.jar ^
jetty-server-9.4.54.v20240208.jar ^
guava-33.4.0-jre.jar ^
json-simple-1.1.1.jar ^
ical4j-4.2.3.jar ^
slf4j-api-2.0.12.jar ^
slf4j-simple-2.0.12.jar ^
commons-codec-1.17.0.jar ^
commons-lang3-3.18.0.jar ^
threeten-extra-1.8.0.jar

for %%f in (%FILES_CHECK%) do (
    if not exist "%LIB%\%%f" (
        echo [MISS] %%f
        set MISSING=1
    )
)

if %MISSING%==1 (
    echo.
    echo [ERROR] JAR 파일 누락. Lib 폴더 확인 필요
    pause
    goto :end
)

echo [STEP 1] 완료

REM ── 컴파일 ───────────────────────────────────────────────
echo.
echo [STEP 2] 컴파일 중...

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

cd "%SOURCE_DIR%"

set FILES=
for %%f in (*.java) do (
    set FILES=!FILES! %%f
)

javac -encoding UTF-8 -cp "%LIB%\*" -d "%OUT%" !FILES! 2> "%ERROR_FILE%"

if errorlevel 1 (
    echo [ERROR] 컴파일 실패
    echo 로그 확인: %ERROR_FILE%
    pause
    exit /b 1
)

echo 컴파일 완료

REM ── JAR 빌드 ─────────────────────────────────────────────
echo.
echo [STEP 3] JAR 빌드 중...

set "BUILD_DIR=%ROOT_DIR%\TEST"
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

cd "%BUILD_DIR%"

if exist "%JarFile%" del "%JarFile%"

jar cfe "%JarFile%" %PROJECT% -C "%OUT%" .

echo JAR 생성 완료

REM ── 실행 ───────────────────────────────────────────────
echo.
echo [STEP 4] 실행 중...

start "" javaw -cp "%BUILD_DIR%\%JarFile%;%LIB%\*" %PROJECT%

echo.
echo [완료] %PROJECT% 실행됨
echo.

:end
pause
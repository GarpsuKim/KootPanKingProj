@echo off
chcp 65001 > nul
echo ============================================
echo  AnalogClock EXE 빌드 스크립트 (jpackage)
echo ============================================

cd .
cd ..

setlocal enabledelayedexpansion
set "PROJECT=KootPanKing"
set "ROOT_DIR=%CD%"
set "SOURCE_DIR=%ROOT_DIR%\Source"
set "SOURCE_FILE=%SOURCE_DIR%\%CLASS_NAME%.java"
set "LIB_PATH=%ROOT_DIR%\Lib"
set "CompileError_PATH=%ROOT_DIR%\ERROR_OF_COMPILE"
set "CLASS_PATH=%ROOT_DIR%\Class"
set ERROR_FILE=%CompileError_PATH%\ERROR_%CLASS_NAME%.txt
set OUT=%CLASS_PATH%
set "JarFile=%PROJECT%.jar"

:: ── 1. 작업 폴더 생성 ────────────────────────
if not exist build     mkdir build
if not exist dist      mkdir dist
if not exist input_dir mkdir input_dir

:: build 폴더 클린 (이전 컴파일 결과 제거)
if exist build\*.class del /q build\*.class
:: dist 클린 (jpackage 는 대상 폴더가 이미 있으면 오류)
if exist dist\KootPanKing rmdir /s /q dist\KootPanKing

:: ── 2. 컴파일 ────────────────────────────────
echo.
echo 컴파일 중...

CD "%SOURCE_DIR%"
DEL  ddDIR.TXT
type nul > ddDIR.TXT
dir /b "*.java"  >>  ddDIR.TXT

TYPE   ddDIR.txt
pause

set FILES=
for /f "delims=" %%f in (ddDIR.TXT) do set FILES=!FILES! %%f
javac -encoding UTF-8 -cp %LIB_PATH%\* -d %ROOT_DIR%\build !FILES!

if errorlevel 1 (
    echo [오류] 컴파일 실패!
    pause & exit /b 1
)
echo     완료


CD %ROOT_DIR%

:: ── 3. JAR 생성 ──────────────────────────────
echo.
echo [2/4] JAR 생성 중...

:: 라이브러리 JAR 를 input_dir 최상위에 복사 (jpackage 가 자동으로 classpath 등록)
echo     라이브러리 복사 중...
copy /Y "%LIB_PATH%\*.jar" input_dir\ >nul

:: manifest (Main-Class 만 지정, Class-Path 불필요)
echo Main-Class: %PROJECT%  > build\manifest.txt

jar cfm input_dir\%JarFile%  build\manifest.txt -C build .
if errorlevel 1 (
    echo [오류] JAR 생성 실패!
    pause & exit /b 1
)
echo     완료

:: ── 4. 설정 파일 복사 (있는 경우) ────────────
echo.
echo [3/4] 리소스 복사 중...
if exist clock_config.ini        copy clock_config.ini        input_dir\ >nul
if exist alarms.dat              copy alarms.dat              input_dir\ >nul
if exist Kakao.txt               copy Kakao.txt               input_dir\ >nul
if exist *.wav                   copy *.wav                   input_dir\ >nul
if exist *.mp3                   copy *.mp3                   input_dir\ >nul
if exist app.ico                 copy app.ico                 input_dir\ >nul
echo     완료

:: ── 5. jpackage EXE 빌드 ─────────────────────
echo.
echo [4/4] EXE 빌드 중... (1~2분 소요)

:: 아이콘 파일 존재 여부에 따라 분기
if exist app.ico (
    jpackage ^
        --name %PROJECT% ^
        --type app-image ^
        --input input_dir ^
        --dest dist ^
        --main-jar %JarFile% ^
        --main-class %PROJECT% ^
        --icon app.ico ^
        --app-version 1.0.0 ^
        --vendor "MyApp"
) else (
    jpackage ^
        --name %PROJECT% ^
        --type app-image ^
        --input input_dir ^
        --dest dist ^
        --main-jar %JarFile% ^
        --main-class %PROJECT% ^
        --app-version 1.0.0 ^
        --vendor "MyApp"
)

if errorlevel 1 (
    echo [오류] jpackage 빌드 실패!
    pause & exit /b 1
)

cd  "%ROOT_DIR%\input_dir"


:: ── 완료 ─────────────────────────────────────
echo.
echo ============================================
echo  빌드 완료!
echo  실행파일 위치: dist\KootPanKing\KootPanKing.exe
echo ============================================
echo.
echo  배포 시 dist\KootPanKing\ 폴더 전체를 전달하세요.
echo  (Java 설치 없이 실행 가능)
echo.
dir
del .
cd  ..
rd  input_dir
pause
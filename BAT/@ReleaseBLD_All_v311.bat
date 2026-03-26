REM  888888888888888888888888888888888888888888888888888888
REM  This file name : %ROOT_DIR%/BAT/@ReleaseBLD_All_v3.bat
REM  888888888888888888888888888888888888888888888888888888

SET PROJECT=KootPanKing
SET VERSION=1.0.1
SET PRODUCT_CODE_GUID=47617270-5375-504B-8069-6D4B6F726561
rem G.a.r.p.S.u.50.K.80.i.m.K.o.r.e.a.

@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

echo ============================================
echo   SAFE BUILD SCRIPT (KootPanKing)
echo ============================================

:: 1. 루트 디렉토리 설정 (스크립트 위치 기준 상위 폴더)
cd /d "%~dp0"
cd ..
set "ROOT_DIR=%CD%"

:: 2. 폴더 존재 여부 검증
if not exist "%ROOT_DIR%\Source" (
echo [중단] Source 폴더를 찾을 수 없습니다: %ROOT_DIR%\Source
pause & exit /b 1
)

:: 3. 기본 경로 설정
set "SOURCE_DIR=%ROOT_DIR%\Source"
set "LIB_PATH=%ROOT_DIR%\Lib"
set "BUILD_DIR=%ROOT_DIR%\build"
set "DIST_DIR=%ROOT_DIR%\KootPanKing_SetUp"
set "INPUT_DIR=%ROOT_DIR%\input_dir"
set "LOG_FILE=%ROOT_DIR%\build_log.txt"
set "JarFile=%PROJECT%.jar"
set "ICOFile=%PROJECT%.ico"
set TMP=C:\Temp
set TEMP=C:\Temp

:: 4. 이전 빌드 파일 정리
echo [1/4] 폴더 준비 중...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"

mkdir "%BUILD_DIR%"
mkdir "%DIST_DIR%"
mkdir "%INPUT_DIR%"

:: 5. 컴파일 (모든 하위 폴더의 .java 포함)
echo [2/4] 컴파일 중...
cd /d "%SOURCE_DIR%"
dir /s /b *.java > "%TEMP%\java_files.txt"

javac -encoding UTF-8 -cp "%LIB_PATH%\*" -d "%BUILD_DIR%" @"%TEMP%\java_files.txt" 2> "%LOG_FILE%"

if errorlevel 1 (
echo [오류] 컴파일 실패! 로그를 확인하세요: %LOG_FILE%
type "%LOG_FILE%"
pause & exit /b 1
)
del "%TEMP%\java_files.txt"
echo      완료.


:: 6. JAR 및 Manifest 생성
echo [3/4] JAR 생성 및 리소스 복사 중...

:: Manifest 작성 (괄호 없이 깔끔하게)
echo Main-Class: %PROJECT%> "%BUILD_DIR%\manifest.txt"

:: 라이브러리 복사
copy /Y "%LIB_PATH%\*.jar" "%INPUT_DIR%" >nul

:: JAR 생성 (로그 기록)
jar cfm "%INPUT_DIR%\%JarFile%" "%BUILD_DIR%\manifest.txt" -C "%BUILD_DIR%" . >> "%LOG_FILE%" 2>&1

if errorlevel 1 (
echo [오류] JAR 생성 실패
pause & exit /b 1
)

:: 리소스 파일들 복사 (에러 방지를 위해 한 줄씩 처리하거나 구문 수정)
:: %%ext 앞에 공백이 너무 많거나 줄바꿈이 꼬이면 에러가 날 수 있습니다.
for %%e in (ini dat txt wav mp3 ico) do (
if exist "%ROOT_DIR%\*.%%e" (
copy /Y "%ROOT_DIR%\*.%%e" "%INPUT_DIR%" >nul
)
)

echo      완료.

:: 7. EXE 빌드 (jpackage 옵션 수정)
echo [4/4] EXE 빌드 중 (	--type exe ^)...

:: 핵심: --main-jar와 --main-class를 지정하고, --input 폴더를 라이브러리 경로로 활용
rem set "JP_OPTS_OLD=--name  %PROJECT% --type app-image --input "%INPUT_DIR%" --dest "%DIST_DIR%" --main-jar %JarFile% --main-class %PROJECT% --app-version 1.0.0 --vendor "KootPanKing""
rem set "JP_OPTS=--name "%PROJECT%" --type exe --input "%INPUT_DIR%" --dest "%DIST_DIR%" --main-jar "%JarFile%" --main-class "%PROJECT%" --app-version 1.0.0 --vendor "%PROJECT%" --win-menu --win-menu-group "%PROJECT%" --win-shortcut     --win-dir-chooser "

set JP_OPTS=--name "%PROJECT%" ^
	--type exe ^
	--app-version %VERSION% ^
	--win-upgrade-uuid %PRODUCT_CODE_GUID% ^
	--input "%INPUT_DIR%" ^
	--dest "%DIST_DIR%" ^
	--main-jar "%JarFile%" ^
	--main-class "%PROJECT%" ^
	--vendor "%PROJECT%" ^
	--win-menu ^
	--win-menu-group "%PROJECT%" ^
	--win-shortcut ^
	--win-per-user-install ^
	--win-dir-chooser

:: 만약 외부 라이브러리 인식이 안 된다면 아래 옵션을 JP_OPTS에 추가하세요:
:: --module-path 대신 일반 클래스패스 앱이므로 jpackage가 --input의 JAR들을 기본적으로 처리합니다.

@echo on
:: 아이콘 설정
SET icoFullPath=%ROOT_DIR%\%ICOFile%
if exist "%icoFullPath%" (
set "JP_OPTS=%JP_OPTS% --icon %icoFullPath%"
)

@echo off
jpackage %JP_OPTS%    > %ROOT_DIR%\ERROR_OF_COMPILE\jpackage_Out.txt 2>&1

if errorlevel 1 (
echo [오류] jpackage 실행 실패
pause & exit /b 1
)

:: 8. 마무리 정리
echo 정리 중...
rmdir /s /q "%INPUT_DIR%"
rmdir /s /q "%BUILD_DIR%"

echo ============================================
echo   빌드 완료!
echo   결과물 위치: %DIST_DIR%\%PROJECT%\%PROJECT%.exe
echo ============================================
pause

REM  888888888888888888888888888888888888888888888888888888
REM  This file name : %ROOT_DIR%/BAT/@ReleaseBLD_All_v3.bat
REM  888888888888888888888888888888888888888888888888888888
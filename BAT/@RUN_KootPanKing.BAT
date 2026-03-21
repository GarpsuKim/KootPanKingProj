@echo off
chcp 65001 > nul

cd .
cd ..

REM ── 경로 설정 ──────────────────────────────────────────────────
set "PROJECT=KootPanKing"
set "JarFile=%PROJECT%.jar"
set "ROOT_DIR=%CD%"
set "OUT=%ROOT_DIR%\Class"
set "LIB=%ROOT_DIR%\Lib"

REM ── JAR 존재 확인 ──────────────────────────────────────────────
echo [STEP 1] JAR 존재 확인 중...
set MISSING=0
set JSON=json-simple-1.1.1.jar
set ICAL1=ical4j-4.2.3.jar
set ICAL2=slf4j-api-2.0.12.jar
set ICAL3=slf4j-simple-2.0.12.jar
set ICAL4=commons-codec-1.17.0.jar
set ICAL5=commons-lang3-3.18.0.jar
set ICAL6=threeten-extra-1.8.0.jar

if not exist "%LIB%\jackson-core-2.21.1.jar"           ( echo [MISS] jackson-core-2.21.1.jar           & set MISSING=1 )
if not exist "%LIB%\jetty-server-9.4.54.v20240208.jar" ( echo [MISS] jetty-server-9.4.54.v20240208.jar & set MISSING=1 )
if not exist "%LIB%\guava-33.4.0-jre.jar"              ( echo [MISS] guava-33.4.0-jre.jar              & set MISSING=1 )
if not exist "%LIB%\json-simple-1.1.1.jar"             ( echo [MISS] json-simple-1.1.1.jar             & set MISSING=1 )

if not exist "%LIB%\%ICAL1%"                           ( echo [MISS] %ICAL1%                            & set MISSING=1 )
if not exist "%LIB%\%ICAL2%"                           ( echo [MISS] %ICAL2%                            & set MISSING=1 )
if not exist "%LIB%\%ICAL3%"                           ( echo [MISS] %ICAL3%                            & set MISSING=1 )
if not exist "%LIB%\%ICAL4%"                           ( echo [MISS] %ICAL4%                            & set MISSING=1 )
if not exist "%LIB%\%ICAL5%"                           ( echo [MISS] %ICAL5%                            & set MISSING=1 )
if not exist "%LIB%\%ICAL6%"                           ( echo [MISS] %ICAL6%                            & set MISSING=1 )

if %MISSING%==1 (
    echo.
    echo [ERROR] 위 JAR 파일 없음. %LIB% 폴더 확인하세요.
    pause
    goto :end
)
echo [STEP 1] JAR 확인 완료

REM ── 클래스패스 (Google Calendar JAR 완전 제거) ─────────────────
rem GoogleCalendarService 가 순수 Java HTTP 로 재작성되어
rem google-api-client / google-http-client / opencensus 전부 불필요
set CP=%LIB%\jackson-core-2.21.1.jar
set CP=%CP%;%LIB%\jetty-server-9.4.54.v20240208.jar
set CP=%CP%;%LIB%\guava-33.4.0-jre.jar
set CP=%CP%;%LIB%\%JSON%

REM ── JAR 빌드 ───────────────────────────────────────────────────
echo [STEP 2] JAR 빌드 중...
if exist %JarFile% del %JarFile% 
jar cfe %JarFile%  %PROJECT% -C class .
echo [STEP 2] 빌드 완료

REM ── 실행 ───────────────────────────────────────────────────────
echo [STEP 3] 실행 중...
start "" javaw -cp "%ROOT_DIR%\%JarFile%;%Lib%\*" %PROJECT%

echo.
echo [완료] %PROJECT% 시작됨
echo.

:end

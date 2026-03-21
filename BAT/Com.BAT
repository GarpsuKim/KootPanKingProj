@echo off
chcp 65001 > nul

if "%1" =="" (
    echo [ERRO] source ????[xxxx.java]
    echo 사용법: com.bat  source
	PAUSE
    goto :end
)

cd .
cd ..

@echo off
set "CLASS_NAME=%1"
set "ROOT_DIR=%CD%"
set "SOURCE_DIR=%ROOT_DIR%\Source"
set "SOURCE_FILE=%SOURCE_DIR%\%CLASS_NAME%.java"
set "LIB_PATH=%ROOT_DIR%\Lib"
set "CompileError_PATH=%ROOT_DIR%\ERROR_OF_COMPILE"
set "CLASS_PATH=%ROOT_DIR%\Class"
set ERROR_FILE=%CompileError_PATH%\ERROR_%CLASS_NAME%.txt
set OUT=%CLASS_PATH%

echo SOURCE: %SOURCE_FILE%

type nul > "%ERROR_FILE%"

javac -d %OUT%  -Xlint:deprecation ^
  --class-path "%OUT%;K:\java25\Lib\*" ^
  -encoding UTF-8 ^
  -sourcepath "%SOURCE_DIR%" ^
  "%SOURCE_FILE%" 2> "%ERROR_FILE%"

if %errorlevel% neq 0 (
    echo [ERROR] COMPILE ERROR
    echo ==========================================
    type %ERROR_FILE%
    echo ==========================================
    echo.
	pause
    goto :end
)

for %%A in (%ERROR_FILE%) do set FILE_SIZE=%%~zA
if %FILE_SIZE% gtr 0 (
    type %ERROR_FILE%
    echo ■■■■■■■■■■■ [WARNING] COMPILE WARNING
	pause
    echo.
)

echo [ok] COMPILE SUCCESSFUL!

:end
CD "%ROOT_DIR%\BAT" 

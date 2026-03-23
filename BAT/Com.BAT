@echo off
chcp 65001 > nul

if "%1"=="" (
    echo [ERROR] source ????
    pause
    goto :end
)

set "ROOT_DIR=%~dp0.."
set "CLASS_NAME=%1"
set "SOURCE_DIR=%ROOT_DIR%\Source"
set "SOURCE_FILE=%SOURCE_DIR%\%CLASS_NAME%.java"
set "LIB_PATH=%ROOT_DIR%\Lib"
set "ERROR_DIR=%ROOT_DIR%\ERROR_OF_COMPILE"
set "ERROR_FILE=%ERROR_DIR%\ERROR_%CLASS_NAME%.txt"

if not exist "%ERROR_DIR%" mkdir "%ERROR_DIR%"

echo SOURCE: %SOURCE_FILE%

type nul > "%ERROR_FILE%"

javac -d "%ROOT_DIR%\Class" ^
  --class-path "%ROOT_DIR%\Class;%LIB_PATH%\*" ^
  -encoding UTF-8 ^
  -sourcepath "%SOURCE_DIR%" ^
  "%SOURCE_FILE%" 2> "%ERROR_FILE%"

if errorlevel 1 (
    echo [ERROR] COMPILE ERROR
    type "%ERROR_FILE%"
    pause
    goto :end
)

for %%A in ("%ERROR_FILE%") do set FILE_SIZE=%%~zA
if %FILE_SIZE% gtr 0 (
    type "%ERROR_FILE%"
    echo [WARNING]
    pause
)

echo [OK] COMPILE SUCCESSFUL!

:end
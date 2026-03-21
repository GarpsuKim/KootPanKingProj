@echo off
chcp 65001 > nul

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

echo ROOT 경로-----------  %ROOT_DIR%
echo SOURCE 경로---------  %SOURCE_DIR%
echo LIBRARY 경로--------  %LIB_PATH%
echo CLASS 경로 ---------  %CLASS_PATH%
echo CompileError 경로 --  %CompileError_PATH%

pause
ECHO "DIR %SOURCE_DIR%"
DIR "%SOURCE_DIR%"
pause
ECHO "DIR %LIB_PATH%"
DIR "%LIB_PATH%"
pause
CD "%ROOT_DIR%\BAT" 
DIR
pause
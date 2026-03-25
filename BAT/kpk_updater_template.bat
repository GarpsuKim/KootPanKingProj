rem  프로그램 자동 업데이트를 위한 bat 명령어 파일
rem  모든 파일 자동 다운로드/압축풀기/ 업데이트 완료 후 
rem  EXE 재시작 직전에 이 파일 실행
@echo off
setlocal
echo [kpk_updater] start > "{{LOG_PATH}}"
echo [kpk_updater] installDir : "{{INSTALL_DIR}}" >> "{{LOG_PATH}}"
echo [kpk_updater] extractDir : "{{EXTRACT_DIR}}" >> "{{LOG_PATH}}"
echo [kpk_updater] waiting for Java process exit... >> "{{LOG_PATH}}"
ping 127.0.0.1 -n 4 >nul
echo [kpk_updater] wait done >> "{{LOG_PATH}}"
if not exist "{{EXTRACT_DIR}}" (
  echo [kpk_updater] ERROR: extractDir not found >> "{{LOG_PATH}}"
  goto :error
)
echo [kpk_updater] extractDir exists OK >> "{{LOG_PATH}}"
echo [kpk_updater] robocopy start >> "{{LOG_PATH}}"
robocopy "{{EXTRACT_DIR}}" "{{INSTALL_DIR}}" /E /IS /IT /NJH /NJS >> "{{LOG_PATH}}" 2>&1
set RC=%errorlevel%
echo [kpk_updater] robocopy exit code: %RC% >> "{{LOG_PATH}}"
if %RC% GTR 7 (
  echo [kpk_updater] ERROR: robocopy failed >> "{{LOG_PATH}}"
  goto :error
)
echo [kpk_updater] robocopy OK >> "{{LOG_PATH}}"
echo [kpk_updater] starting exe: "{{INSTALL_DIR}}\{{EXE_NAME}}" >> "{{LOG_PATH}}"
if not exist "{{INSTALL_DIR}}\{{EXE_NAME}}" (
  echo [kpk_updater] ERROR: exe not found >> "{{LOG_PATH}}"
  goto :error
)
cd /d "{{INSTALL_DIR}}"
echo [kpk_updater] cd done >> "{{LOG_PATH}}"
start "" "{{INSTALL_DIR}}\{{EXE_NAME}}"
echo [kpk_updater] start issued >> "{{LOG_PATH}}"
ping 127.0.0.1 -n 6 >nul
echo [kpk_updater] wait after start done >> "{{LOG_PATH}}"
goto :cleanup
:error
echo [kpk_updater] === UPGRADE FAILED === >> "{{LOG_PATH}}"
echo.
echo *** 업그레이드 실패 ***
echo 로그 파일: {{LOG_PATH}}
echo.
pause
goto :cleanup
:cleanup
echo [kpk_updater] cleanup start >> "{{LOG_PATH}}"
rmdir /S /Q "{{TMP_DIR}}"
echo [kpk_updater] done >> "{{LOG_PATH}}"
(goto) 2>nul & del "%~f0"

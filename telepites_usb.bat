@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo  Voicetex Diktalas - telepites USB-n (ADB)
echo  -----------------------------------------
for /f "tokens=1,* delims==" %%a in (local.properties) do if "%%a"=="sdk.dir" set SDK=%%b
set SDK=%SDK:\\=\%
set SDK=%SDK:\:=:%
set ADB=%SDK%\platform-tools\adb.exe
if not exist "%ADB%" (
  echo  HIBA: nem talalom az adb.exe-t: %ADB%
  goto vege
)
"%ADB%" devices
"%ADB%" install -r "..\voicetex-diktalo-1.3.0.apk"
:vege
echo.
pause

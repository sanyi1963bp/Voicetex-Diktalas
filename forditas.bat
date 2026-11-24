@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo  Voicetex Diktalas - forditas
echo  ----------------------------
call gradlew.bat assembleRelease --console=plain
if errorlevel 1 (
  echo.
  echo  HIBA: a forditas nem sikerult. Masold ki a fenti hibauzenetet Claude-nak.
  goto vege
)
copy /y "app\build\outputs\apk\release\app-release.apk" "..\voicetex-diktalo-1.3.0.apk" >nul
echo.
echo  KESZ: ..\voicetex-diktalo-1.3.0.apk
echo  Masold at a telefonra, es telepitsd.
:vege
echo.
pause

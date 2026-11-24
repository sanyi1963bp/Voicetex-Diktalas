@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo  Voicetex Diktalas - forditas (teljes + lite)
echo  --------------------------------------------
call gradlew.bat assembleTeljesRelease assembleLiteRelease --console=plain
if errorlevel 1 (
  echo.
  echo  HIBA: a forditas nem sikerult. Masold ki a fenti hibauzenetet Claude-nak.
  goto vege
)
if not exist apk mkdir apk
copy /y "app\build\outputs\apk\teljes\release\app-teljes-release.apk" "..\voicetex-diktalo-1.5.1.apk" >nul
copy /y "app\build\outputs\apk\lite\release\app-lite-release.apk" "..\voicetex-diktalo-lite-1.5.1.apk" >nul
copy /y "app\build\outputs\apk\teljes\release\app-teljes-release.apk" "apk\voicetex-diktalo.apk" >nul
copy /y "app\build\outputs\apk\lite\release\app-lite-release.apk" "apk\voicetex-diktalo-lite.apk" >nul
echo.
echo  KESZ:
echo    Teljes (lebego gombbal, USB-n): ..\voicetex-diktalo-1.5.1.apk
echo    Lite (csak billentyuzet, linkrol): ..\voicetex-diktalo-lite-1.5.1.apk
:vege
echo.
pause

@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
echo.
echo  Voicetex Diktalas - frissites: forditas + telepites Wi-Fi-n
echo  -----------------------------------------------------------
for /f "tokens=1,* delims==" %%a in (local.properties) do if "%%a"=="sdk.dir" set SDK=%%b
set SDK=%SDK:\\=\%
set SDK=%SDK:\:=:%
set ADB=%SDK%\platform-tools\adb.exe
if not exist "%ADB%" (
  echo  HIBA: nem talalom az adb.exe-t: %ADB%
  goto vege
)

REM A cimet mar az elejen megkerdezzuk, hogy a forditas alatt ne kelljen varni
set CIM=
if exist wifi_cim.txt set /p CIM=<wifi_cim.txt
echo  A telefonon: Fejlesztoi beallitasok - Vezetek nelkuli hibakereses - "IP-cim es port".
if defined CIM echo  Legutobb: !CIM! - Enter = ugyanez. Kabelhez ird be: usb
set UJCIM=
set /p UJCIM=" IP-cim es port: "
if "!UJCIM!"=="" set UJCIM=!CIM!

echo.
echo  [1/2] Forditas...
call gradlew.bat assembleTeljesRelease assembleLiteRelease --console=plain -q
if errorlevel 1 (
  echo.
  echo  HIBA: a forditas nem sikerult. Masold ki a fenti hibauzenetet Claude-nak.
  echo  Ha "masik folyamat hasznalja" hibat ir, futtasd ujra ezt a fajlt.
  goto vege
)
if not exist apk mkdir apk
copy /y "app\build\outputs\apk\teljes\release\app-teljes-release.apk" "apk\voicetex-diktalo.apk" >nul
copy /y "app\build\outputs\apk\lite\release\app-lite-release.apk" "apk\voicetex-diktalo-lite.apk" >nul
echo  Forditas kesz.

echo.
echo  [2/2] Telepites...
if /i "!UJCIM!"=="usb" (
  "%ADB%" -d install -r "apk\voicetex-diktalo.apk"
  goto vege
)
"%ADB%" connect !UJCIM! | findstr /i "connected" >nul
if errorlevel 1 (
  echo  HIBA: nem sikerult csatlakozni ide: !UJCIM!
  echo  Nezd meg a telefonon az aktualis portot, es futtasd ujra.
  echo  Ha meg sosem parositottad ezt a gepet, elobb a telepites_wifi.bat-ot futtasd.
  goto vege
)
>wifi_cim.txt echo !UJCIM!
"%ADB%" -s !UJCIM! install -r "apk\voicetex-diktalo.apk"
if errorlevel 1 (
  echo  HIBA: a telepites nem sikerult.
  goto vege
)
echo.
echo  KESZ: az uj verzio fent van a telefonon.
:vege
echo.
pause

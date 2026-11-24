@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
echo.
echo  Voicetex Diktalas - telepites Wi-Fi-n, kabel nelkul (ADB)
echo  ---------------------------------------------------------
for /f "tokens=1,* delims==" %%a in (local.properties) do if "%%a"=="sdk.dir" set SDK=%%b
set SDK=%SDK:\\=\%
set SDK=%SDK:\:=:%
set ADB=%SDK%\platform-tools\adb.exe
if not exist "%ADB%" (
  echo  HIBA: nem talalom az adb.exe-t: %ADB%
  goto vege
)
set APK=apk\voicetex-diktalo.apk
if not exist "%APK%" (
  echo  HIBA: nincs meg az APK: %APK% - futtasd elobb a forditas.bat-ot.
  goto vege
)

echo  A telefonon: Beallitasok - Fejlesztoi beallitasok - Vezetek nelkuli hibakereses: BE,
echo  majd koppints magara a "Vezetek nelkuli hibakereses" sorra.
echo.
set /p ELSO=" Ez az elso parositas ezzel a geppel? [i/n]: "
if /i "!ELSO!"=="i" (
  echo.
  echo  A telefonon: "Eszkoz parositasa parositasi koddal".
  set /p PAR=" Az ott latott IP-cim es port, pl. 192.168.1.23:37123 : "
  set /p KOD=" A 6 jegyu parositasi kod: "
  "%ADB%" pair !PAR! !KOD!
  if errorlevel 1 (
    echo  HIBA: a parositas nem sikerult. Probald ujra, a kod par perc utan lejar.
    goto vege
  )
)

echo.
set CIM=
if exist wifi_cim.txt set /p CIM=<wifi_cim.txt
echo  A telefonon, a "Vezetek nelkuli hibakereses" oldal tetejen: "IP-cim es port".
if defined CIM echo  Legutobb: !CIM! - a port altalaban minden alkalommal mas, Enter = ugyanez.
set UJCIM=
set /p UJCIM=" IP-cim es port: "
if "!UJCIM!"=="" set UJCIM=!CIM!
"%ADB%" connect !UJCIM! | findstr /i "connected" >nul
if errorlevel 1 (
  echo  HIBA: nem sikerult csatlakozni ide: !UJCIM!
  echo  Ellenorizd, hogy a telefon es a gep ugyanazon a Wi-Fi-n van, es a cimet jol irtad.
  goto vege
)
>wifi_cim.txt echo !UJCIM!
echo  Csatlakozva: !UJCIM!
echo.
"%ADB%" -s !UJCIM! install -r "%APK%"
echo.
set /p ENG=" Kell a lebego gomb korlatozott beallitasanak feloldasa is? Csak az elso telepiteskor. [i/n]: "
if /i "!ENG!"=="i" "%ADB%" -s !UJCIM! shell appops set hu.voicetex.billentyuzet ACCESS_RESTRICTED_SETTINGS allow
:vege
echo.
pause

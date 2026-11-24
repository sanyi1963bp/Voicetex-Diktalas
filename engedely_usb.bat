@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo  Voicetex - korlatozott beallitas feloldasa USB-n (ADB)
echo  ------------------------------------------------------
for /f "tokens=1,* delims==" %%a in (local.properties) do if "%%a"=="sdk.dir" set SDK=%%b
set SDK=%SDK:\\=\%
set SDK=%SDK:\:=:%
set ADB=%SDK%\platform-tools\adb.exe
"%ADB%" shell appops set hu.voicetex.billentyuzet ACCESS_RESTRICTED_SETTINGS allow
"%ADB%" shell appops get hu.voicetex.billentyuzet ACCESS_RESTRICTED_SETTINGS
echo.
echo  Ha fent "allow" latszik: most kapcsold be a telefonon a
echo  Voicetex lebego mikrofon kisegito szolgaltatast.
echo.
pause

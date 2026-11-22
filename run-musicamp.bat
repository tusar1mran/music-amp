@echo off
title MusicAmp Launcher
set "SDK=D:\Android\Sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
set "EMU=%SDK%\emulator\emulator.exe"
set "APK=D:\projects\music-amp\app\build\outputs\apk\debug\app-debug.apk"
set "AVD=LightAndroid14"

echo Starting Android Emulator (%AVD%)...
tasklist /FI "IMAGENAME eq qemu-system-x86_64.exe" 2>NUL | find /I "qemu-system-x86_64.exe" >NUL
if errorlevel 1 (
    start "" "%EMU%" -avd %AVD%
) else (
    echo Emulator is already running.
)

echo Waiting for emulator to boot...
"%ADB%" wait-for-device

:wait_boot
for /f "delims=" %%i in ('"%ADB%" shell getprop sys.boot_completed 2^>NUL') do set "BOOT=%%i"
if not "%BOOT%"=="1" (
    timeout /t 2 /nobreak >NUL
    goto wait_boot
)

echo Emulator booted!
"%ADB%" shell pm list packages com.musicamp.poweramp | find "com.musicamp.poweramp" >NUL
if errorlevel 1 (
    if exist "%APK%" (
        echo Installing MusicAmp APK...
        "%ADB%" install -r "%APK%"
    )
)

echo Launching MusicAmp...
"%ADB%" shell am start -n com.musicamp.poweramp/.MainActivity
echo Done!
timeout /t 2 /nobreak >NUL

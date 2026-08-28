@echo off
title AcousticGuard Android Mirror
set "SCRCPY_PATH=C:\Users\rahul\AppData\Local\Microsoft\WinGet\Packages\Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe\scrcpy-win64-v4.1\scrcpy.exe"

if exist "%SCRCPY_PATH%" (
    "%SCRCPY_PATH%" --window-title="AcousticGuard - Android Screen" --always-on-top --stay-awake
) else (
    scrcpy --window-title="AcousticGuard - Android Screen" --always-on-top --stay-awake
)
pause

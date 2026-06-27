@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-daily.ps1" %*

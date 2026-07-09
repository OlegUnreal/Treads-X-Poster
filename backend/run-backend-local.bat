@echo off
setlocal
cd /d "%~dp0.."
"C:\Users\ZEPHYRUS\.jdks\openjdk-26.0.1\bin\java.exe" -jar backend\\target\\social-posting-0.1.0.jar > backend\\run-backend.log 2>&1
if errorlevel 1 exit /b %errorlevel%

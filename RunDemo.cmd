@echo off
cd /d "%~dp0"
node "%~dp0demo\run-demo.cjs"
if errorlevel 1 pause

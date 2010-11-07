@echo off

set CURRENT_DIR=%cd%

set CONFIG_FILE=config.properties

if "%1" == "" goto myConfigFile
set CONFIG_FILE=%1

:myConfigFile
set CONFIG_FILE=%CURRENT_DIR%/%CONFIG_FILE%

set JAVA_EXE="C:\Program Files\Java\jre1.6\bin\java.exe"
if exist %JAVA_EXE% goto okExec

set JAVA_EXE="C:\Program Files\Java\jre1.6.0\bin\java.exe"
if exist %JAVA_EXE% goto okExec

set JAVA_EXE="C:\Program Files (x86)\Java\jre1.6\bin\java.exe"
if exist %JAVA_EXE% goto okExec

set JAVA_EXE="C:\Program Files (x86)\Java\jre1.6.0\bin\java.exe"
if exist %JAVA_EXE% goto okExec

set JAVA_EXE=java.exe

:okExec
%JAVA_EXE% -jar JaFFa.jar %CONFIG_FILE%

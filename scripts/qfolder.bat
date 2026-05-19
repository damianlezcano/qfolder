@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%qfolder.jar"

if not exist "%JAR%" (
    echo ERROR: No se encuentra %JAR%
    exit /b 1
)

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA=%JAVA_HOME%\bin\java.exe"
        goto :check_version
    )
)

where java >nul 2>&1
if %errorlevel% equ 0 (
    set "JAVA=java"
    goto :check_version
)

echo ERROR: Java 21+ no encontrado. Instale OpenJDK 21 o superior.
exit /b 1

:check_version
"%JAVA%" -version 2>&1 | findstr /i "version" >nul
if %errorlevel% neq 0 (
    echo ERROR: No se pudo verificar la version de Java.
    exit /b 1
)

start "" "%JAVA%" -jar "%JAR%" %*
exit /b 0

@echo off
REM ============================================================
REM Design Patterns Pro - Windows 一键编译运行脚本
REM 需要 JDK 25，默认从 F:\enCodingApps\JDK\jdk-25 读取
REM ============================================================
setlocal enabledelayedexpansion

set "JDK_HOME=F:\enCodingApps\JDK\jdk-25"
set "JAVAC=%JDK_HOME%\bin\javac.exe"
set "JAVA=%JDK_HOME%\bin\java.exe"
set "SRC_DIR=src"
set "OUT_DIR=out"

if not exist "%JAVAC%" (
    echo [ERROR] 未找到 JDK：%JDK_HOME%
    echo 请修改 build.bat 中的 JDK_HOME 变量
    exit /b 1
)

echo ============================================================
echo [1/3] 清理输出目录 %OUT_DIR%
echo ============================================================
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"

echo ============================================================
echo [2/3] 编译全部源码（--release 25）
echo ============================================================
dir /s /b "%SRC_DIR%\*.java" > sources.txt
"%JAVAC%" --release 25 -encoding UTF-8 -d "%OUT_DIR%" "@sources.txt"
if errorlevel 1 (
    echo [ERROR] 编译失败
    del sources.txt
    exit /b 1
)
del sources.txt
echo [OK] 编译成功

echo ============================================================
echo [3/3] 依次运行每个模式的 Client
echo ============================================================

set "MAIN_CLASSES="
for /f "delims=" %%f in ('dir /s /b "%OUT_DIR%\*.class" ^| findstr /i "Client.class"') do (
    set "CLS=%%f"
    set "CLS=!CLS:%OUT_DIR%\=!"
    set "CLS=!CLS:\=.!"
    set "CLS=!CLS:.class=!"
    set "MAIN_CLASSES=!MAIN_CLASSES! !CLS!"
)

for %%m in (!MAIN_CLASSES!) do (
    echo.
    echo ------------------------------------------------------------
    echo 运行：%%m
    echo ------------------------------------------------------------
    "%JAVA%" -cp "%OUT_DIR%" %%m
)

echo.
echo ============================================================
echo 全部运行完毕
echo ============================================================
endlocal

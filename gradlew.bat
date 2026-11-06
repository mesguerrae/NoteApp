@rem Minimal Gradle wrapper launcher for Windows.
@if "%DEBUG%"=="" @echo off
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if "%JAVA_HOME%"=="" goto noJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
goto haveJava
:noJavaHome
set JAVA_EXE=java
:haveJava

"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

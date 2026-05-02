@rem Gradle startup script for Windows
@rem
@if "%DEBUG%"=="" @echo off
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
java %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

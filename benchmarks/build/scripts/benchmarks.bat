@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  benchmarks startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Add default JVM options here. You can also use JAVA_OPTS and BENCHMARKS_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windows variants

if not "%OS%" == "Windows_NT" goto win9xME_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\elasticsearch-benchmarks-5.3.4-SNAPSHOT.jar;%APP_HOME%\lib\elasticsearch-5.3.4-SNAPSHOT.jar;%APP_HOME%\lib\jmh-core-1.12.jar;%APP_HOME%\lib\jmh-generator-annprocess-1.12.jar;%APP_HOME%\lib\jopt-simple-4.6.jar;%APP_HOME%\lib\commons-math3-3.2.jar;%APP_HOME%\lib\lucene-core-6.4.2.jar;%APP_HOME%\lib\lucene-analyzers-common-6.4.2.jar;%APP_HOME%\lib\lucene-backward-codecs-6.4.2.jar;%APP_HOME%\lib\lucene-grouping-6.4.2.jar;%APP_HOME%\lib\lucene-highlighter-6.4.2.jar;%APP_HOME%\lib\lucene-join-6.4.2.jar;%APP_HOME%\lib\lucene-memory-6.4.2.jar;%APP_HOME%\lib\lucene-misc-6.4.2.jar;%APP_HOME%\lib\lucene-queries-6.4.2.jar;%APP_HOME%\lib\lucene-queryparser-6.4.2.jar;%APP_HOME%\lib\lucene-sandbox-6.4.2.jar;%APP_HOME%\lib\lucene-spatial-6.4.2.jar;%APP_HOME%\lib\lucene-spatial-extras-6.4.2.jar;%APP_HOME%\lib\lucene-spatial3d-6.4.2.jar;%APP_HOME%\lib\lucene-suggest-6.4.2.jar;%APP_HOME%\lib\securesm-1.1.jar;%APP_HOME%\lib\hppc-0.7.1.jar;%APP_HOME%\lib\joda-time-2.9.5.jar;%APP_HOME%\lib\snakeyaml-1.15.jar;%APP_HOME%\lib\jackson-core-2.8.6.jar;%APP_HOME%\lib\jackson-dataformat-smile-2.8.6.jar;%APP_HOME%\lib\jackson-dataformat-yaml-2.8.6.jar;%APP_HOME%\lib\jackson-dataformat-cbor-2.8.6.jar;%APP_HOME%\lib\t-digest-3.0.jar;%APP_HOME%\lib\HdrHistogram-2.1.6.jar;%APP_HOME%\lib\spatial4j-0.6.jar;%APP_HOME%\lib\jts-1.13.jar;%APP_HOME%\lib\log4j-api-2.7.jar;%APP_HOME%\lib\log4j-core-2.7.jar;%APP_HOME%\lib\log4j-1.2-api-2.7.jar;%APP_HOME%\lib\jna-4.2.2.jar

@rem Execute benchmarks
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %BENCHMARKS_OPTS%  -classpath "%CLASSPATH%" org.openjdk.jmh.Main %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable BENCHMARKS_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%BENCHMARKS_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega

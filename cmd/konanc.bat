@rem
@rem Copyright 2010-2017 JetBrains s.r.o.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem http://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
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

declare -a java_args
declare -a konan_args

while [ $# -gt 0 ]; do
  case "$1" in
    -D*)
      java_args=("${java_args[@]}" "$1")
      shift
      ;;
    -J*)
      java_args=("${java_args[@]}" "${1:2}")
      shift
      ;;
    -X*)
      echo "TODO: need to pass arguments to all the tools somehow."
      shift
      ;;
    --time)
      konan_args=("${konan_args[@]}" --time)
      java_args=("${java_args[@]}" -agentlib:hprof=cpu=samples -Dkonan.profile=true)
      JAVACMD="time $JAVACMD"
      shift
      ;;
     *)
      konan_args[${#konan_args[@]}]=$1
      shift
      ;;
  esac
done

findHome() {
    local source="${BASH_SOURCE[0]}"
    while [ -h "$source" ] ; do
	local linked="$(readlink "$source")"
	local dir="$(cd -P $(dirname "$source") && cd -P $(dirname "$linked") && pwd)"
	source="$dir/$(basename "$linked")"
    done
    (cd -P "$(dirname "$source")/.." && pwd)
}

KONAN_HOME="$(findHome)"

KONAN_JAR="${KONAN_HOME}/konan/lib/backend.native.jar"
KOTLIN_JAR="${KONAN_HOME}/konan/lib/kotlin-compiler.jar"
INTEROP_JAR="${KONAN_HOME}/konan/lib/Runtime.jar"
HELPERS_JAR="${KONAN_HOME}/konan/lib/helpers.jar"
NATIVE_LIB="${KONAN_HOME}/konan/nativelib"
KONAN_CLASSPATH="$KOTLIN_JAR:$INTEROP_JAR:$KONAN_JAR:$HELPERS_JAR"
KONAN_COMPILER=org.jetbrains.kotlin.cli.bc.K2NativeKt
JAVA_OPTS=-ea

#
# KONAN BACKEND INVOCATION
#

java_args=("${java_args[@]} -noverify -Dkonan.home=${KONAN_HOME} -Djava.library.path=${NATIVE_LIB}")

$JAVACMD $JAVA_OPTS ${java_args[@]} -cp $KONAN_CLASSPATH $KONAN_COMPILER "${konan_args[@]}"

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
exit 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
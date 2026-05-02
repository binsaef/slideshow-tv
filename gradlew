#!/usr/bin/env sh
##############################################################################
# Gradle start up script for UN*X
##############################################################################
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
# Default JVM options
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
exec java $DEFAULT_JVM_OPTS \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"

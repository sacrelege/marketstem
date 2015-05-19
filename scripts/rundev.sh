#!/bin/bash

PROJECT_NAME="marketstem"
INSTALL_DIR=~/$PROJECT_NAME
REPO_DIR=~/git/$PROJECT_NAME

mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

SERVER_OPS="-server -XX:MaxMetaspaceSize=256m"
JAR_OP="-jar $REPO_DIR/build/libs/$PROJECT_NAME*.jar"
GC_OPS="-XX:+UseConcMarkSweepGC -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark -XX:+CMSParallelRemarkEnabled -XX:+ParallelRefProcEnabled -XX:CMSInitiatingOccupancyFraction=80 -XX:+UseCMSInitiatingOccupancyOnly"

ps ax | egrep "$PROJECT_NAME.*jar" | grep 'grep' -v | awk '{print $1}' | xargs kill -9 --

java $COMMON_OPS $JAR_OP $GC_OPS MARKETSTEM MARKET_DATA_CACHE AGGREGATE_TICKER

exit 0

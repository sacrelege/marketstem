#!/bin/bash

PROJECT_NAME="marketstem"
INSTALL_DIR=~/$PROJECT_NAME
REPO_DIR=~/git/$PROJECT_NAME
cd "$INSTALL_DIR"

JAR_OP="-jar $REPO_DIR/build/libs/$PROJECT_NAME*.jar"
SERVER_OPS="-server -XX:MaxMetaspaceSize=256m"
WEB_SERVER_OPS="-javaagent:$INSTALL_DIR/newrelic/newrelic.jar -Dnewrelic.enable.java.8"
GC_OPS="-XX:+UseConcMarkSweepGC -XX:+ScavengeBeforeFullGC -XX:+CMSScavengeBeforeRemark -XX:+CMSParallelRemarkEnabled -XX:+ParallelRefProcEnabled -XX:CMSInitiatingOccupancyFraction=80 -XX:+UseCMSInitiatingOccupancyOnly"

if [[ -z $(pgrep -f "$INSTALL_DIR|gradle") ]]
    then
    rm -f "$INSTALL_DIR/nohup.out"
    systemMemory=$( awk '/MemTotal/{print $2}' /proc/meminfo )

    if [ "$systemMemory" -gt 2000000 ]; then
      nohup java -Xmx1600M $SERVER_OPS $GC_OPS $WEB_SERVER_OPS $JAR_OP MARKETSTEM MARKET_DATA_CACHE AGGREGATE_TICKER MARKETSTEM_HTTP >>console.out 2>&1 &
    elif [ "$systemMemory" -gt 1000000 ]; then
      nohup java -Xmx700M $SERVER_OPS $GC_OPS $WEB_SERVER_OPS $JAR_OP MARKETSTEM MARKET_DATA_CACHE MARKETSTEM_HTTP >>console.out 2>&1 &
    fi
fi

exit 0;

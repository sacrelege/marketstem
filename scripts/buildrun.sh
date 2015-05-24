#!/bin/bash

PROJECT_NAME="marketstem"
REPO_DIR=~/git/$PROJECT_NAME
SCRIPTS_DIR="$REPO_DIR/scripts"

cd "$REPO_DIR"

while : ; do
   git pull
   gradle clean shadowJar

   ps ax | egrep "$PROJECT_NAME.*jar" | grep 'grep' -v | awk '{print $1}' | xargs kill -9 --

   [[ ! -f `ls -U build/libs/$PROJECT_NAME*.jar` ]] || break
done

bash "$SCRIPTS_DIR/run.sh"

exit 0

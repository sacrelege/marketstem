#!/bin/bash

PROJECT_NAME="marketstem"
REPO_DIR=~/git/$PROJECT_NAME
SCRIPTS_DIR="$REPO_DIR/scripts"

cd "$REPO_DIR"

while : ; do
   git pull
   gradle clean fatJar

   pgrep -f marketstem.*?jar | xargs kill -9 --

   [[ ! -f `ls -U build/libs/$PROJECT_NAME*.jar` ]] || break
done

bash "$SCRIPTS_DIR/run.sh"

exit 0

#!/bin/bash

PROJECT_NAME="marketstem"
REPO_DIR="$HOME/git/$PROJECT_NAME"

cd "$REPO_DIR"

while : ; do
   gradle clean shadowJar

   ps ax | egrep "$PROJECT_NAME.*jar" | grep 'grep' -v | awk '{print $1}' | xargs kill -9 --

   [[ ! -f `ls -U build/libs/$PROJECT_NAME*.jar` ]] || break
done

bash "$REPO_DIR/scripts/rundev.sh"

exit 0

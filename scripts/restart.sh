#!/bin/bash

PROJECT_NAME="marketstem"
REPO_DIR=~/git/$PROJECT_NAME
SCRIPTS_DIR="$REPO_DIR/scripts"

cd "$REPO_DIR"

git pull
ps ax | egrep "$PROJECT_NAME.*jar" | grep 'grep' -v | awk '{print $1}' | xargs kill -9 --

bash "$SCRIPTS_DIR/run.sh"

exit 0

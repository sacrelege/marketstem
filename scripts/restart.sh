#!/bin/bash

PROJECT_NAME="marketstem"
REPO_DIR="$HOME/git/$PROJECT_NAME"

cd "$REPO_DIR"

git pull

ps ax | egrep "$PROJECT_NAME.*jar" | grep 'grep' -v | awk '{print $1}' | xargs kill -9 --

bash "$REPO_DIR/scripts/run.sh"

exit 0

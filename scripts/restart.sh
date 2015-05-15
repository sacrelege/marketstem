#!/bin/bash

PROJECT_NAME="marketstem"
REPO_DIR=~/git/$PROJECT_NAME
SCRIPTS_DIR="$REPO_DIR/scripts"
echo "$SCRIPTS_DIR"
cd "$REPO_DIR"
git pull
pgrep -f marketstem | xargs kill -9 --
bash "$SCRIPTS_DIR/run.sh"

exit 0

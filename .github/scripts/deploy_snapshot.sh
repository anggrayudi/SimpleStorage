#!/bin/bash

SLUG="anggrayudi/MaterialPreference"
BRANCH="master"
CURRENT_BRANCH=$(${GITHUB_REF##*/})

set -e

if [ "$GITHUB_REPOSITORY" != "$SLUG" ]; then
  echo "Skipping deployment: wrong repository. Expected '$SLUG' but was '$GITHUB_REPOSITORY'."
elif [ "$CURRENT_BRANCH" != "$BRANCH" ]; then
  echo "Skipping deployment: wrong branch. Expected '$BRANCH' but was '$CURRENT_BRANCH'."
else
  echo "Deploying snapshot..."
  ./gradlew :storage:uploadArchives --no-daemon --no-parallel --stacktrace
  echo "Snapshot deployed!"
  ./gradlew closeAndReleaseRepository --stacktrace
  echo "Snapshot released!"
fi

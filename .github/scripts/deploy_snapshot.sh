#!/bin/bash

# Append suffix -SNAPSHOT
sed -ie "s/STORAGE_VERSION.*$/&-SNAPSHOT/g" gradle.properties

SLUG="anggrayudi/SimpleStorage"

set -e

if [ "$GITHUB_REPOSITORY" != "$SLUG" ]; then
  echo "Skipping deployment: wrong repository. Expected '$SLUG' but was '$GITHUB_REPOSITORY'."
elif [ "${GITHUB_REF##*/}" != "master" ]; then
  echo "Skipping deployment: wrong branch. Expected 'master' but was '${GITHUB_REF##*/}'."
else
  echo "Deploying snapshot..."
  ./gradlew :storage:uploadArchives --no-daemon --no-parallel --stacktrace
  echo "Snapshot deployed!"
  ./gradlew closeAndReleaseRepository --stacktrace
  echo "Snapshot released!"
fi

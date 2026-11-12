#!/usr/bin/env bash
# Chooses the artifact a release publishes (publish.yml's "Choose the artifact" step; see the routing table there).
#
# Input, from the environment:
#   EVENT      release | workflow_dispatch
#   TAG        the release's tag (release only)
#   PICKED     the artifact picked (workflow_dispatch only)
#   LIBRARIES  every artifact released by a prefixed tag, space-separated
# Output: `artifact=` and `project=` lines appended to $GITHUB_OUTPUT. Exits 1, naming why, for a tag or pick that
# names no artifact. Tested by release-route-test.sh, which build.yml runs.
set -euo pipefail
: "${GITHUB_OUTPUT:=/dev/null}"
: "${TAG:=}"
: "${PICKED:=}"
artifact=''
if [ "$EVENT" = release ]; then
  # The longest listed library whose name, followed by `-`, starts the tag. Matched against the list, never
  # parsed off the version, so a version with its own `-` (1.0.0-1) still routes.
  for library in $LIBRARIES; do
    case "$TAG" in
      "$library"-*) if [ ${#library} -gt ${#artifact} ]; then artifact="$library"; fi ;;
    esac
  done
  if [ -z "$artifact" ]; then
    case "$TAG" in
      [0-9]*) artifact=web-shared ;;
      *) echo "::error::Tag '$TAG' names no artifact published from this repository"; exit 1 ;;
    esac
  fi
else
  artifact="$PICKED"
fi
project=''
if [ "$artifact" != web-shared ]; then
  for library in $LIBRARIES; do
    if [ "$library" = "$artifact" ]; then
      project=":$library"
    fi
  done
  if [ -z "$project" ]; then
    echo "::error::No artifact '$artifact' is published from this repository"
    exit 1
  fi
fi
echo "Publishing $artifact (task ${project}:publish)"
echo "artifact=$artifact" >> "$GITHUB_OUTPUT"
echo "project=$project" >> "$GITHUB_OUTPUT"

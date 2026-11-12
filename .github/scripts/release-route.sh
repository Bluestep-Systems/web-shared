#!/usr/bin/env bash
# Chooses the artifact a release publishes (publish.yml's "Choose the artifact" step; see the routing table there).
#
# Input, from the environment:
#   EVENT      release | workflow_dispatch
#   TAG        the release's tag (release only)
#   PICKED     the artifact picked (workflow_dispatch only)
#   LIBRARIES  every artifact released by a prefixed tag, space-separated
#   FAMILY     the four DTO artifacts released with the umbrella by a bare tag, space-separated
# Output: `artifact=`, `check_tasks=` (each project's checkReleaseTag) and `publish_tasks=` (each project's publish),
# appended to $GITHUB_OUTPUT. A bare tag, and a pick of web-shared, is the umbrella and every FAMILY member: one version
# line, one contract tag. Any other pick is that artifact alone (a FAMILY member alone is a dispatch-only recovery for
# a run that published some of them). The umbrella is always last, so a run that stops early leaves no umbrella without its
# members, and rerunning the release finishes it: bluestep.library skips an upload whose version is already published. Exits 1, naming why, for a tag or pick that
# names no artifact. Tested by release-route-test.sh, which build.yml runs.
set -euo pipefail
: "${GITHUB_OUTPUT:=/dev/null}"
: "${TAG:=}"
: "${PICKED:=}"
: "${FAMILY:=}"
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
check_tasks=''
publish_tasks=''
if [ "$artifact" = web-shared ]; then
  # The umbrella is published LAST, after every member: web-shared:<v> must never be live without the artifacts it
  # depends on, however the run ends. (build.gradle also orders the umbrella's publish tasks after the members'.)
  check_tasks=":checkReleaseTag"
  publish_tasks=""
  for member in $FAMILY; do
    check_tasks="$check_tasks :$member:checkReleaseTag"
    publish_tasks="$publish_tasks :$member:publish"
  done
  publish_tasks="${publish_tasks# } :publish"
else
  for library in $LIBRARIES $FAMILY; do
    if [ "$library" = "$artifact" ]; then
      check_tasks=":$library:checkReleaseTag"
      publish_tasks=":$library:publish"
    fi
  done
  if [ -z "$publish_tasks" ]; then
    echo "::error::No artifact '$artifact' is published from this repository"
    exit 1
  fi
fi
echo "Publishing $artifact (tasks $publish_tasks)"
echo "artifact=$artifact" >> "$GITHUB_OUTPUT"
echo "check_tasks=$check_tasks" >> "$GITHUB_OUTPUT"
echo "publish_tasks=$publish_tasks" >> "$GITHUB_OUTPUT"

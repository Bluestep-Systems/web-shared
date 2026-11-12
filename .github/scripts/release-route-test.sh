#!/usr/bin/env bash
# Checks release-route.sh against every kind of tag: each library, a version with its own `-` (Codex review,
# secret-files-1.0.0-1), a bare version, and tags that must publish nothing. Run by build.yml; runs anywhere.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
# The list publish.yml passes, read from it so the two cannot drift.
LIBRARIES="$(sed -n 's/^ *LIBRARIES: //p' "$here/../workflows/publish.yml")"
FAMILY="$(sed -n 's/^ *FAMILY: //p' "$here/../workflows/publish.yml")"
export LIBRARIES FAMILY
# The members in FAMILY's order, then the umbrella last: pinned, because a failed run must not leave the umbrella live.
ALL=':web-shared-identity:publish :web-shared-ai:publish :web-shared-usage:publish :web-shared-lookup:publish :publish'
failures=0

expect() { # expect <event> <tag-or-pick> <publish tasks | FAIL>
	local out status
	out="$(mktemp)"
	if [ "$1" = release ]; then
		EVENT=release TAG="$2" GITHUB_OUTPUT="$out" "$here/release-route.sh" >/dev/null 2>&1 && status=0 || status=$?
	else
		EVENT="$1" PICKED="$2" GITHUB_OUTPUT="$out" "$here/release-route.sh" >/dev/null 2>&1 && status=0 || status=$?
	fi
	local got
	if [ "$status" -ne 0 ]; then got=FAIL; else got="$(sed -n 's/^publish_tasks=//p' "$out")"; fi
	rm -f "$out"
	if [ "$got" != "$3" ]; then
		echo "FAIL: $1 '$2' -> $got, expected $3"
		failures=$((failures + 1))
	fi
}

expect release 6.6.0 "$ALL"
expect release 6.6.0-rc1 "$ALL"
expect release secret-files-1.0.0 :secret-files:publish
expect release secret-files-1.0.0-1 :secret-files:publish
expect release deadline-http-1.0.0 :deadline-http:publish
expect release contract-negotiation-1.0.0 :contract-negotiation:publish
expect release harness-contract-1.0.0 :harness-contract:publish
expect release harness-contract-1.0.0-rc-2 :harness-contract:publish
expect release v6.5.0 FAIL
expect release web-shared-6.5.0 FAIL
expect release bogus-1.0.0 FAIL
expect release secret-files FAIL
expect release '' FAIL
expect workflow_dispatch web-shared "$ALL"
expect workflow_dispatch web-shared-ai :web-shared-ai:publish
expect release web-shared-ai-6.6.0 FAIL
expect workflow_dispatch deadline-http :deadline-http:publish
expect workflow_dispatch nope FAIL

[ "$failures" -eq 0 ] && echo "release routing: all cases pass" || exit 1

#!/bin/bash
# Lists only the apps a person can open (launcher entries), not system plumbing.
# Output: one line per app — "package|installed-by-user(yes/no)" — for Claude to present.
. "$(dirname "$0")/env.sh"
third=$(tv shell pm list packages -3 | sed 's/package://' | tr -d '\r')
pkgs=$( { tv shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER; \
          tv shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER; } \
        | tr -d '\r' | grep '/' | sed 's|/.*||;s/^ *//' | sort -u)
skip=$(grep -v '^#' "$ROOT/config/never-disable.txt" | grep -v '^$')
for p in $pkgs; do
  echo "$skip" | grep -qx "$p" && continue
  [[ "$p" == tv.steroid.home ]] && continue
  echo "$third" | grep -qx "$p" && echo "$p|yes" || echo "$p|no"
done

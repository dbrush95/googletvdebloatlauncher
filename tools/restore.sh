#!/bin/sh
# Re-enable whatever tools/debloat.sh disabled. Does not install anything new.
set -eu
cd "$(dirname "$0")"
ADB="${ADB:-adb}"
LIST="${1:-debloat-applied.txt}"
if [ ! -f "$LIST" ]; then
  echo "No $LIST. Nothing was disabled from this folder." >&2
  exit 1
fi
if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No TV on adb." >&2
  exit 1
fi
while IFS= read -r pkg || [ -n "$pkg" ]; do
  pkg=$(printf '%s' "$pkg" | sed 's/#.*//; s/[[:space:]]//g')
  [ -z "$pkg" ] && continue
  if "$ADB" shell pm enable "$pkg" >/dev/null 2>&1; then
    echo "enabled $pkg"
  else
    echo "could not enable $pkg"
  fi
done < "$LIST"

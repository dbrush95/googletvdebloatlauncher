#!/bin/sh
# Re-enable whatever tools/debloat.sh disabled. Does not install anything new.
set -eu
cd "$(dirname "$0")"
ADB="${ADB:-adb}"
LIST="${1:-state/applied.txt}"
if [ ! -f "$LIST" ] && [ -f debloat-applied.txt ]; then
  LIST="debloat-applied.txt"
fi
if [ ! -f "$LIST" ]; then
  echo "No record of a disable. Nothing to restore." >&2
  exit 1
fi
if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No TV on adb." >&2
  exit 1
fi
while IFS= read -r pkg || [ -n "$pkg" ]; do
  pkg=$(printf '%s' "$pkg" | sed 's/#.*//; s/[[:space:]]//g')
  [ -z "$pkg" ] && continue
  if "$ADB" shell pm enable --user 0 "$pkg" >/dev/null 2>&1; then
    echo "enabled $pkg"
  else
    echo "could not enable $pkg"
  fi
done < "$LIST"
echo "Restored from $LIST"

#!/bin/sh
# Disable the packages in debloat.txt. Reversible. Does not uninstall.
# Records what was enabled first, the same idea as CleanYourTV.
# Jacob's TV has to be the Home app before stock launchers are touched.
# Cast and setup are never disabled.
set -eu
cd "$(dirname "$0")"
ADB="${ADB:-adb}"
LIST="${1:-debloat.txt}"
mkdir -p state
if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No TV on adb. Enable USB debugging, then: adb connect TV_IP:5555" >&2
  exit 1
fi
"$ADB" shell pm list packages -e > state/enabled-before.txt
HOME=$("$ADB" shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null || true)
OURS=0
echo "$HOME" | grep -q "com.dbrush.jacobstv" && OURS=1
if [ "$OURS" -eq 1 ]; then
  echo "Jacob's TV is Home. Stock launchers can be disabled."
else
  echo "Jacob's TV is not Home yet. Stock launchers will be left alone."
fi
: > state/applied.txt
while IFS= read -r raw || [ -n "$raw" ]; do
  pkg=$(printf '%s' "$raw" | sed 's/#.*//; s/[[:space:]]//g')
  [ -z "$pkg" ] && continue
  case "$pkg" in
    com.google.android.tungsten.setupwraith|com.google.android.apps.mediashell|com.android.vending|com.google.android.gms)
      echo "skip $pkg (cast, store, or setup)"
      continue
      ;;
    com.google.android.apps.tv.launcherx|com.google.android.tvlauncher|com.google.android.leanbacklauncher)
      if [ "$OURS" -ne 1 ]; then
        echo "skip $pkg (Jacob's TV is not Home)"
        continue
      fi
      ;;
  esac
  if ! "$ADB" shell pm path "$pkg" 2>/dev/null | grep -q "^package:"; then
    echo "skip $pkg (not installed)"
    continue
  fi
  if "$ADB" shell pm disable-user --user 0 "$pkg" >/dev/null 2>&1; then
    echo "disabled $pkg"
    echo "$pkg" >> state/applied.txt
  else
    echo "could not disable $pkg"
  fi
done < "$LIST"
cp state/applied.txt debloat-applied.txt
echo "Restore with: tools/restore.sh"

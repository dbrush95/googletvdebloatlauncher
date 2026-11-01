# Jacob's TV

Low-bloat home screen for Jacob's Vizio, drawn like a Stark HUD.

When the set wakes, the arc reactor comes up and the TV says **Welcome, Jacob.** The home under it stays out of the way: a dock of favorites, the focused name in large type, inputs, link status, settings, and every installed app one click down. Recommendation rows and the stock launcher tiles are not on this grid. Nothing here is uninstalled.

About says: Dallas built this because Dallas kicks ass.

The home shape — dock, shelf, all-apps, inputs, a drifting clock — follows the idea in [CleanYourTV](https://github.com/Gathin23/cleanyourtv). The look, the welcome, and this app are not that project. This build does not disable packages. Hiding a tile is not the same as `pm disable-user`.

## What the remote does

- OK opens the focused tile.
- Down on the dock opens every app. Back closes it.
- Hold OK on a dock tile to move it, take it off the dock, or open app info.
- Hold OK in all-apps to pin that app to the dock (up to 7).
- Inputs tries the TV input switcher, then system settings.
- The gear key opens settings. The input key opens inputs.
- A YouTube remote button opens the YouTube TV app when it is installed.
- After a few quiet minutes the clock drifts so it does not burn a mark. Any key wakes it.
- The welcome plays again when you come home after more than 4 minutes. Set `welcomeAfterMs` to `0` in `MainActivity` if you want it on every return.

## Debloat

The home already hides recommendation rows and the stock launcher tiles. To stop those packages from running, with USB debugging on and Jacob's TV already chosen as Home:

```sh
adb connect TV_IP:5555
tools/debloat.sh
```

That runs `pm disable-user`. It does not uninstall. `tools/restore.sh` turns the same packages back on. Stock launchers are skipped until Jacob's TV owns Home.

## Backgrounds

Wall cycles Rocket (default), Coast, and Void. The rocket is the launch photograph, with the phone chrome removed.

## Welcome

The boot plays a short tone, then a voice: "Welcome, Jacob."

## Install on a Vizio that runs Android TV

Older Vizio SmartCast sets are not Android and cannot sideload this. This is for a Vizio that has Android TV or Google TV, with Developer options.

1. On the TV: Settings → Device Preferences → About → press OK on Build seven times. Open Developer options and turn on USB debugging. Turn on Network debugging if you see it.
2. This repo builds `jacobs-tv.apk` in Actions and attaches it to the `tv` release.
3. Download that APK while signed in (the repo is private). Send it to the TV with Send files to TV, or `adb install -r jacobs-tv.apk`.
4. Open Jacob's TV once. Press Home. Pick Jacob's TV and choose Always.

The launcher does not need an account, does not use the internet, and only checks whether Wi-Fi or Ethernet is up so the Link chip can say so.

## Build

GitHub Actions builds a debug APK on every push to `main`. Android Studio Ladybug or newer can also open this folder and run `assembleDebug`.

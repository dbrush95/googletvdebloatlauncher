# Home

Low-bloat home for Google TV and Android TV, drawn like a Stark HUD. It is not tied to one brand. The same APK is the home screen on Google TV and Android TV sets. Published at [dbrush95/googletvdebloatlauncher](https://github.com/dbrush95/googletvdebloatlauncher).

When the set wakes, the arc reactor comes up and the TV says **Welcome home.** The home under it stays out of the way: a dock of favorites, the focused name in large type, inputs, link status, settings, and every installed app one click down. Recommendation rows and the stock launcher tiles are not on this grid. Nothing here is uninstalled.

About says: Dallas built this because Dallas kicks ass. github.com/dbrush95/googletvdebloatlauncher

The home shape — dock, shelf, all-apps, inputs, a drifting clock — follows the idea in [CleanYourTV](https://github.com/Gathin23/cleanyourtv). The look and the welcome are this build. Once it is the Home app it can disable the stock recommendation and launcher packages. Restore turns that pass back on.

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

The home already hides recommendation rows and the stock launcher tiles. To stop those packages from running, with USB debugging on and this app already chosen as Home:

```sh
adb connect TV_IP:5555
tools/debloat.sh
```

That runs `pm disable-user` and writes a record in `tools/state/`. It does not uninstall. Cast, the Play Store, and setup stay on. `tools/restore.sh` turns the same packages back on. Stock launchers are skipped until this app owns Home. The Debloat panel on the TV does the same pass, and Restore puts that pass back.

## Backgrounds

Wall cycles Rocket (default), Coast, and Void. The rocket is the launch photograph, with the phone chrome removed.

## Welcome

The boot plays a short tone, then a voice: "Welcome home."

## Install on Google TV or Android TV

This is for any set that runs Google TV or Android TV, with Developer options. Sets that are not Android cannot sideload it.

1. On the TV: Settings → Device Preferences → About → press OK on Build seven times. Open Developer options and turn on USB debugging. Turn on Network debugging if you see it.
2. This repo builds `home.apk` in Actions and attaches it to the `tv` release.
3. Download that APK. Send it to the TV with Send files to TV, or `adb install -r home.apk`.
4. Open Home once. Press Home. Pick Home and choose Always.

The launcher does not need an account. Weather and the built-in browser use the network when you open them. The Link chip only checks whether Wi-Fi or Ethernet is up.

## Build

GitHub Actions builds a debug APK on every push to `main`. Android Studio Ladybug or newer can also open this folder and run `assembleDebug`.

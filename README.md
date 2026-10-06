# Light Deck

Control your Home Assistant lights from a Meta Quest or Meta VR Glasses.

Light Deck is a native Android app (Kotlin + Jetpack Compose). On Horizon OS it
runs as a floating 2D panel, so the same APK works on Quest 3/3S and on Meta VR
Glasses, with hands (look and pinch) or controllers.

## Features

- Lights grouped by Home Assistant area, with **All on** / **All off** per room
- Tap a light to toggle it, drag to dim, pick a color preset
- Live updates: changes made anywhere (wall switch, phone, automations) show up immediately
- Finds Home Assistant on your network via mDNS
- Reconnects automatically, with backoff
- Access token stored encrypted with an Android Keystore key
- Large pinch-friendly targets and a dark theme for VR

## Project layout

| Path | What it is |
| --- | --- |
| `ha-client/` | Plain Kotlin/JVM Home Assistant WebSocket client (`HaSession`) and live light model (`LightRepository`). No Android dependency, fully unit tested. |
| `app/` | Android app: Compose UI, ViewModel, settings, mDNS discovery. |
| `gradle/libs.versions.toml` | Dependency versions shared by both builds. |

`ha-client` is a separate Gradle build pulled into the app with `includeBuild`,
so it can be built and tested without the Android SDK.

## Build

Requirements: JDK 17+, and the Android SDK for the app (Android Studio sets this up).

```sh
./gradlew -p ha-client test     # client tests, no Android SDK needed
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug/
```

CI (`.github/workflows/build.yml`) runs both and uploads the debug APK.

## Run on a headset

1. Enable developer mode on the Quest (Meta Horizon phone app → Devices → Developer mode).
2. Install with [Meta Quest Developer Hub](https://developers.meta.com/horizon/documentation/android-apps/meta-quest-developer-hub/)
   or `adb install app/build/outputs/apk/debug/app-debug.apk`.
3. Open **Light Deck** from the app library (Unknown sources).
4. Pick your server or type its address, and paste a long-lived access token
   (Home Assistant → your profile → Security → Long-lived access tokens).

Typing a token on a headset keyboard is slow. Debug builds accept it over adb:

```sh
adb shell am start -n io.github.zbowling.lightdeck/.MainActivity \
  --es ha_url http://homeassistant.local:8123 --es ha_token "$HA_TOKEN"
```

Meta VR Glasses hardware ships in spring 2027. Until then, test on a Quest with
controllers put down (hands only) or in the
[Meta Spatial Simulator](https://developers.meta.com/vr/documentation/android-apps/spatial-sim-overview/).

## Roadmap

- **Sign in with Home Assistant** (OAuth2 login flow) so nobody has to paste tokens.
- **Spatial controls** with the [Meta Spatial SDK](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-overview):
  pin a small control panel next to each real lamp using passthrough and spatial
  anchors, reusing the Compose tiles inside Spatial SDK panels.
- Color temperature control for tunable-white bulbs.
- Upgrade to Android Gradle Plugin 9 (built-in Kotlin).

## References

- [Bring your app to Meta VR Glasses](https://developers.meta.com/vr/essentials/bring-your-app-glasses/)
- [App compatibility for Meta VR Glasses](https://developers.meta.com/vr/essentials/app-compatibility/)
- [Android apps on Horizon OS](https://developers.meta.com/horizon/documentation/android-apps/horizon-os-apps/)
- [Home Assistant WebSocket API](https://developers.home-assistant.io/docs/api/websocket)

# Light Deck

Control your Home Assistant lights from a Meta Quest or Meta VR Glasses.

Light Deck is a native Android app (Kotlin + Jetpack Compose) on Meta's
**Standard Android** build path: Horizon OS runs it as a resizable 2D panel, so the
same APK works on Quest 2/3/3S/Pro and on Meta VR Glasses, with controllers, hands,
or Look and Pinch (look at a control, pinch to select).

## Features

- Lights grouped by Home Assistant area, with **All on** / **All off** per room
- Tap a light to toggle it, drag to dim, pick a color preset
- Live updates: changes made anywhere (wall switch, phone, automations) show up immediately
- Finds Home Assistant on your network via mDNS
- Reconnects automatically, with backoff
- Access token stored encrypted with an Android Keystore key
- Large gaze- and pinch-friendly targets and a dark theme for VR

## Meta VR requirements this app follows

From Meta's Android docs (Create a new app, Look and Pinch for 2D apps, Android
design requirements) and the `hz-android-2d-porting` / `hz-new-project-creation`
agent skills:

| Requirement | Where |
| --- | --- |
| Declare supported devices, including `vrglasses` | `com.oculus.supportedDevices` in the manifest |
| Declare Horizon OS SDK levels (min 69, target 207) | `uses-metavr-sdk` + legacy `uses-horizonos-sdk` |
| Default panel 1024×640dp, usable down to 360×225dp | `<layout>`; headers wrap with `FlowRow` |
| Resizable panel without activity restarts | `resizeableActivity`, `configChanges` |
| Launch as a 2D window | `com.oculus.intent.category.2D` |
| No eye tracking permissions (Look and Pinch review) | none requested; no hand-tracking permission either, since the system handles input for 2D apps |
| Compose UI 1.10+ so the system can infer gaze targets | Compose BOM 2026.03.00 (UI 1.10.5) |
| Horizon OS look, hover/press feedback and 48dp targets | [Meta VR UI Set](https://developers.meta.com/horizon/documentation/android-apps/meta-vr-ui-set-sdk) (`UiSetTheme`, cards, buttons, `Switch`, `Slider`, `TextField`, `Icons.Regular`) instead of Material 3 |
| One interactable element per logical control, with a shape for the system hover | light header row is a single `toggleable` clipped to the card shape; its `Switch` is display-only; servers are clickable cards |
| 48dp minimum targets with space between them | color swatches 48dp with 12dp gaps; UI Set clamps its own targets to 48dp |
| In-app back navigation | "Back" button on the server screen |
| No hover-dependent flows | everything is click-based |
| Android `minSdk` ≥ 29, `targetSdk` 34 for 2D panel apps, arm64 | `app/build.gradle.kts` |

## Project layout

| Path | What it is |
| --- | --- |
| `ha-client/` | Plain Kotlin/JVM Home Assistant WebSocket client (`HaSession`) and live light model (`LightRepository`). No Android dependency, fully unit tested. |
| `app/.../ui/` | Screens and components built on the Meta VR UI Set. `LightTile.kt` is the reference Look and Pinch component. |
| `app/.../data/` | Encrypted settings and mDNS server discovery. |
| `app/.../LightsViewModel.kt`, `MainActivity.kt` | App state and the single activity. |
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

## Using this as a template

Light Deck is meant as a small, canonical Standard Android app for Meta VR Glasses
and Quest. To start your own app from it:

1. Change `namespace`/`applicationId` in `app/build.gradle.kts` before the first install.
2. Keep the manifest block as is: supported devices (with `vrglasses`), Horizon OS SDK
   levels, `<layout>` sizes, `resizeableActivity`, `configChanges`, the 2D category,
   and no eye tracking permissions.
3. Build UI from UI Set components inside `UiSetTheme`, paint the panel with
   `UiSetTheme.colorScheme.background.container.brush`, and use the theme's
   typography roles and spacing scale.
4. Give each logical control exactly one interactable element, clipped to its visual
   shape (see `LightTile.kt`); pass `null` callbacks to nested controls that only
   display state.
5. Check layouts from 360dp to 1280dp wide, and check gaze targets with Meta Spatial
   Simulator's **Show Interactive Elements**.

## AI agent tooling

Meta publishes official agent tooling for Horizon OS development:

- **Meta VR CLI (`metavr`)**: device and app management, Meta Spatial Simulator,
  screenshots, Perfetto traces, and documentation search, also available as an
  MCP server. Install with `curl -fsSL https://developers.meta.com/horizon/install-cli/ | sh`
  or run it with `npx -y metavr`.
- **Meta VR agent skills** ([meta-quest/agentic-tools](https://github.com/meta-quest/agentic-tools)),
  such as `hz-quest-verify-first`, `hz-android-2d-porting`, `hz-metavrx-ui-set`
  and `hz-vr-debug`.

`.claude/settings.json` registers Meta's plugin marketplace and enables the
`meta-vr` plugin, so Claude Code offers to install the skills and the `metavr` MCP
server when you open this repo. Other agents: run `metavr init`.

Useful loops once `metavr` is installed:

```sh
metavr ssim download && metavr ssim start      # Meta Spatial Simulator (has Look and Pinch testing)
metavr app install app/build/outputs/apk/debug/app-debug.apk
metavr app launch io.github.zbowling.lightdeck
metavr capture screenshot -o panel.png
metavr docs search "look and pinch"
```

In the simulator, enable **Show Interactive Elements** to check that each light
tile, swatch, slider and button is detected as a gaze target.

## Roadmap

- **Sign in with Home Assistant** (OAuth2 login flow) so nobody has to paste tokens.
- **Spatial windows** with the [Meta VR Layout SDK](https://developers.meta.com/horizon/documentation/android-apps/meta-vr-layout-sdk):
  per-room control windows placed around the main panel, staying on the Standard
  Android path.
- Only if controls must be pinned to the physical lamps: the
  [Meta Spatial SDK](https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-overview)
  with passthrough and spatial anchors (an immersive app, a much bigger step).
- Color temperature control for tunable-white bulbs.
- Upgrade to Android Gradle Plugin 9 (built-in Kotlin).

## References

- [What's new in Meta VR Glasses](https://developers.meta.com/horizon/documentation/android-apps/whats-new-in-glasses)
- [Create a new app](https://developers.meta.com/horizon/documentation/android-apps/create-app)
- [Look and Pinch for 2D apps](https://developers.meta.com/horizon/documentation/android-apps/gaze-and-hands)
- [Android design requirements](https://developers.meta.com/horizon/documentation/android-apps/design-requirements)
- [Bring your app to Meta VR Glasses](https://developers.meta.com/vr/essentials/bring-your-app-glasses/)
- [App compatibility for Meta VR Glasses](https://developers.meta.com/vr/essentials/app-compatibility/)
- [Android apps on Horizon OS](https://developers.meta.com/horizon/documentation/android-apps/horizon-os-apps/)
- [Home Assistant WebSocket API](https://developers.home-assistant.io/docs/api/websocket)

# Light Deck

Home Assistant light control for Meta Quest and Meta VR Glasses. A Standard
Android (Kotlin + Compose) 2D panel app; no Spatial SDK.

- `ha-client/`: JVM-only Home Assistant WebSocket client. Test with
  `./gradlew -p ha-client test` (no Android SDK needed).
- `app/`: the Android app. Build with `./gradlew :app:assembleDebug`; CI builds the APK.
  UI uses the Meta VR UI Set (`metavrx.uiset.compose.*`), not Material 3. Don't mix
  the two; follow the `hz-metavrx-ui-set` skill and Meta's GalleryVrx sample for APIs.

## Working on Horizon OS code

- Use Meta's `meta-vr` plugin skills (enabled in `.claude/settings.json`). Run
  `hz-quest-verify-first` before changing manifest entries, SDK versions or
  Meta-specific APIs; Horizon OS details change faster than training data.
- Verify against developers.meta.com (or `metavr docs search`) rather than memory.
  `https://developers.meta.com/llms.txt` indexes the docs as Markdown.
- Keep the Look and Pinch rules in the README's "Meta VR requirements" table:
  no eye tracking permissions, Compose UI 1.10+, 48dp targets (60dp primary),
  one interactable element per control, layouts usable down to 360dp wide.

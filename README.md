# xEdgeGestures

Edge-swipe gestures for Android, hosted in an AccessibilityService. Written for handhelds
and phones whose navigation bar is hidden: swipe from an edge to go back, home, open recents,
take a screenshot, show a slider or trigger a custom action.

## Features

- **Edge zones** — configurable left/right/bottom strips with independent actions.
- **Gestures** — swipe, long swipe, double swipe and hold, each mappable.
- **Actions** — back, home, recents, notifications, screenshot, back-key alternatives,
  brightness/volume sliders, app launch, kill-app picker.
- **Root extras** — optional root-only actions (`su --mount-master` when needed).
- **Info bar and slider overlays** — visual feedback for volume/brightness.
- **Survives process death** — the service runs inside an AccessibilityService, so the
  overlays keep working when the app process is killed by the system.
- **Per-device profiles** — different edge sizes for handheld vs phone.

## Tech

Kotlin · Jetpack Compose · Hilt · AccessibilityService · WindowManager overlays · Coil ·
`minSdk 26`, `targetSdk 34`

## Architecture

```
app/src/main/java/com/maiconerick/xedgegestures/
├── service/     accessibility + overlay service
├── overlay/     edge detection and gesture handling
├── actions/     action implementations (back, home, screenshot, sliders...)
├── root/        optional root commands
├── infobar/     feedback overlays
├── killpicker/  custom app-kill dialog
├── slider/      brightness/volume sliders
└── ui/          settings and theme
```

## Permissions

Accessibility service access is required; overlay permission is requested on first launch.

## Build

Requirements: JDK 17, Android SDK 34.

```bash
# local.properties
sdk.dir=/path/to/Android/Sdk

./gradlew assembleDebug
```

## License

MIT — see [LICENSE](LICENSE).

# Contributing

Thanks for considering a contribution. A few practical notes for this fork:

## Development setup

1. **JDK 17** and **Android SDK 36** are required.
2. Clone the repo and add a Maps key:

   ```bash
   echo "MAPS_API_KEY=YOUR_KEY_HERE" >> local.properties
   ```

   For non-map work the build is happy with any non-empty placeholder; the
   key is only consumed at runtime by the map screen.

3. Build a debug APK:

   ```bash
   ./gradlew assembleDebug
   ```

4. Or open the project in Android Studio Koala / Ladybug or newer.

## Coding guidelines

- **Kotlin only** — keep parity with the existing codebase.
- Follow the existing style. The project does not currently use ktlint /
  detekt, but please match indentation (4 spaces), brace placement, and
  import ordering.
- Don't introduce ads, tracking, analytics, or telemetry of any kind.
- Hooks that depend on a specific Android signature **must** be wrapped
  in a `try/catch` with a fallback. The module runs in `system_server`
  on some paths and a single uncaught `NoSuchMethodError` will boot-loop
  the device.

## Pull request checklist

- [ ] `./gradlew assembleDebug` succeeds locally.
- [ ] You have not added a `local.properties` or a real API key to the
      diff.
- [ ] If the change touches `XposedHook.kt`, you have explained which
      Android versions you tested on.
- [ ] If the change adds a new preference, both English (`values/`) and
      Arabic (`values-ar/`) strings are present.
- [ ] [`CHANGELOG.md`](CHANGELOG.md) updated under the unreleased
      section.

## Reporting issues

Useful information to include:

- Device model, Android version, security patch level.
- Xposed framework and version (Vector v2.0, LSPosed Canary, …).
- Steps to reproduce.
- `logcat -s GPS\ Setter,LSPosed,Vector` output if relevant.

## Licence

By contributing you agree that your changes will be released under the
project licence ([GPL-3.0](LICENSE)).

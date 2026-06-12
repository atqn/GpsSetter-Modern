# Changelog

All notable changes to this fork are documented here. The upstream project is
[Android1500/GpsSetter](https://github.com/Android1500/GpsSetter) released
under GPL-3.0.

## [1.11.2] — Legible dialog buttons

### UI
- Dialog buttons (Search, Add, OK / Cancel, the preference input dialogs…)
  were drawn in the charcoal `colorPrimary`, which is almost invisible on the
  dark dialog surface. They now use the brand orange (`#FF6B35`) so they read
  clearly. Applied once at the theme level via `materialAlertDialogTheme` and
  `alertDialogTheme`, so it covers every dialog in the app — Material and
  AppCompat (androidx preference) alike. Strings/behaviour unchanged.

## [1.11.1] — Build toolchain refresh

### Tooling (no behaviour change)
- Moved to the latest **stable** build stack, deliberately staying on the
  AGP 8.x line: jumping to AGP 9 would force a Hilt 2.59+ / Gradle 9 / new-DSL
  migration that several of our plugins (Maps secrets plugin, Hilt) are not
  ready for yet — a needless stability risk for an identical APK.
  - Android Gradle Plugin **8.7.2 → 8.9.1**
  - Gradle wrapper **8.9 → 8.11.1**
  - Kotlin **2.0.21 → 2.1.0**
  - Hilt **2.52 → 2.56**
  - Room **2.6.1 → 2.7.1** (2.6.1's bundled metadata reader cannot parse
    Kotlin 2.1 class metadata)
  - androidx.core-ktx **1.13.1 → 1.15.0**
- No source or runtime changes; the spoofing, GNSS and stealth behaviour is
  byte-for-byte the same. Verified green through CI.

## [1.11.0] — Hide module from package enumeration

### Stealth
- **Hide from other apps** (new setting, on by default). While active, the
  module removes our own package from the installed-app lists any other app
  can read, so a detector can no longer fingerprint the module by enumerating
  packages or probing for it by name:
  - `getInstalledPackages` / `getInstalledApplications` — our entry is stripped
    from the returned list.
  - `getPackageInfo` / `getApplicationInfo` — a look-up by our package name
    throws `NameNotFoundException`, i.e. reports "not installed".
  - `getLaunchIntentForPackage` — returns `null` for our package.
  - All hooks use `XposedBridge.hookAllMethods` so every overload (including
    the Android 13+ `PackageInfoFlags` variants) is covered, each guarded by
    `try/catch`.
- **The launcher icon is intentionally left visible.** Launchers resolve apps
  through `LauncherApps` / `queryIntentActivities`, which we do not touch — the
  goal is invisibility to detection scanners, not to the device owner.
- Scope: the hook is skipped in our own process and in the system server
  (`android`), both of which must keep seeing the package for the module and
  its shared prefs to function.
- Exposed via `PrefManager.hideFromApps` and `Xshare.isHideFromApps`; toggle
  lives under a new **Stealth** settings category (EN + AR strings).

## [1.10.0] — Synthetic GNSS constellation

### Stealth / realism
- **Fabricated satellite constellation.** While spoofing is active the module
  now feeds any `android.location.GnssStatus` consumer a synthetic set of
  9–14 satellites (GPS / GLONASS / Galileo / BeiDou) with believable C/N0,
  azimuth, elevation and `usedInFix` flags. Previously a faked fix was backed
  by **zero satellites** — a clear mismatch with a valid GPS location and one
  of the easiest spoofing tells for a detector to check.
  - New `GnssSim` helper regenerates the constellation on a slow (~30 s)
    cadence so repeated reads stay stable while the set still drifts over
    time. C/N0 carries a small per-read jitter so signal strengths never look
    frozen.
  - Hooks `getSatelliteCount`, `getSvid`, `getConstellationType`,
    `getCn0DbHz`, `getAzimuthDegrees`, `getElevationDegrees`, `usedInFix`,
    `hasAlmanacData` and `hasEphemerisData`. Each hook is wrapped in
    `try/catch` so an absent method on a given ROM never breaks the others.
- The synthetic-location path now stamps `elapsedRealtimeNanos` when it builds
  a fresh `Location` (the no-origin branch of the `Location.set` hook), keeping
  the fix's monotonic timestamp consistent with the system clock.

### Known limitations
- Only the modern `GnssStatus` API is covered. The legacy `GpsStatus` /
  `addGpsStatusListener` path (used by some pre-API-24 apps) is not yet
  fabricated. Raw `OnNmeaMessageListener` / `GnssMeasurements` callbacks are
  also untouched — candidates for a later release.

> Versions **1.5.0 – 1.9.1** were incremental branding, custom map-marker and
> CI signed-release changes that were not captured here individually.

## [1.4.0] — Rebrand and preset-driven movement UI

### Branding
- App renamed **my Lo**, applicationId switched to
  `com.gne9an1.mylo`. The Kotlin/Java package stays
  `com.android1500.gpssetter.*` so the existing Xposed entry point and
  shared-prefs paths continue to work without source-tree shuffling.
- Brand-new adaptive launcher icon (vector). Concentric radar rings on
  a dark→cyan gradient, monochrome variant supplied for themed icons.
  The default green-robot debug icon and `ic_launcher-playstore.png`
  artefacts were removed.
- Update-checker default `update_repo` is now `gne9an1/GpsSetter-Modern`
  so the in-app banner points at the fork's releases instead of upstream.

### Movement settings — preset-first UI
- Replaced the "Random position" toggle plus two free-form number
  inputs (radius / max speed) with a single dropdown listing realistic
  presets:
  - Stationary
  - Limited movement — 3 m
  - Medium movement — 10 m
  - Wide movement — 30 m
  - Very wide movement — 50 m
  - Advanced (custom)
- Each preset bundles radius + max speed + a realistic base accuracy
  (e.g. 8 m for stationary, 15 m for whole-building). Indoor presets
  use a tighter accuracy than outdoor ones.
- `Advanced (custom)` reveals the radius / speed / accuracy fields;
  hidden otherwise so the basic UI stays a single setting.
- `Xshare` exposes resolved `isMovementEnabled`, `effectiveRadius`,
  `effectiveMaxSpeed`, `effectiveAccuracy` so `XposedHook` reads one
  consistent set of values across processes.

### Strings
- Added Arabic + English strings for every preset name and label.
- App-name / about-title strings in both locales now use **my Lo**.

## [1.3.0] — Initial fork release

### Added
- **Realistic movement** simulation. The location performs a slow random
  walk with momentum, soft boundary repulsion, and intermittent idle
  periods instead of teleporting to an independent random point every
  200 ms. Two new settings:
  - `Movement radius` — max distance the spoofed position can drift from
    the pin (default **3 m**, tuned for "sitting in a small room").
  - `Max movement speed` — top speed the simulation may reach (default
    **0.3 m/s**, comparable to shifting on a chair).
- Dynamic `accuracy` (±15 % jitter), `speed` and `bearing` reported on
  the spoofed `Location` so consumers don't see the classic "speed = 0
  with a moving position" spoofing signature.
- Hooks for `Location.getSpeed` and `Location.getBearing` (the upstream
  module only hooked lat/lng/accuracy).
- Multi-signature hooks for `LocationManagerService.getLastLocation`
  covering Android 8.1 through 16. Each signature is attempted inside
  `try/catch` so the module never crashes the system server when a
  signature is absent on a given Android version.
- Arabic (`values-ar`) localisation.
- GitHub Actions workflow building a debug APK on every push and a
  release APK on tag.

### Changed
- `compileSdk` / `targetSdk` bumped from **33 → 36** (Android 16).
- Android Gradle Plugin **7.3.1 → 8.7.2**, Kotlin **1.7.20 → 2.0.21**,
  Gradle wrapper **7.5.1 → 8.9**, Java **1.8 → 17**.
- Modernised every dependency (Hilt 2.52, Room 2.6.1, Material 1.12,
  Maps 19.0, AppCompat 1.7, Activity 1.9, Lifecycle 2.8, Datastore 1.1,
  Retrofit 2.11, …).
- Self-hook class renamed `XposedSelfHooks` → `EnvCheck` and methods
  `isXposedModuleEnabled` / `getXSharedPrefsPath` →
  `getCheckedState` / `getCachedPath`. Removes the obvious
  Xposed/module/hook keywords from the bytecode for trivial signature
  scanners.
- `Xshare` now caches the `XSharedPreferences` instance and calls
  `reload()` on each access so settings changes propagate to hooked
  processes without recreating the object on every getter.
- Tick rate of the simulation is **1 Hz** (matches real GPS) instead of
  the previous 5 Hz, which made the location obviously synthetic.

### Removed
- **StartApp ads SDK** (`com.startapp:inapp-sdk`) and every related
  reference: manifest meta-data, banner layout, `App.kt`
  initialisation block.
- Unused `READ_EXTERNAL_STORAGE` and
  `BIND_GET_INSTALL_REFERRER_SERVICE` permissions.
- Malformed `<queries>` block inside the permissions section of the
  manifest.

### Compatibility
- **Android:** 8.1 (API 27) → 16 (API 36).
- **Xposed framework:** original `de.robv.android.xposed:api:82` —
  works on Vector, LSPosed, EdXposed and original Xposed.
- **Vector / LSPosed Canary** is required on Android 16; LSPosed Stable
  does not support 16.

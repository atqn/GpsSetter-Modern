# GPS Setter — Modernised Fork

[![Build APK](https://img.shields.io/badge/build-GitHub%20Actions-blue)](../../actions)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Android 8.1 - 16](https://img.shields.io/badge/Android-8.1%20--%2016-green)
![Xposed API 82](https://img.shields.io/badge/Xposed-API%2082-orange)

An Xposed module that reports a custom GPS location to apps without enabling
the Android "mock location" developer toggle.

This is a fork of [Android1500/GpsSetter](https://github.com/Android1500/GpsSetter)
v1.2.9 modernised for Android 16, with realistic movement simulation, no ads,
and a few stealth tweaks. See [CHANGELOG.md](CHANGELOG.md) for the full list
of changes.

---

## Features

- 🗺️ Pick any point on Google Maps, search by address, or enter
  `lat,lng` directly.
- 🚶 **Realistic movement** — slow random walk with momentum, soft
  boundary, and idle periods. Configurable radius (default 3 m) and max
  speed (default 0.3 m/s). Tuned to look like a stationary GPS fix
  drifting naturally.
- ⭐ Save favourite locations.
- 🌗 Light / dark / system theme.
- 📡 Hooks `Location` getters globally — covers `LocationManager`,
  `FusedLocationProvider`, and any third-party SDK that consumes a
  `Location` object.
- 🛰️ Optional system-wide hook on `LocationManagerService` with
  multi-signature fallback for Android 8.1 → 16.
- 🚫 No ads, no analytics, no telemetry.

## Requirements

- Rooted Android device, API 27 (Android 8.1) or newer.
- Xposed framework — one of:
  - [JingMatrix/Vector](https://github.com/JingMatrix/Vector) v2.0+
    (recommended, Android 8.1 → 17 Beta).
  - LSPosed Canary (required on Android 16; LSPosed Stable will not
    work).
  - EdXposed or the classic Xposed framework on older Android versions.
- A Google Maps API key (free, see Build below).

## Build

```bash
git clone https://github.com/<your-user>/GpsSetter.git
cd GpsSetter
echo "MAPS_API_KEY=YOUR_KEY_HERE" >> local.properties
./gradlew assembleRelease
```

Requirements:
- **JDK 17**.
- **Android SDK 36** with build-tools 36.x installed.
- A Google Maps API key — create one at
  [Google Cloud Console](https://console.cloud.google.com/google/maps-apis/credentials).

The unsigned release APK ends up in
`app/build/outputs/apk/release/app-release-unsigned.apk`.

### Automated builds

Every push to the default branch builds a debug APK in **GitHub
Actions**. Pushing a tag matching `v*` (e.g. `v1.3.0`) builds a release
APK and attaches it to a GitHub Release automatically. See
[`.github/workflows`](.github/workflows).

## Install

1. Build (or download a release APK) and install it.
2. Open the Xposed manager (Vector / LSPosed) and enable **GPS Setter**.
3. Reboot the device (or soft-reboot via the manager).
4. Open the app, tap a point on the map, and press the play button.

## Usage tips

- Toggle **Realistic movement** in Settings if you want the position
  to drift naturally instead of being a perfect static point.
- Set the **radius** to the size of your real environment (3 m for a
  desk, 10 m for a house, 30 m for a garden).
- Enable **Hook system location** only if a target app is reading
  `getLastKnownLocation` directly from the system service before any
  client-side hook fires.

## Disclaimer

This software is provided for personal experimentation and privacy
research only. Spoofing your location may violate the terms of service
of certain apps and games, and may be illegal in some jurisdictions.
You are solely responsible for how you use it.

## License

GPL-3.0 — same as upstream. See [LICENSE](LICENSE).

## Credits

- Original module: [Android1500/GpsSetter](https://github.com/Android1500/GpsSetter)
- This fork: modernisation, realistic movement, ad removal,
  Android 16 support.

---

# GPS Setter — نسخة مُحدّثة (عربي)

موديول Xposed يعطي التطبيقات موقعاً جغرافياً مزيّفاً بدون تفعيل
خيار "المواقع الوهمية" في إعدادات المطور.

## أبرز التغييرات في هذا الـ Fork

- **حركة واقعية** بدل القفزات العشوائية: نموذج مشي عشوائي بطيء مع
  زخم وفترات سكون، نصف القطر والسرعة قابلين للتحكم من الإعدادات
  (الافتراضي 3 م و 0.3 م/ث — مناسب لشخص جالس في غرفة صغيرة).
- دعم **Android 16** (API 36) كاملاً مع توقيعات هوك متعددة
  للإصدارات 8.1 → 16.
- **حذف كامل لإعلانات StartApp** ومتعلقاتها.
- إخفاء بسيط لأسماء كلاسات الـ self-hook ضد فاحصات الـ signature.
- ترجمة عربية للواجهة.

## المتطلبات

- جهاز مروت (Root)، Android 8.1 أو أحدث.
- إطار Xposed: **Vector** (موصى به) أو LSPosed Canary أو EdXposed.
- مفتاح Google Maps API مجاني.

## البناء

```bash
git clone https://github.com/<your-user>/GpsSetter.git
cd GpsSetter
echo "MAPS_API_KEY=YOUR_KEY_HERE" >> local.properties
./gradlew assembleRelease
```

يحتاج **JDK 17** و **Android SDK 36**.

## الترخيص

GPL-3.0 — مثل الأصل.

# Naisaru Launcher

Native Android home-screen replacement built with Kotlin and Jetpack Compose.

## Version 0.3.0

- Gesture to open the app drawer by swiping upward from anywhere on the home screen.
- Long press empty home space for DIY / wallpaper / themes / settings / widgets.
- Freeform app icons and transparent image overlays: move, pinch to scale, rotate with two fingers.
- Dedicated corner rotation handle when an object is selected in DIY mode.
- App drawer with bottom category toolbar: icon + name, similar to a One UI Home Up layout.
- Edit category names and icons, and create/delete categories.
- Long-press and drag app icons onto a bottom category to move them; drag onto another app to place before it.
- Spring animations when switching app categories.
- Local settings persisted in SharedPreferences.

## APK

GitHub Actions builds a debug APK after a push to the main branch. Go to [Actions](https://github.com/naisaru/Naisaru-launcher/actions), select the latest successful **Build Android APK** run and download **Naisaru-Launcher-debug** from **Artifacts**. Unzip the package to install the APK.

This is an experimental build. Some advanced Liquid Glass rendering, system gestures, Home Up integration, and edge cases remain under development. Android cannot provide all One UI system-level behaviors to third-party launchers.

## Build locally

Use Android Studio with JDK 17 and Android SDK 35, or run `gradle :app:assembleDebug` with Gradle 8.9.

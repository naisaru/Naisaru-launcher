# Naisaru Launcher

A native Android home-screen replacement made with Kotlin and Jetpack Compose.

## Version 0.4.0

The design uses dark and green panels in the spirit of a customizable Smart-style launcher. It is **an independently developed Android app**, not an edited or redistributed Smart Launcher APK.

### DIY home-screen editor

- Long-press an empty area to open the edit menu, then select **Редактировать**
- Add Android application icons, system widgets, and transparent PNG/WebP images
- Move items with one finger; pinch with two fingers to scale and rotate
- Tap an item to select it; drag the green handle at its lower-right corner for precise rotation
- Adjust size using the slider; reset rotation or delete the selected item
- **Привязка к сетке** toggles a visible alignment grid and snapping
- Select the grid spacing (16, 24, 32, 48 or 64 dp); turn snapping off for fully free placement
- For images, choose **Без действия**, **Запускать приложение** or **Открыть ссылку**
- Positions, sizes, rotation angles, grid preferences and tap actions persist on the device

### App drawer

- Swipe up from anywhere on the home screen to open the drawer
- Categorized apps with a bottom group toolbar
- Long-press and drag an app icon onto a group to change its category
- Long-press and drag an app over another app to change its ordering
- Change group names and icons; add or delete groups
- Spring animations for group switching

## Installation

Open [GitHub Actions](https://github.com/naisaru/Naisaru-launcher/actions), select the latest successful **Build Android APK** run, and download the **Naisaru-Launcher-debug** artifact. Unzip the archive to install the APK on Android.

## Build

Requires JDK 17, Android SDK 35, and Gradle 8.9.

```sh
gradle :app:assembleDebug
```

This is an experimental launcher. Building successfully does not guarantee correctness on every device. Samsung One UI system transitions and internal Home Up features are outside the control of a third-party launcher.

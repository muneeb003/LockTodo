# LockTodo

A to-do app that puts your tasks on your Android lock screen, on your own wallpaper, with a home-screen widget.

## Features
- **Task list**: add, tick off, delete, and clear finished tasks. Everything is saved on the phone.
- **Lock-screen view**: when you turn the screen off, your tasks appear over the lock screen with a clock and date. Tap a task to tick it off. Swipe up or tap to unlock as usual (your normal PIN or fingerprint still applies).
- **Your own wallpaper**: in Settings, tap **Choose photo** to use any picture as the lock-screen view's background.
- **Your notifications on the lock screen**: messages and alerts from your other apps show under your tasks. Tap one to unlock and open it, tap ✕ to dismiss it, or **Clear all**. You can hide message text for privacy.
- **Lock-screen notification**: pending tasks also appear in your notifications (on by default).
- **Home-screen widget**: lists your tasks. Tap a task to tick it off, or tap **+** to open the app.

## Build & install
1. Install [Android Studio](https://developer.android.com/studio) (any version from 2024.2 onward).
2. **File → Open** and select this `LockTodo` folder. Let Gradle sync; it downloads the SDK pieces it needs.
3. Plug in your phone with USB debugging on, then press **Run ▶**.
   - Or from a terminal: `./gradlew assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`.

Requires Android 9 (API 28) or later.

## First-time setup on the phone
1. Open LockTodo and allow notifications.
2. Tap ⚙ **Settings → Show tasks on lock screen**. Android opens the **"Display over other apps"** page; turn it on for LockTodo, then go back.
3. Turn on **Show my notifications** and allow **Notification access** for LockTodo.
4. **Choose photo** to set your wallpaper, then tap **Preview lock screen** to check it.
5. Tap **Add widget to home screen** (or long-press the home screen → Widgets → LockTodo).

## Notes
- While the lock-screen view is on, Android requires a small persistent notification. LockTodo uses it to show your tasks.
- On Xiaomi, Oppo, Vivo, Huawei and some Samsung phones, also allow **"Show on lock screen" / "Autostart"** for LockTodo and set battery usage to **Unrestricted**. Otherwise the system may stop it in the background.
- The view hides during phone calls and video calls.

## Code map
| File | What it does |
|---|---|
| `TodoStore.kt` | Saves tasks and keeps the app, lock screen, notification and widget in sync |
| `MainActivity.kt` | Task list and Settings screens |
| `LockScreenActivity.kt` | The lock-screen view (clock, wallpaper, tasks, swipe to unlock) |
| `LockScreenService.kt` | Opens the lock-screen view each time the screen turns off |
| `Wallpaper.kt` | Stores and loads your chosen photo |
| `NotificationsService.kt` | Reads your other apps' notifications for the lock-screen view |
| `TodoWidget.kt` | Home-screen widget |
| `LockScreenNotifier.kt` | Lock-screen notification |

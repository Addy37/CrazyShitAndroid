# CrazyShit Unofficial v1.4.2

A runtime compatibility hotfix for the v1.4 modern UI on Android 12 and newer.

## Fixed

- Fixes the three-dot app menu crashing on Android 12+ devices.
- Fixes the in-app mini-player crashing when returning from native video playback.
- Corrects the Android 12+ theme override so Material 3 widgets use the same compatible theme as the rest of the app.
- Adds explicit Android 13+ Back handling inside the native video player.
- Back from native video now follows the app's **Minimize player on Back** setting instead of letting the system finish the player unexpectedly.
- Keeps the v1.4 Material 3 UI, Settings, Watch Later, player gestures, adaptive icon, ad blocking, native video player, and mini-player.

## Root cause

The base app theme was Material 3, but the Android 12+ `values-v31` theme still inherited the platform `Theme.Material.NoActionBar`. Material 3 controls such as the bottom sheet and mini-player card can throw a runtime theme-enforcement exception under that mismatch even though the project compiles successfully. v1.4.2 makes the Android 12+ theme consistently Material 3.

The player Activity also had Android's predictive Back system enabled without registering its own `OnBackInvokedCallback`, while its minimize-to-browser behavior lived in `onBackPressed()`. v1.4.2 registers the platform Back callback directly and routes it through the same minimize/return logic used by the player's Back button.

This project remains unofficial and is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

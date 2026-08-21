# CrazyShit Unofficial v1.4.1

A startup hotfix for the v1.4 modern-UI release.

## Fixed

- The WebView is visible immediately again instead of being held fully transparent until `onPageFinished()` fires.
- The Media3 mini-player UI is no longer constructed during app startup.
- Mini-player views are now created lazily only when a video is actually minimized.
- Keeps the v1.4 Material 3 UI, Settings, Watch Later, player gestures, adaptive icon, ad blocking, and native video features.

## Why this update

The v1.4.0 build compiled successfully in CI, but the new startup fade and eager mini-player initialization were too aggressive for real-device WebView startup and could leave the app looking completely blank or fail during initialization. v1.4.1 restores a conservative startup path while retaining the v1.4 features.

This project remains unofficial and is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

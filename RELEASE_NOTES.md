# CrazyShit v2.0.8

CrazyShit 2.0.8 fixes the More section in landscape and makes it match the rest of the adaptive landscape UI.

## What's new

- Landscape More no longer opens the oversized portrait-style bottom sheet
- Added a compact right-side More panel designed specifically for landscape
- The landscape More panel is scrollable, so every action stays reachable on short screens
- More actions are arranged in a 2-column layout to use landscape width better
- Added a dedicated close button and background dimming for a cleaner modal feel
- View style, Settings, Login / account, Library, Categories, My profile, Open full website, and Check for updates remain available
- Portrait keeps the existing More bottom sheet unchanged

## Existing landscape improvements

- Landscape uses a slim left-side navigation rail instead of the tall bottom navigation bar
- The top bar is shorter and hides the redundant subtitle
- Home, Trending, and Memes automatically use a denser 2-column grid on normal landscape phones
- Compact-height landscape phones use tighter rail spacing so the selected Home item is not clipped
- Chaos keeps its immersive landscape behavior and hides the rail and header while playing fullscreen
- Rotating back to portrait restores the normal portrait layout and feed style

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.0.8 can install over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

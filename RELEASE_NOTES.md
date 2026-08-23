# CrazyShit v2.0.6

CrazyShit 2.0.6 is a landscape-focused UI update that keeps the portrait experience intact while making wide-screen browsing feel intentional instead of stretched.

## What's new

- Landscape now replaces the tall bottom navigation bar with a slim left-side navigation rail
- The landscape rail keeps Home, Trending, Chaos, Memes, and More within easy reach while giving content more vertical room
- The top bar becomes much shorter in landscape, hides the redundant subtitle, and keeps Search easy to reach
- Home, Trending, and Memes automatically use a denser 2-column grid on normal landscape phones
- Extra-wide screens can expand native feeds to 3 columns and Categories to 4 columns
- Native feed margins and padding are tightened in landscape to show more content at once
- Rotating back to portrait restores the existing portrait navigation and the user's normal feed style
- Chaos keeps its immersive landscape behavior and hides the navigation rail and header while playing fullscreen
- The adaptive layout responds at runtime when the device rotates instead of requiring an app restart

## Existing Chaos improvements

- Press and hold the video to temporarily play at 2x speed, then release to return to the prior speed
- Chaos remembers your mute state between clips and app sessions
- Not interested permanently hides a clip from Chaos on the current device
- Chaos controls and the scrubbable progress bar fade away after inactivity and return with a tap
- Increased Chaos look-ahead keeps nearby clips prepared for smoother next/previous swipes
- Preloaded clips do not get written to playback history unless they were actually started

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.0.6 can install over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

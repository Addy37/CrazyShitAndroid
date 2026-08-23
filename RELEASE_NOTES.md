# CrazyShit v2.4.2

CrazyShit 2.4.2 brings the native Home feed closer to the structure of CrazyShit.com by adding the site's real date/group headers between video sections.

## Native feed section headers

- Home now shows section labels such as **TODAY'S CRAZY SHIT**, **THURSDAY AUGUST 20**, and **WEDNESDAY AUGUST 19** in the same order exposed by the website
- Only the useful section title is carried into the app; Daily Rant text and header comment counts are intentionally omitted
- Headers use a compact native treatment with the first word highlighted in CrazyShit yellow and the rest in white
- Section titles come from the live website structure instead of being generated from the phone's date

## View-style compatibility

- Large cards keep their existing layout underneath each section header
- Compact rows keep their existing layout underneath each section header
- In 2-column Grid mode, each section header automatically spans both columns and starts the next group on a clean row
- Section rows are not clickable and do not interfere with thumbnail loading, comments, watch-state badges, or card actions

## Existing behavior kept

- Continue and Watched indicators from 2.4/2.4.1 remain unchanged
- Saved playback position and resume feedback remain unchanged
- Home feed scrolling and pagination remain intact
- Trending, Memes, Chaos, Library, the floating bottom dock, and video playback behavior are unchanged
- Chaos portrait controls remain untouched

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.4.2 installs over earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

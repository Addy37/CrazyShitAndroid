# CrazyShit v2.4.5

CrazyShit 2.4.5 fixes the Home section-header layout in landscape mode while keeping the safe native header architecture introduced in 2.4.4.

## Landscape section-header fix

- Section headers now span the full feed width in landscape instead of occupying one grid column
- The first row of videos begins underneath the header as a normal 2-column or 3-column row
- Landscape's adaptive grid keeps the section span rule when it creates its denser layout
- The span rule automatically follows the current landscape column count

## Portrait behavior kept

- Portrait Large, Compact and Grid behavior remains unchanged
- TODAY'S CRAZY SHIT and weekday/date headers keep the same yellow-first-word styling
- Daily Rant text and header comment counts remain omitted

## Existing behavior kept

- Continue and Watched indicators remain unchanged
- Playback progress and resume feedback remain unchanged
- Saved positions, history and feed scrolling remain intact
- Trending, Memes, Chaos, Library, the floating bottom dock and video playback behavior are unchanged

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.4.5 installs over 2.4.4 and earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

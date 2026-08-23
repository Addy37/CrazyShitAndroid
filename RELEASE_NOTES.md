# CrazyShit v2.4.3

CrazyShit 2.4.3 is an emergency stability fix for the startup crash introduced by the 2.4.2 feed section-header experiment.

## Startup crash fix

- Removes the 2.4.2 runtime section-header injector that could mutate RecyclerView data while the Home feed was laying itself out
- Restores the stable Home feed path used by 2.4.1
- Removes the experimental reflection/post-layout header helper entirely from the running app

## Existing behavior kept

- Continue and Watched indicators remain unchanged
- Continue badge polish and resume feedback from 2.4.1 remain unchanged
- Saved playback position, history and feed scroll behavior remain intact
- Large, Compact and Grid card layouts remain unchanged
- Trending, Memes, Chaos, Library, the floating bottom dock and video playback behavior are unchanged
- Chaos portrait controls remain untouched

## Section headers

The website-style date headers are temporarily disabled in this emergency build. They will be rebuilt as first-class native feed items instead of modifying an already-running RecyclerView.

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.4.3 installs over 2.4.2 and earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

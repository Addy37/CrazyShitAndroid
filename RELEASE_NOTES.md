# CrazyShit v2.4.1

CrazyShit 2.4.1 refines the watched and Continue treatment introduced in 2.4.0 without changing the underlying playback-history behavior.

## Watch-state visual polish

- Continue badges are smaller and less dominant over thumbnails
- Continue labels now use the cleaner **Continue · 0:50** format
- The orange Continue background is darker and slightly more transparent
- Watched badges are tightened to match the new badge proportions
- Partially watched thumbnails use a softer center play overlay so the resume state reads first
- The playback progress line keeps a faint dark track behind the orange fill for better readability at low progress
- Newly attached feed cards receive the same treatment automatically while scrolling

## Resume feedback

- Reopening an unfinished video with a saved position briefly shows **Resuming at 0:50**
- The resume message only appears when remembered playback position is enabled
- Completed videos and very short plays do not show the resume confirmation

## Existing behavior kept

- Back returns normally to the existing feed and preserves the browsing position
- Unfinished videos still resume close to where playback stopped
- Completed videos continue to show **✓ Watched**
- Home, Trending, Grid and Compact layouts keep their existing watch-state data
- Picture-in-Picture remains separate from the retired in-app mini-player
- Chaos playback and portrait controls remain untouched
- The floating bottom dock remains unchanged

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.4.1 installs over earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

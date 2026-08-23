# CrazyShit v2.5.0

CrazyShit 2.5.0 upgrades the main browse navigation around the site's Series and Categories collections while keeping Home and Chaos behavior intact.

## New main navigation

- The main dock is now **Home | Series | Chaos | Categories | More**
- Series replaces the old Trending tab
- Categories replaces the old Memes tab
- Chaos stays in the featured center position
- The same labels and icons are applied to the adaptive landscape navigation rail

## Series

- Series is now a first-class native browse tab
- Series cards use the site's artwork and collection titles
- The parser uses the dedicated Series listing when available and can fall back to the Popular Series block on Home if the listing route changes
- Tapping a series opens its videos in a native collection feed instead of dropping directly into the website

## Categories with artwork

- Categories now uses visual thumbnail cards inspired by the website instead of text-only boxes
- Duplicate image/title links from the site are merged into one native category card
- Tapping a category opens its videos in the same native collection feed
- Category and Series grids stay 2-column in portrait and automatically become denser in landscape

## Trending and Memes

- Trending and Memes are no longer primary tabs
- Both remain available under **More → Browse**
- They open in the reusable native feed browser

## Existing behavior kept

- Home section/date headers remain unchanged
- Continue, Watched, playback progress and resume feedback remain unchanged
- Saved positions, History and Watch Later remain intact
- Chaos playback and portrait controls remain unchanged
- The floating dock and landscape rail styling remain intact

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.5.0 installs over v2.4.5 and earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.
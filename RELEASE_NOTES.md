# CrazyShit v2.5.4

CrazyShit 2.5.4 changes how Series and Categories thumbnails are collected from CrazyShit.com.

The site serves the card artwork as normal JPEG thumbnails from `media.crazyshit.com/thumbs/...`, but those URLs are populated only after the cards participate in a real browser viewport. Earlier releases loaded the listing page in an unattached background WebView, so the titles appeared while the site's lazy image loader often never fired.

## Series and Categories artwork fix

- Attaches the browse resolver WebView behind the real app content at full viewport size
- Keeps the renderer nearly transparent and non-interactive
- Forces common lazy image attributes into active image and background sources
- Marks images for eager loading
- Sweeps the rendered page vertically to trigger viewport-based lazy loading
- Waits for the lazy render pass before reading final thumbnail URLs
- Scans normal image sources, lazy attributes, CSS backgrounds and generated style layers
- Keeps the native Series and Categories grids unchanged
- Keeps direct artwork and existing cached artwork when already available

## Confirmed site behavior

CrazyShit browse artwork resolves to direct media thumbnails such as:

`https://media.crazyshit.com/thumbs/2026/08/6dab6ddc.jpg`

The filename hash is assigned by the site, so the app must capture the resolved URL after the site's browser-side loader runs rather than trying to build the URL from a Series or Category name.

## Existing behavior kept

- Home section/date headers remain unchanged
- Continue, Watched, playback progress and resume feedback remain unchanged
- Saved positions, History and Watch Later remain intact
- Chaos playback and portrait controls remain unchanged
- Trending and Memes remain available under **More → Browse**
- Portrait and rotated navigation layouts remain unchanged

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.5.4 installs over v2.5.3 and earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

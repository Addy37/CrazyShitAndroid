# CrazyShit v2.5.2

CrazyShit 2.5.2 fixes the remaining blank artwork on Series and Categories by widening the rendered card scan to match how the website actually paints browse thumbnails.

## Browse thumbnail fix

- Keeps **Home | Series | Chaos | Categories | More** unchanged
- Keeps the native Series and Categories grids unchanged
- Scans every rendered element inside each Series/Category card instead of only obvious image and lazy-load nodes
- Reads normal CSS backgrounds plus `::before` and `::after` artwork layers
- Also checks mask, border-image, list-style image and CSS properties containing `url(...)`
- Waits through additional lazy-render passes before giving up
- Ignores the resolver's internal `about:blank` cleanup page so Series and Categories cannot interfere with each other's artwork pass
- Existing direct image URLs still keep priority
- Glide keeps the site's User-Agent, Referer and cookies when loading resolved artwork

## Existing behavior kept

- Home section/date headers remain unchanged
- Continue, Watched, playback progress and resume feedback remain unchanged
- Saved positions, History and Watch Later remain intact
- Chaos playback and portrait controls remain unchanged
- Trending and Memes remain available under **More → Browse**
- Portrait and landscape navigation layouts remain unchanged

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.5.2 installs over v2.5.1 and earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

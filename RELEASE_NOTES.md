# CrazyShit v2.5.1

CrazyShit 2.5.1 fixes the missing artwork on the new Series and Categories tabs introduced in 2.5.0.

## Series and Categories artwork fix

- Keeps the new **Home | Series | Chaos | Categories | More** navigation unchanged
- Keeps the native 2-column Series and Categories grids unchanged
- Adds a rendered browse-artwork resolver for cases where the website applies card images through lazy JavaScript or computed CSS instead of exposing a usable image URL in the raw HTML
- The resolver loads the Series/Categories listing page once and maps each collection URL to the artwork shown by the rendered website
- Direct image URLs found by the existing parser still take priority
- Series can also read artwork from the Home Popular Series block when the dedicated Series listing does not expose every image
- Glide keeps the site's User-Agent, Referer and cookies when loading resolved artwork

## Existing behavior kept

- Home section/date headers remain unchanged
- Continue, Watched, playback progress and resume feedback remain unchanged
- Saved positions, History and Watch Later remain intact
- Chaos playback and portrait controls remain unchanged
- Trending and Memes remain available under **More → Browse**
- Portrait and landscape navigation layouts remain unchanged

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.5.1 installs over v2.5.0 and earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

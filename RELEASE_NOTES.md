# CrazyShit v2.5.3

CrazyShit 2.5.3 adds a second, independent artwork fallback for Series and Categories when the website listing pages do not expose their lazy-loaded card images.

## Series and Categories thumbnail fallback

- Keeps the existing rendered listing-page artwork resolver as the first choice
- Blank Series and Category cards now read the first page of their own collection and reuse the first usable video thumbnail
- If that collection thumbnail is also lazy-rendered, the app reuses the normal video thumbnail resolver already used by native feeds
- Only Series and Category cards currently being bound on screen trigger the fallback work
- Fallback collection requests are limited to three worker threads
- Resolved collection artwork is cached locally so later visits load it immediately
- Direct and listing-page artwork still keep priority and are never replaced by the fallback
- Local frame thumbnails are supported if the normal media resolver has to extract a video frame

## Existing behavior kept

- Home section/date headers remain unchanged
- Continue, Watched, playback progress and resume feedback remain unchanged
- Saved positions, History and Watch Later remain intact
- Chaos playback and portrait controls remain unchanged
- Trending and Memes remain available under **More → Browse**
- Portrait and rotated navigation layouts remain unchanged

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.5.3 installs over v2.5.2 and earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

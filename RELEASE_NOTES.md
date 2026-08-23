# CrazyShit v2.4.4

CrazyShit 2.4.4 brings the website-style Home feed section headers back with a native implementation that does not modify RecyclerView data after layout has started.

## Native Home section headers

- Home recognizes the site's real group labels such as **TODAY'S CRAZY SHIT**, **THURSDAY AUGUST 20**, and **WEDNESDAY AUGUST 19**
- Only the main section title is shown; Daily Rant text and header comment counts stay omitted
- Headers are compact, non-clickable native rows with the first word highlighted in CrazyShit yellow and the rest in white
- Section items are created by the repository while the page is parsed, before RecyclerView receives the data

## Safe view-style support

- Large keeps full-width headers above the existing large cards
- Compact keeps full-width headers above the existing compact rows
- Grid uses RecyclerView's normal SpanSizeLookup so each header spans both columns
- No reflection, runtime list injection, lifecycle header hook, or post-layout notifyDataSetChanged workaround is used
- If the site's header markup cannot be recognized, the app simply falls back to the normal working media feed

## Existing behavior kept

- Continue and Watched indicators remain unchanged
- Continue badge polish, playback progress and resume feedback remain unchanged
- Saved playback position, history and feed scrolling remain intact
- Trending, Memes, Chaos, Library, the floating bottom dock and video playback behavior are unchanged
- Chaos portrait controls remain untouched

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.4.4 installs over 2.4.3 and earlier signed stable releases while retaining app data and watch history.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

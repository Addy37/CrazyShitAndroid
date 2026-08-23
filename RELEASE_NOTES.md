# CrazyShit v2.2.1

CrazyShit 2.2.1 refines the flashy 2.2 UI after real-device testing, focusing on a cleaner navigation bar, a stronger Chaos centerpiece, tighter compact cards and a more useful collapsing header.

## Navigation refinement

- Slimmed the floating portrait bottom navigation so it takes up less vertical space
- Removed the heavy warm outer outline and replaced it with a quieter dark border
- Added a moving orange-tinted active pill that glides between normal selected tabs
- Reduced inactive-label prominence so the selected destination reads more clearly
- Tightened navigation label sizing for a lighter, less crowded look
- Kept the floating rounded shape and app-wide orange accent

## Raised Chaos centerpiece

- Chaos now uses a raised circular orange icon treatment in the center of the nav
- The Chaos icon stays white against the orange button for stronger contrast
- Selecting Chaos gives the center button a small spring-style lift without changing Chaos playback behavior
- The normal moving active pill hides on Chaos so the center button itself becomes the selected indicator

## Compact feed cleanup

- Reduced compact-card title size slightly while retaining a two-line maximum
- Shortened large view counts into forms such as 65.6K views and 1.2M views
- Compact and grid comments now use the tighter 💬 15 style instead of longer comments text
- Removed uploader text from compact metadata so titles and counts have more breathing room
- Reduced the previous depth effect slightly so compact cards remain crisp while scrolling

## Better collapsing header

- The feed header now collapses to 52dp instead of simply dimming as a block
- Subtitle fades away first during collapse
- Logo and search control scale down slightly while the title remains readable
- Switching tabs expands the header again so every destination starts with clear context
- Landscape and Chaos playback remain excluded from scroll-driven header changes

## v2.2 polish kept

- Animated skeleton loading on normal feeds
- Smooth horizontal paging motion
- Mini-player entrance and live orange progress line
- Video-detail fade/slide transition
- Animated splash artwork
- Grouped More screen from v2.1

## Chaos behavior kept

- Portrait Chaos title and action controls remain visible continuously
- Empty lower-overlay space passes taps and long presses through to the video
- Hold for 2x playback
- Swipe up/down between clips
- Auto-advance when a clip ends
- Remember mute state
- Not interested filtering
- Scrubbable progress bar with inactivity fade
- Preloading and recent-view avoidance
- The ambient/blur experiment remains disabled

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.2.1 can install over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

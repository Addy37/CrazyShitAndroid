# CrazyShit v2.2.0

CrazyShit 2.2.0 is a flashier visual-polish release that makes the native app feel more animated and premium while leaving the working Chaos playback behavior alone.

## Flashier navigation and motion

- Portrait bottom navigation now floats above the app shell with rounded corners, stronger elevation and a subtle orange outline
- Selected tabs animate with a short scale response; Chaos stays visually emphasized in the center
- Reselecting a tab gives it a quick pulse instead of feeling static
- Home, Trending and Memes keep the existing horizontal page motion and now gain extra depth while scrolling
- Feed cards subtly scale and fade based on distance from the center of the screen for a light parallax-style effect

## Collapsing header

- The main header now smoothly collapses from 70dp to 48dp while scrolling down through normal feeds
- The header expands again when scrolling upward or returning to the top
- The collapse changes the actual layout height, so the feed gains usable screen space instead of leaving an empty gap
- Landscape behavior and Chaos playback are excluded from this effect

## Animated loading

- Home, Trending, Memes and legacy native feeds now use animated content-shaped skeleton cards while the first page is loading
- Skeletons mirror the shape of the real thumbnail, title and metadata layout instead of showing only a spinner
- The skeleton layer fades away as soon as real content is available
- Chaos is intentionally excluded

## Mini-player upgrade

- The native mini-player now slides and fades into place when created
- Added a thin orange live progress line across the bottom of the mini-player
- Increased mini-player elevation and added a subtle warm accent stroke so it stands out from the feed without becoming distracting
- Existing reopen, continue-watching and close behavior remains unchanged

## Video and startup transitions

- Native video-detail screens now use a short fade and upward slide transition rather than an abrupt screen replacement
- The CrazyShit splash artwork now performs a quick scale/fade entrance before flowing into the app
- Splash duration remains short so startup still feels fast

## v2.1 polish kept

- Grouped More screen with Your Stuff, Browse and App sections
- Installed version displayed inside More
- Compact More rows with accent icons, subtitles and chevrons
- Smooth More panel entry animation
- Orange navigation, loading and ripple accents
- Softer native feed-card corners

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

The stable application ID and signing identity remain unchanged, so CrazyShit v2.2.0 can install over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

# CrazyShit v2.1.0

CrazyShit 2.1.0 is a visual polish release focused on making the native app feel smoother, cleaner and more consistent without changing the core Chaos behavior.

## More redesign

- Rebuilt More into compact grouped sections instead of a stack of oversized equal-weight cards
- Added Your Stuff, Browse and App groups so account, library, categories and settings are easier to scan
- Added compact accent icon bubbles, subtitles and chevrons for each action
- More now shows the installed app version directly in the panel
- Opening More temporarily changes the app header to More / Settings, library and account, then restores the previous tab header when dismissed
- Portrait More opens as a rounded bottom panel and landscape More opens as a matching right-side panel
- Added a short slide/fade entrance animation and subtle press-scale feedback on More rows

## Motion and visual polish

- Added a subtle fade/scale transition while horizontally swiping between Home, Trending, Chaos and Memes
- Added soft press feedback to native feed cards without changing their existing click or long-press actions
- Added lightweight RecyclerView item animations for non-Chaos content
- Standardized selected navigation, progress indicators and touch ripples around the CrazyShit orange accent
- Slightly softened oversized feed-card corner radii for a more consistent native look
- Matched the Android navigation-bar background to the app shell so the bottom system area blends into the UI more cleanly

## Chaos behavior kept

- Portrait Chaos title and action controls remain visible continuously
- Empty space in the lower Chaos overlay continues to pass taps and long presses through to the video
- Hold for 2x playback
- Swipe up/down between clips
- Auto-advance when a clip ends
- Remember mute state
- Not interested filtering
- Scrubbable progress bar with inactivity fade
- Preloading and recent-view avoidance
- The ambient/blur experiment remains disabled for now

## Compatibility

The stable application ID and signing identity remain unchanged, so CrazyShit v2.1.0 can install over earlier signed stable releases while retaining app data.

Beta builds use a separate `.dev` package and can remain installed beside stable CrazyShit.

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

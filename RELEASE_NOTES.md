# CrazyShit v2.8.0

CrazyShit 2.8 is the Foundation Cleanup release. The goal is to keep the 2.7.2 look and behavior while simplifying the code underneath it so future features do not stack more runtime patches on top of the native UI.

## Foundation cleanup

- Native UI lifecycle ordering is owned by one coordinator instead of being spread through the Application class
- Home, Series, Chaos and Categories are now first-class pager destinations with no Trending/Memes navigation compatibility aliases
- The horizontal navigation rail uses the same direct Series and Categories IDs as the main bottom navigation
- Chaos filters non-media rows before they enter its pool, so section rows no longer require reflective cleanup after loading
- Chaos portrait chrome and touch handling now live inside ChaosFeedView instead of a separate runtime compatibility controller
- Video detail controller visibility, OLED styling, compact action pills and entrance motion now live directly in VideoDetailActivity
- Four obsolete compatibility/polish classes were removed after their behavior moved into the owning views
- Responsive geometry remains the final owner after theme and motion polish

## Automated quality checks

- Pull request builds run a hard architecture regression guard that rejects the removed 2.7 compatibility symbols if they return
- Android lint runs on every beta as a non-blocking audit while the existing lint backlog is cleaned up
- The lint report is saved as a workflow artifact for review
- Debug APK compilation remains a hard build gate

## Existing behavior retained

- OLED Black remains the default visual theme
- List remains the default feed view
- Cards, Grid and Posters remain available
- Series and Categories keep their embedded artwork
- Chaos keeps its video-only filtering, swipe feed, auto-advance, save/share/comments and portrait controls
- Global Search, Continue Watching, History, Watch Later and context-aware Related videos remain intact
- Portrait, horizontal orientation and wide-screen fitment from 2.7.2 remain the baseline
- Stable application ID and signing identity remain unchanged

## Version plan

- 2.8 stays on the beta package while this cleanup is tested
- RELEASE_VERSION remains 2.7.2 until 2.8 is approved for stable
- The 3.0 Kotlin modernization remains a later migration after the Java architecture is simplified

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

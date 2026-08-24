# CrazyShit v2.7.2

CrazyShit 2.7.2 is the responsive fitment and stability release. It keeps the OLED Immersive UI from 2.7 while tightening behavior across portrait, horizontal orientation, wide screens and secondary native screens.

## Responsive fitment

- Main feed geometry is reapplied after theme and motion polish so rotation sizing stays consistent
- Home and collection feed cards use better horizontal sizing
- Grid and Posters now use responsive media and card heights instead of fixed phone-only dimensions
- Series and Categories artwork cards scale with available column width
- Settings and Library use centered maximum widths on wider displays instead of stretching edge to edge
- Search uses tighter header and result spacing in horizontal orientation
- Additional safe-area and display-cutout handling was added to older native screens

## Feed and browsing polish

- Posters is fully supported throughout the pager and collection view-mode plumbing
- List remains the default and fallback feed style
- Changing view styles preserves the visible item and scroll offset more accurately
- Search and normal feed thumbnail failures can retry instead of remaining blocked for the session

## Resource and lifecycle cleanup

- Rendered thumbnail resolvers now explicitly destroy their WebViews, clear pending work and shut down executors
- Feed and Search adapters release thumbnail resources and playback listeners when screens close
- Settings and Library now participate in Android predictive back on supported devices

## Existing behavior retained

- OLED Black remains the default visual theme
- Ambient Glow, Motion Effects and Collapsing Header remain individually configurable
- Chaos keeps the media-only filtering fix from 2.7.1
- Continue Watching, History, Watch Later, global Search and context-aware Related videos remain intact
- Series and Categories continue using embedded artwork
- Stable application ID and signing identity are unchanged, so 2.7.2 installs over earlier stable 2.7 builds while retaining app data

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

# CrazyShit v2.8.0

CrazyShit 2.8 is the Foundation Cleanup release. The goal is to keep the 2.7.2 look and behavior while simplifying the code underneath it so future features do not stack more runtime patches on top of the native UI.

## Foundation cleanup

- Native UI lifecycle ordering is now owned by a single coordinator instead of being spread through the Application class
- Responsive geometry remains the final owner after theme and motion polish, preserving the smoother 2.7.2 rotation behavior
- The new coordinator gives 2.8 one place to progressively fold navigation, theme, motion, Chaos and video-detail behavior into fewer owners
- Existing controller detach/cleanup paths are centralized so lifecycle changes are easier to audit

## Automated quality checks

- Pull request builds now run Android lint before producing the debug APK
- The normal debug build still runs after lint so every beta must pass both static Android checks and compilation

## Version plan

- 2.8 starts the architectural cleanup without changing the stable package or user data
- Old navigation compatibility plumbing, reflection-heavy Chaos fixes and video-detail polish are scheduled to be folded into their owning classes during the 2.8 beta cycle
- The 3.0 Kotlin modernization remains a later migration after the Java architecture is simplified

## Existing behavior retained

- OLED Black remains the default visual theme
- List remains the default feed view
- Cards, Grid and Posters remain available
- Series and Categories keep their embedded artwork
- Chaos keeps the media-only filtering fix
- Global Search, Continue Watching, History, Watch Later and context-aware Related videos remain intact
- Portrait, horizontal orientation and wide-screen fitment from 2.7.2 remain the baseline

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com.

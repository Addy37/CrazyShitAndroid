# CrazyShit v2.8.5

CrazyShit 2.8.5 fixes silent new-video checks and makes delivery status visible.

## Notification delivery

- Runs a fresh site check when the app returns to the foreground after 30 minutes
- Replaces a completed one-time job instead of leaving it stuck as already finished
- Keeps the hourly, 3-hour, and 6-hour background schedule
- Retries a fully failed automatic check without repeatedly retrying a regional EFukt block
- Preserves the quiet first baseline so existing videos do not arrive as fake new alerts

## Notification status

- Adds a polished Check now action in Settings
- Shows the last completed check and a separate CrazyShit and EFukt result
- Reports timeouts, connection failures, regional blocks, and empty site responses
- Confirms when a baseline starts or when no new videos are available
- Keeps the existing branded preview and new-video notification design

## Compatibility

- Keeps the existing stable application ID and signing identity
- Installs directly over CrazyShit 2.8.4
- Keeps alerts optional and controllable through Android notification channels

## Project status

This Android client is an independent community project. It is not affiliated with, endorsed by, sponsored by, or published by CrazyShit.com or EFukt.com.

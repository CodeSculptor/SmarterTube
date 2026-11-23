# Resume after network drop (IMPLEMENTED-UNVERIFIED)

## What upstream already does
On a player error, `ErrorFixerController` reloads/restarts the engine after ~1 s, in a loop, with no
awareness of connectivity. Offline that means a tight failing loop and, for "Unable to connect to",
it permanently switches the data-source engine. Nothing tells the user what is happening. We do
not change that code (common/ untouched); this feature is a safety net on top.

## What we added (stmobile only)
- `NetworkResumePolicy` - pure decision/backoff state machine (unit tested).
- `NetworkResumeHelper` - ConnectivityManager callback (default-network callback API 24+, internet
  NetworkRequest API 21-23, CONNECTIVITY_ACTION receiver below 21), main-thread, 1.5 s loss debounce.
- `MobilePlaybackFragment`: a field plus `onStart` (start) / `onDestroy` (stop) overrides. Not stopped
  in onStop so background audio and the mini-player keep recovering.
- `ACCESS_NETWORK_STATE` in the stmobile manifest; three strings.

## Rules
Episode starts only on a real connectivity loss while the engine is alive and playWhenReady is true.
On return: 3 s grace, then if not playing and user did not pause: restart engine (position kept),
re-check after 2/4/8/16 s, max 4 restarts, then give up with a hint. Paused/closed at any check: stop.

## Known limit
Upstream's loop still runs while offline (see above). Guarding it would need a small common/ change.

## Device test script (airplane mode via quick settings)
1. Play a regular video ~30 s in. Airplane ON. After ~2 s expect "Waiting for connection...". Airplane OFF: within ~3-10 s expect "Reconnected" and playback continuing from roughly the same position.
2. Repeat with wifi toggled off/on instead of airplane.
3. Paused case: pause, airplane ON for 10 s, OFF. Expect no hint and the video stays paused.
4. Paused during outage: play, airplane ON, tap pause while waiting, airplane OFF. Expect it stays paused.
5. Closed case: play, airplane ON, close/back out of the player, airplane OFF. Expect no resume, no sound.
6. Background audio: play, press Home (audio continues), airplane ON ~15 s, OFF. Expect audio to resume with no UI.
7. Mini-player (if merged): minimise to the bar, airplane ON/OFF. Expect the bar's video to resume.
8. Long outage (60 s) then back: still resumes. Check logcat tag NetworkResumeHelper.
9. Non-network error: open an age-restricted/unavailable video. Expect no repeated restarts or hints.
10. Quick blip: toggle airplane ON then OFF within 1 s. Expect no hints.
11. Shorts: repeat step 1 on a Short.
12. Android 7 emulator (Phone_API24): repeat step 1.

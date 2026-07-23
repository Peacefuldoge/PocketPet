# Changelog

## 2.6.0

- Added drag-to-edge docking on both the left and right screen borders.
- The pet smoothly slides out of the screen while its visible region contracts to the head.
- The final docked position aligns the screen edge with the head center, leaving only the inward-facing half visible.
- Tapping the visible half-head reverses the animation and restores normal roaming.
- Hides feed and close controls while docked and pauses the autonomous state machine.

## 2.5.0

- Recreated the right-facing walk as 25 complete, integrated character drawings.
- Body, feet, flippers and tail are drawn together in every source frame, with no layered foot/body/tail assembly.
- Generated the left-facing walk exclusively by horizontally mirroring each complete right-facing frame.
- Removed all procedural foot replacement and lower-body compositing from the delivered animation.
- Preserved feeding, happy jump, full-screen random roaming and prone idle behavior.

## 2.4.0

- Replaced the fixed-upper-body walk with 25 independently authored full-body poses per direction.
- The head, hair, both flippers, torso weight and tail now change continuously throughout the gait.
- Kept both feet oriented toward the travel direction while alternating their forward/back positions by half a cycle.
- Removed optical-flow, cross-fade and lower-body-only mirroring artifacts.
- Preserved feeding, happy jump, full-screen random roaming and prone idle behavior.

## 2.3.1

- Fixed the unnatural direction flip shown in the supplied screen recording.
- Both feet now keep pointing in the character's travel direction and alternate only by front/back position and lift.
- Left-facing motion is produced by mirroring the complete character frame, never the lower body alone.
- Rebuilt all 48 frames per direction from a clean lower-body layer, removing old-foot remnants and transparent holes.
- Slowed one complete gait cycle to about 2.1 seconds while retaining smooth 60 Hz position updates.
- Preserved long-press feeding, happy jump, full-screen random roaming and prone idle behavior.

## 2.3.0

- Rebuilt the walk cycle as two explicit 24-frame half-strides so the two feet alternate in the correct order.
- Kept motion interpolation while slowing one complete cycle to about 1.5 seconds.
- Added a transparent gold-wrapped feeding-item sprite based on the supplied reference.
- Long-pressing the pet now reveals a timed feed button.
- Feeding animates the item toward the pet, then reuses the existing happy jump and vibration feedback.

## 2.2.1

- Reordered the 25 authored poses into a lower-jump closed walk cycle.
- Added one motion-compensated in-between for every key-pose transition, producing 50 playback frames per direction.
- Removed disconnected interpolation artifacts and rejected unstable tween frames.
- Slowed one complete walk cycle from about 1.0 second to about 1.4 seconds.

## 2.2.0

- Replaced the layered fixed-body walk with 25 individually authored full-body poses for each direction.
- Added separate right-facing and left-facing sprite sequences instead of mirroring the view at runtime.
- Both feet alternate while the torso, head, hair, flippers and tail move with the character's weight shift.
- Changed walk playback to a time-based 40 ms frame clock so animation does not slow down from handler rounding.

## 2.1.0

- Replaced the 6-frame one-leg-looking walk with a 16-frame cycle whose left and right lower-leg layers are exactly half a cycle apart.
- Increased position updates from roughly 31 Hz to roughly 60 Hz and separated movement timing from sprite timing.
- Replaced horizontal-only movement with random 2D target selection across the usable screen area.
- Added 8-frame stand-to-prone animation, two-frame breathing sleep loop, and reverse wake-up animation.
- After three completed trips, the pet waits for 9 seconds, lies down, sleeps for a random 14–25 seconds, then wakes and resumes roaming.
- Tapping or dragging interrupts rest and resets the sleep counter.

## 2.0.0

- Migrated from a WebView floating window to a native transparent `WindowManager` overlay service.
- Added foreground-service keepalive, click-to-cheer, drag positioning, and automatic walking.

# Phase D Regression Protocol

This document is the manual pre-merge checklist that prevents the four
Phase B contract fixes from silently regressing. It is the realistic
substitute for instrumented Android tests on this codebase, where the
USB DAC bypass path depends on connected hardware that no JUnit harness
can simulate cleanly. Each item below is a small, repeatable sequence
of steps with an unambiguous pass criterion and an explicit logcat
signature an engineer can grep for to confirm the result.

The intent is that any pull request that touches files under
`app/src/main/java/tf/monochrome/android/audio/usb/` or
`app/src/main/cpp/usb/` is run through this protocol before merge.
The total time to walk all four items is about ten minutes once the
test phone and DAC are set up, which is short enough that it should
not feel like a barrier to shipping. The unit test at
`app/src/test/java/tf/monochrome/android/audio/usb/LibusbAudioSinkGainTest.kt`
covers the gain math automatically and runs in CI; the four items in
this document cover everything else.

## Setup once per session

The tests need a Pixel 9a (or the development device of record) with
a Focal Bathys connected via USB-C, exclusive bypass mode enabled in
Settings, Developer Options "Disable USB audio routing" enabled, and
adb logcat captured to a file. The audio test corpus must be present
on the device under `/storage/emulated/0/Music/MonoTryptVerify/`.
Before each item, capture a fresh logcat to a separately-named file
so the post-merge artifacts can be reviewed item-by-item.

## Item 1: End-of-stream contract on a single-track session

Begin a new playback session with a queue containing exactly one
track. Use `24bit/96k/stereo/sine1k_short.wav` from the corpus.
Observe the player as the track approaches the end. The pass
criterion is that the player transitions from the playing state to
the ended state within roughly half a second of the track's nominal
duration, and the next-track UI affordance becomes available. The
fail signature is the player remaining in the playing state with the
position indicator stopped at the track end and no transition to
ended within ten seconds.

The logcat signature for a passing run is the sequence
`LibusbSinkTrace ... configure ... handleBuffer (one or more) ...
playToEndOfStream ... isEnded -> true`. To confirm the run, grep:

```
grep -E 'LibusbSinkTrace.*(playToEndOfStream|isEnded -> true)' capture.log
```

The output must contain at least one `playToEndOfStream` line followed
by exactly one `isEnded -> true` line. If `isEnded -> true` is missing,
the EOS contract has regressed.

## Item 2: Watchdog recovery from an artificial stall

Begin playback of any test corpus file long enough to settle into
steady state — `24bit/96k/stereo/sine1k_long.wav` is the canonical
choice. While playing, induce an iso-pump stall by running a heavy
CPU load in parallel: launch several animation-heavy apps, or run
`adb shell stress-ng --cpu 8 --timeout 5s` from the host if the
shell has stress-ng. The goal is to starve the libusb event thread
for at least 400 ms, which is the watchdog stall threshold.

The pass criterion is that audio cuts out briefly during the stall,
then resumes within roughly two seconds (the stop, sleep, start
sequence in `recoverIsoPump` plus the iso pump's startup window),
and the user can identify the recovery moment by the brief silence
gap rather than a permanent silence. The fail signature is permanent
silence with no recovery, which means either the watchdog did not
trip (CPU starvation was insufficient — try a longer stall) or the
recovery path failed to bring the pump back up.

The logcat signature for a passing run is the trace sequence
`watchdog_trip ... retry=scheduled ... watchdog_recover ok=true`.
Grep:

```
grep -E 'LibusbSinkTrace.*(watchdog_trip|watchdog_recover)' capture.log
```

The output must contain a `watchdog_trip` line followed by a
`watchdog_recover ok=true` line. If `watchdog_recover ok=false` or
`ok=aborted` appears instead, the recovery either failed (real
issue) or aborted because of a format change race (Phase D item 3 in
action — that is correct behavior if a track change happened in the
recovery window, otherwise it is a bug).

## Item 3: Volume on 24-bit content

Begin playback of `24bit/96k/stereo/sine1k_short.wav` and observe the
analog output level on the Bathys. While playing, drag the in-app
volume slider from maximum to roughly thirty percent. The pass
criterion is an audible reduction in output level proportional to
the slider movement; specifically, the user should hear roughly ten
decibels of attenuation (a clearly audible volume drop, not a subtle
one) when the slider goes from maximum to thirty percent.

The fail signature is no audible change in output level when the
slider moves. If the user perceives no change despite the slider
visually moving, the gain dispatch in `handleBuffer` has regressed,
likely because a future refactor of the dispatch table dropped the
24-bit case.

There is no logcat signature for this item because the gain path is
silent at runtime. The unit test in `LibusbAudioSinkGainTest.kt` is
the automated companion; the manual check here verifies the
dispatch glue around it.

## Item 4: Pause and resume preserves audio

Begin playback of `24bit/96k/stereo/pink_short.wav` (pink noise is
chosen because a continuous noise signal makes any drop
immediately audible — a sine wave's dropout is harder to detect
because the brain interpolates between cycles). Roughly one second
into playback, press pause. Wait two seconds. Press play. Listen
for the resume.

The pass criterion is that resume continues smoothly from the exact
point of pause, with no audible drop or click at the transition.
The fail signature is a perceptible silence gap or click at resume,
which means the soft-mute path has regressed back to the old
flushRing-based behavior.

The logcat signature for a passing run is the trace sequence
`pause ... play` with both lines showing `bypassActive=true`. Grep:

```
grep -E 'LibusbSinkTrace.*(pause|play) bypassActive=true' capture.log
```

If the lines are present but the audio drops, the regression is in
the C++ side — the `muted_` flag is being honored but `drainRing`
may have lost the early return that preserves the ring tail across
the mute window.

## Recording the run

After all four items pass, save the four logcat captures alongside
the PR's commit SHA in a regression archive. The archive is the
provenance trail that lets a future engineer reproduce the exact
test conditions if a related bug surfaces in the field. The archive
location convention is `docs/regression-runs/<commit>/item-N.log`,
which keeps the runs versioned alongside the code they validated.

## Why this is a manual protocol rather than instrumented tests

Instrumented Android tests for the bypass path would need a connected
DAC during test execution, a way to synchronize the playback state
machine with the test driver, and a way to observe the analog output
of the DAC programmatically. The first requirement turns CI into a
hardware lab, which is a real cost; the second requires plumbing
test-only hooks into the production code; the third is impossible
for a Bluetooth headphone like the Bathys without a recording rig.

The protocol above gives most of the regression coverage at a
fraction of the engineering cost, with the unit test handling the
piece that genuinely is testable as pure data. When the project
grows to the point that Bathys-class hardware is permanently
attached to a test rig, the protocol can be re-encoded as
instrumented tests; until then, the cost-benefit tilts toward the
manual checklist.

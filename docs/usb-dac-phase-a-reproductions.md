# Phase A Reproductions

This document captures the three failure modes that motivated the Phase A
instrumentation, with explicit reproduction steps and expected logcat
signatures. The goal is not yet to fix these issues; it is to confirm
that the instrumentation added in this branch makes each failure
diagnosable from logs alone, so that subsequent phases can validate
fixes against the same captures.

Each reproduction is structured the same way: prerequisites, exact
steps, the observable user-visible symptom, and the logcat signature
the engineer should expect to find in a captured log. The signatures
are written as `grep` patterns so they can be checked mechanically.

## Prerequisites for all reproductions

Hardware: Pixel 9a running Android 16 or the development device of
record, plus a Focal Bathys connected via USB-C. Developer Options
must have "Disable USB audio routing" enabled, otherwise the kernel
UAC driver will hold the streaming interface and `LibusbUacDriver.open`
will fail before the issues under test become reachable.

Software: a debug build of MonoTrypt off the
`tryptz/usb-dac-phase-a-instrumentation` branch installed on the device,
the audio test corpus generated under `build/audio-test-corpus/`, and
`adb logcat -v time -T 0` running into a captured file for the duration
of each test.

The Settings toggle "Exclusive USB DAC" must be on. Verify by observing
`UsbExclusiveCtl` reach `Status.Streaming` in the log before beginning
any reproduction.

## Reproduction 1: end-of-track hang on a single-track session

This reproduces the `isEnded` contract violation in `LibusbAudioSink`
where the renderer never observes end-of-stream because
`driver.isStreaming.value` stays true after the source ends.

Steps. Begin a new playback session with a queue containing exactly
one track. Use `build/audio-test-corpus/24bit/96k/stereo/sine1k_short.wav`
because two seconds is long enough for the iso pump to settle into
steady state and short enough that the test completes quickly.
Observe the player as the track approaches its end.

Expected user-visible symptom. The track plays normally until the
final samples have been drained from the ring. At that point the
position indicator stops advancing, the play/pause control remains in
the "playing" state, and the iso pump heartbeat in logcat continues
to fire at the expected cadence. The player never transitions to
the ended state and the next-track UI affordance is never offered.

Expected logcat signature. The trace log will show the sequence
`configure ... handleBuffer ...` followed eventually by
`playToEndOfStream bypassActive=true ring_pending=N` where N
decreases over subsequent polls toward zero. Critically, the trace
will NOT show `isEnded -> true` because the existing `isEnded`
implementation requires `driver.isStreaming.value` to be false, which
nothing in the current code path causes after natural end-of-stream.
The bypass telemetry log will show `under_ratio` rising as the ring
empties and silence padding begins, while `played_delta` continues
to advance at nominal rate; this combination is the unambiguous
signature of a stream that is technically still streaming but has no
real source data to play. To find the signature in a capture:

```
grep -E 'LibusbSinkTrace.*(playToEndOfStream|isEnded)' capture.log
```

A successful reproduction shows `playToEndOfStream` once and zero
occurrences of `isEnded -> true`.

## Reproduction 2: watchdog trip in exclusive mode falls through to silence

This reproduces the design tension between the watchdog's
fall-through-to-delegate behavior and the `bypass-or-nothing`
delegate choice. When the watchdog trips, audio routes to
`NoOpAudioSink` and the user hears nothing.

Steps. Begin playback of any test corpus file long enough to settle
into steady state. While playing, force the iso pump to wedge by
inducing an artificial stall — the cleanest mechanism is to run a
heavy CPU load (something CPU-bound such as `stress-ng --cpu 8`
through `adb shell` if available, or simply launching several
animation-heavy apps) which starves the libusb event thread enough
that `playedFrames` stops advancing for longer than the
`kIsoStallNs` 400 ms threshold.

Expected user-visible symptom. The audio cuts out completely.
Visually, playback continues — the position indicator advances, the
play/pause control stays in the playing state, and there is no
visible error. The audio just stops.

Expected logcat signature. The trace log will record the watchdog
trip event explicitly:

```
grep -E 'LibusbSinkTrace.*watchdog_trip' capture.log
```

A trip line shows `watchdog_trip played=N stalled_ms=M
since_first_write_ms=K frames_written=F` where M is at least 400
and K is at least 400. Following the trip, the bypass telemetry log
will show `wr_calls` continuing to increment (the renderer is still
trying to feed buffers) but `wr_frames` going to zero (because
`bypassActive` is now false and `super.handleBuffer` routes them to
`NoOpAudioSink` which discards). The `under_ratio` will hold at
whatever it was at the moment of the trip rather than rising — this
is the cleanest distinguishing signal between "watchdog tripped and
no audio" versus "iso pump is starving and stuttering".

## Reproduction 3: volume slider has no effect on 24-bit content

This reproduces the gap in `LibusbAudioSink.applyGainPcm16` where the
gain function is only invoked for 16-bit PCM, leaving 24-bit and
32-bit content at unity gain regardless of the user's slider position.

Steps. Begin playback of
`build/audio-test-corpus/24bit/96k/stereo/sine1k_short.wav` (a 1 kHz
sine at 24-bit / 96 kHz, which is the format the Bathys ships at by
default). Record the analog output of the Bathys with a calibrated
ADC into a file. While recording, drag the in-app volume slider from
maximum to a clearly audible reduction — for example, 30 % linear,
which corresponds to roughly 10 dB of attenuation on a linear gain
slider.

Expected user-visible symptom. The recorded analog output level
does not change when the slider moves. The bit pattern of the source
sine wave is unchanged by the slider movement. From the user's
perspective the slider appears broken; in fact it is functioning,
but its output (`BypassVolumeController.getVolume`) is being read
and silently discarded by the dispatch logic in
`LibusbAudioSink.handleBuffer`.

Expected logcat signature. There is no log entry for this issue
because the dispatch is silent. The signature is the absence of any
gain-related log line and the simultaneous presence of the bypass
heartbeat showing 24-bit playback. A capture confirms the
reproduction by:

```
grep -E 'LibusbAudioSink.*configured: bypass active' capture.log
```

If this returns a line containing `out 96000/24b/2ch`, then 24-bit
bypass was active for the test, and the recording-side measurement
of zero level change confirms the issue.

## What to capture and keep

For each reproduction, the artifacts kept in the bug repository
should be the full logcat capture (filtered to tags `LibusbAudioSink`,
`LibusbSinkTrace`, `LibusbUacDriver`, `BypassTelemetry`, and
`UsbExclusiveCtl`), plus a brief markdown note recording device, OS
build, app commit hash, and the time-of-day of the reproduction. The
recording-side artifact for reproduction 3 is the WAV file from the
ADC, named with the slider position at which it was captured.

The Phase B fixes will be validated by re-running these exact
reproductions and confirming the absence of each signature in the
post-fix logs, with the Phase A telemetry providing positive
evidence that the fix did not introduce a new failure mode.

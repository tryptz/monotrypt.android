# Bit-Perfect Verification Methodology

This document defines the two acceptable methodologies for proving that
MonoTrypt's USB DAC bypass produces bit-identical output at the wire
when the user is at unity gain with no DSP active. Phase C of the USB
DAC plan executes one of these methodologies; this document is the
specification it works from.

The two methodologies are wire capture and analog loopback. Both are
valid for the bit-perfect claim; they answer slightly different
questions and have different equipment requirements. An engineer
working in Phase C should pick whichever they have the equipment for,
and this document explains the tradeoffs so the choice is informed.

## Wire capture methodology

A wire capture intercepts the USB traffic between the phone and the
DAC at the protocol level and decodes the iso payload. The output is
a sequence of bytes that the DAC received, which can be compared
sample-for-sample against the bytes the renderer fed into
`LibusbAudioSink.handleBuffer`. If those byte sequences are equal,
the bypass is bit-perfect by definition.

Equipment: a host PC capable of running USBPcap (Windows) or usbmon
(Linux), plus a USB-C-to-USB-A adapter that allows the DAC to be
plugged into a host PC port that the capture tool monitors. The
phone is plugged into the host PC's port for the duration of the
test, with USB tethering or any other host-PC USB protocol activity
disabled to keep the capture file small.

Procedure. Start the capture tool filtering on the phone's USB
device address. Begin playback of a known reference file from the
audio test corpus. Let the file play to completion. Stop the
capture. Use a small Python script (one I will provide as part of
Phase C) to walk the captured pcap, identify the iso transfers
addressed to the DAC's streaming endpoint, concatenate their iso
packet payloads in time order, and emit the resulting byte stream
as a raw PCM file. Compare the raw PCM file byte-for-byte to the
source file's PCM payload (extracted from the WAV with a similar
small script that strips the WAV header).

Pass criterion. The two byte sequences are identical except for an
optional silence prefix on the captured side, which corresponds to
the head-start the iso pump emits while the ring fills. The prefix
is acceptable up to roughly 250 ms of silence (the
`kRingTargetMs` plus the initial silence pad). Any deviation in the
non-silence portion of the stream is a failure.

Strengths. This methodology is unambiguous about where the failure
is. If the source bytes match the wire bytes but the DAC sounds
wrong, the failure is downstream of MonoTrypt. If the source bytes
do not match the wire bytes, the failure is in MonoTrypt and the
specific bytes that differ point directly at the responsible code
path.

Weaknesses. It requires a host PC and a USB-A adapter, which not
every engineer working on this codebase will have. It also does not
exercise the analog domain at all, so it cannot detect failures
introduced by the DAC's own DSP processing if the DAC is acting
outside spec.

## Analog loopback methodology

An analog loopback feeds the DAC's headphone output into a
calibrated ADC (an audio interface), records the result, and
performs a null-difference comparison against the source file. If
the difference is below the ADC's noise floor across the entire
recording, the bypass is bit-perfect within the measurement
precision of the loopback.

Equipment: an audio interface with line-in or instrument inputs at
24-bit / 96 kHz or higher (a Focusrite Scarlett, RME Babyface, or
similar), a stereo cable from the Bathys's analog output (if
available; the Bathys does not have a separate analog out, so this
methodology requires substituting a different DAC for verification
purposes — see the alternative below), and a recording program
such as Audacity or a command-line `arecord` invocation.

For the Bathys specifically: because the Bathys does not expose its
analog stage at a connector, the loopback methodology cannot test
the Bathys directly. To verify bit-perfect output to the Bathys,
the wire capture methodology is the only option. The loopback
methodology is included here for completeness because the same
software path serves other DACs that will be tested as part of
Phase D regression coverage; for those DACs a loopback works.

Procedure (for a non-Bathys DAC). Begin recording on the audio
interface. Begin playback of the reference file from the corpus.
Let the file play to completion. Stop the recording. Time-align the
recording with the source by cross-correlating a known impulse at
the start of the source file (an impulse track is included in the
corpus for this purpose). Once aligned, subtract the source from the
recording sample-by-sample. The result is a difference signal whose
RMS amplitude is the bypass error.

Pass criterion. The difference signal's RMS amplitude is at or
below the ADC's noise floor as measured against a silent input
(the corpus includes a silence file for ADC noise-floor
calibration). On a 24-bit ADC with realistic analog noise, the
floor is around -120 dBFS, well below any audibly relevant
difference. A bit-perfect bypass produces a difference at the noise
floor.

Strengths. Tests the entire output chain including any analog
processing the DAC performs, which the wire capture cannot do.
Cheaper to set up than wire capture for engineers who already own
an audio interface for music production.

Weaknesses. Cannot directly test the Bathys (no analog out
connector). Requires careful gain staging between the DAC output
and the ADC input. Time-alignment errors of even one sample can
produce false-positive differences for high-frequency content.

## Choice for Phase C

Phase C of the USB DAC plan uses wire capture as the primary
methodology because the Bathys is the target hardware and its lack
of analog-out makes loopback infeasible. The analog loopback path
is documented here because Phase D will add regression coverage
for additional DACs, some of which expose analog out and will be
tested via loopback for cost efficiency.

The wire capture toolchain is implemented as Phase-C deliverables:
a Python script that consumes pcap input and emits raw PCM, a
companion script that diffs raw PCM against a source WAV, and a
runnable test harness that orchestrates a corpus run end-to-end.
Those will be created when Phase C begins; this document is their
specification.

## What "bit-perfect" excludes

Bit-perfect is a precise claim and should not be confused with two
adjacent claims. First, it does not assert that the DAC's analog
output is "transparent"; the analog stage's noise, distortion, and
frequency response are properties of the DAC, not of MonoTrypt. The
bit-perfect claim ends at the byte boundary between USB and the
DAC's internal digital signal path. Second, it applies only when
the user is at unity gain with no DSP active. Any user-applied
volume reduction, EQ, AutoEQ, or other processor in the bypass
chain converts the output to a deterministic function of the input
that is by definition not bit-identical to the input. The Phase B
volume scaling fix preserves bit-perfect at unity but explicitly
gives it up at any other slider position, which is the correct
behavior.

The product UX implication is that "bit-perfect" should only be
advertised to the user when the slider is at maximum and no DSP is
active; the diagnostics screen should reflect both conditions
clearly. Phase D will surface this in the Settings UI.

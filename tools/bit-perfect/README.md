# Bit-Perfect Verification Tools

This directory contains the Phase C toolchain for verifying that
MonoTrypt's USB DAC bypass produces byte-identical output at the
USB wire when the user is at unity gain. The methodology is
specified in `docs/usb-dac-bit-perfect-verification.md`; this
README documents how to run the tools.

## Pieces

`pcap_to_pcm.py` consumes a usbmon-format pcap or pcapng file and
emits a raw PCM stream containing the iso OUT payloads addressed to
the streaming endpoint. The script is stdlib-only — no `pip install`
step — and supports both legacy pcap and pcapng with the
LINKTYPE_USB_LINUX (189) and LINKTYPE_USB_LINUX_MMAPPED (220)
linktypes that Linux usbmon produces.

`pcm_diff.py` compares two raw PCM streams byte-for-byte, tolerating
a bounded silence prefix on the wire side. The default prefix
allowance is 250 ms at the source sample rate, which corresponds to
the `kRingTargetMs` constant in `libusb_uac_driver.cpp` plus a small
margin for transport-control latency. Failures print the byte offset
of the first mismatch and a hex context window for diagnosis.

`verify_bit_perfect.sh` is the end-to-end orchestration script that
exercises a single corpus file against a connected device. It pushes
the source to the device, starts a usbmon capture, triggers playback
through MonoTrypt, stops the capture, decodes the iso payloads, and
runs the diff. The script is the regression-test entry point that
Phase D will wire into a CI workflow.

## Running a single-file verification

The orchestration script accepts the device-specific parameters as
flags. For a Focal Bathys connected to USB bus 3, with a
24-bit / 96 kHz / stereo source from the audio test corpus:

```bash
tools/bit-perfect/verify_bit_perfect.sh \
    --bus 3 \
    --endpoint 0x01 \
    --bytes-per-frame 6 \
    --rate 96000 \
    --bits 24 \
    --channels 2 \
    --source build/audio-test-corpus/24bit/96k/stereo/sine1k_short.wav \
    --workdir /tmp/bp-verify
```

The bus number is reported by `lsusb -t` on the host; the endpoint
is the streaming OUT endpoint listed by `lsusb -v`. For a DAC other
than the Bathys, both values change — consult the device's
descriptor with `lsusb -v -d <vid>:<pid>` and substitute the
streaming OUT endpoint and the bytes-per-frame implied by the alt
setting in use.

## Running the corpus end-to-end

The corpus generator at `tools/audio-test-corpus/generate.sh`
produces files for every supported bit depth, sample rate, and
channel count. A loop driver around `verify_bit_perfect.sh` over
those files would constitute a Phase C regression sweep; the loop
is intentionally not committed yet because the bus and endpoint
parameters are device-specific and a generic harness would need
either a parameter file or device-detection logic. Phase D will
add that layer alongside the in-app self-test.

## Interpreting failures

The most common failure modes and their signatures:

A failure at offset 0 with the wire bytes also being zero usually
means the iso pump never received any non-silent audio — check the
wire-capture timing against playback start, and verify that
exclusive bypass mode was actually engaged by inspecting
`UsbExclusiveCtl` in logcat.

A failure at a frame-aligned offset with a small mismatch (one or
two bytes off in a 24-bit sample) typically indicates a packing
or sign-extension bug in `applyGainPcm24` — but only at sub-unity
gain. At unity gain the bypass should preserve every byte
exactly, so a mismatch at unity gain points at a deeper issue
(format negotiation, alt setting, byte order).

A failure that begins partway through the stream and continues for
the rest of the capture suggests a packet was dropped or duplicated
at the iso layer; the first mismatch offset divided by the iso
packet size (typically `bytes_per_frame * 6` at HS / 1ms iso
interval / 96kHz) gives the affected packet number, which can be
cross-referenced against the iso telemetry counters in the log
capture.

A successful run prints the number of bytes and frames over which
the comparison passed, and the byte length of the silence prefix
the iso pump emitted at startup. Both values are recorded in the
work directory's diff output for later review.

## Limitations

The tools currently support only Linux usbmon captures. Windows
USBPcap support requires extending `pcap_to_pcm.py` to handle
LINKTYPE_USBPCAP (249) and the slightly different header layout
that USBPcap writes. The work is mechanical — the iso payload
location is the same — but is deferred until a Windows-only
engineer needs to use the toolchain.

The diff is strict byte-equality; it does not tolerate any error
beyond the leading silence prefix. This is correct for the
bit-perfect claim, but it means that any wire-level packet loss
(rare on a wired connection but possible on a flaky cable) shows
up as a diff failure. The recommendation is to rerun on a known-
good cable before debugging deeper if the failure first appears
mid-stream and the iso telemetry shows a packet error in the
same window.

The script depends on `tcpdump` or `dumpcap` for the capture and
`sox` for source-PCM extraction. Those are widely available on
Linux but a fully self-contained Python capture path could
replace them; the cost is the dependency on `python-pyshark` or
direct usbmon ioctl bindings, which the current stdlib-only
design intentionally avoids.

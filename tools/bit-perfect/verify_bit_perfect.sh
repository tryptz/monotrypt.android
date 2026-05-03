#!/usr/bin/env bash
# End-to-end bit-perfect verification orchestrator for MonoTrypt USB
# DAC bypass.
#
# This script runs the full Phase C verification flow against a single
# corpus file: it pushes the source file to the device, starts a
# usbmon capture on the host, plays the file through MonoTrypt via
# an ADB media-button intent, stops the capture, decodes the iso
# payloads, and diffs them against the source. The script is the
# regression-test entry point that Phase D will wire into CI; running
# it manually for a single file is the verification rite that closes
# the Phase C gate.
#
# Prerequisites on the host:
#   - adb in PATH, with the test phone connected and authorised.
#   - usbmon kernel module loaded (modprobe usbmon).
#   - Read access to /sys/kernel/debug/usb/usbmon/{busN}u (typically
#     requires root or membership in a usbmon group; see Linux
#     kernel docs/usb/usbmon.txt for setup).
#   - python3 in PATH (no pip dependencies — the decoder and diff
#     tools are stdlib-only by design).
#   - sox in PATH to extract raw PCM from the source WAV for diffing.
#
# Prerequisites on the device:
#   - MonoTrypt installed off the Phase B branch or later.
#   - Exclusive USB DAC mode enabled in Settings.
#   - Volume slider at maximum so the bit-perfect claim is meaningful
#     (the diff is byte-equality, which only holds at unity gain).
#   - Developer Options > "Disable USB audio routing" enabled.
#
# Usage:
#   verify_bit_perfect.sh \
#       --bus 3 \
#       --endpoint 0x01 \
#       --bytes-per-frame 6 \
#       --rate 96000 \
#       --bits 24 \
#       --channels 2 \
#       --source build/audio-test-corpus/24bit/96k/stereo/sine1k_short.wav \
#       --workdir /tmp/bp-verify
#
# The bus number is the USB bus the DAC is on as reported by
# `lsusb -t` on the host (the DAC hub-and-port path that ends in the
# audio-class device). The endpoint is the streaming OUT endpoint
# from `lsusb -v`. Both values are device-specific and stable
# across reboots if the same physical port is used.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PCAP_TO_PCM="$SCRIPT_DIR/pcap_to_pcm.py"
PCM_DIFF="$SCRIPT_DIR/pcm_diff.py"

usage() {
    sed -n '2,/^$/p' "$0" | sed 's/^#//' >&2
    exit 64
}

# ---- arg parsing ------------------------------------------------------------
BUS=""
ENDPOINT=""
BYTES_PER_FRAME=""
RATE=""
BITS=""
CHANNELS=""
SOURCE=""
WORKDIR=""
CAPTURE_PRE_S=2
CAPTURE_POST_S=3

while [[ $# -gt 0 ]]; do
    case "$1" in
        --bus) BUS="$2"; shift 2 ;;
        --endpoint) ENDPOINT="$2"; shift 2 ;;
        --bytes-per-frame) BYTES_PER_FRAME="$2"; shift 2 ;;
        --rate) RATE="$2"; shift 2 ;;
        --bits) BITS="$2"; shift 2 ;;
        --channels) CHANNELS="$2"; shift 2 ;;
        --source) SOURCE="$2"; shift 2 ;;
        --workdir) WORKDIR="$2"; shift 2 ;;
        -h|--help) usage ;;
        *) echo "error: unknown argument: $1" >&2; usage ;;
    esac
done

for v in BUS ENDPOINT BYTES_PER_FRAME RATE BITS CHANNELS SOURCE WORKDIR; do
    if [[ -z "${!v}" ]]; then
        echo "error: --${v,,} is required" >&2
        usage
    fi
done

if [[ ! -f "$SOURCE" ]]; then
    echo "error: source file not found: $SOURCE" >&2
    exit 1
fi

mkdir -p "$WORKDIR"

# ---- prerequisites ---------------------------------------------------------
require_tool() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "error: required tool '$1' not on PATH" >&2
        exit 1
    fi
}
require_tool adb
require_tool python3
require_tool sox
# tcpdump is preferred over `cat /sys/kernel/debug/usb/usbmon/Nu`
# because tcpdump produces a parseable pcap file directly. dumpcap
# (Wireshark's CLI) is also acceptable; we use whichever is present.
if command -v tcpdump >/dev/null 2>&1; then
    CAPTURE_TOOL=tcpdump
elif command -v dumpcap >/dev/null 2>&1; then
    CAPTURE_TOOL=dumpcap
else
    echo "error: need tcpdump or dumpcap on PATH for the wire capture" >&2
    exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
    echo "error: no device connected to adb" >&2
    exit 1
fi

# ---- prepare the source: extract raw PCM for diffing -----------------------
SRC_BASE="$(basename "$SOURCE" .wav)"
SRC_PCM="$WORKDIR/${SRC_BASE}.pcm"
case "$BITS" in
    16) PCM_FMT=s16le ;;
    24) PCM_FMT=s24le ;;
    32) PCM_FMT=s32le ;;
    *) echo "error: unsupported --bits=$BITS (16, 24, or 32 only)" >&2; exit 1 ;;
esac
echo "==> Extracting raw PCM from source ($PCM_FMT, $RATE Hz, $CHANNELS ch)"
sox -V1 "$SOURCE" -t raw -r "$RATE" -c "$CHANNELS" -e signed -b "$BITS" "$SRC_PCM"

# ---- push the source to the device -----------------------------------------
DEV_DIR="/storage/emulated/0/Music/MonoTryptVerify"
DEV_PATH="$DEV_DIR/$(basename "$SOURCE")"
echo "==> Pushing source to device: $DEV_PATH"
adb shell "mkdir -p $DEV_DIR"
adb push "$SOURCE" "$DEV_PATH" >/dev/null
adb shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d file://$DEV_PATH" >/dev/null

# ---- start the wire capture ------------------------------------------------
PCAP_PATH="$WORKDIR/${SRC_BASE}.pcap"
echo "==> Starting wire capture on usbmon$BUS -> $PCAP_PATH"
case "$CAPTURE_TOOL" in
    tcpdump)
        tcpdump -i "usbmon$BUS" -w "$PCAP_PATH" -U &
        ;;
    dumpcap)
        dumpcap -i "usbmon$BUS" -w "$PCAP_PATH" -q &
        ;;
esac
CAPTURE_PID=$!

# Wait briefly so the capture is actively listening before we start
# playback; without this the iso pump's first ~milliseconds of data
# would be missed and the silence-prefix accounting on the diff side
# would underestimate the real prefix.
sleep "$CAPTURE_PRE_S"

# ---- trigger playback ------------------------------------------------------
echo "==> Triggering MonoTrypt playback"
# A direct VIEW intent into MonoTrypt's playback activity is the
# cleanest way to start a single-track session that exercises the
# end-of-stream contract Phase B fixed. The activity component is
# the canonical entry; if MonoTrypt's manifest changes the component
# name, update this line.
adb shell "am start -a android.intent.action.VIEW \
    -d file://$DEV_PATH \
    -t audio/wav \
    -n tf.monochrome.android/.MainActivity" >/dev/null

# ---- wait for playback to complete ----------------------------------------
DURATION_S="$(soxi -D "$SOURCE" 2>/dev/null | awk '{printf "%d", $1 + 1}')"
DURATION_S="${DURATION_S:-5}"
echo "==> Waiting ${DURATION_S}s for playback to complete"
sleep "$DURATION_S"

# Allow a moment for the pump to finish draining; the EOS contract
# requires hasPendingData() to return false before isEnded does.
sleep "$CAPTURE_POST_S"

# ---- stop the capture ------------------------------------------------------
echo "==> Stopping wire capture"
kill -INT "$CAPTURE_PID" 2>/dev/null || true
wait "$CAPTURE_PID" 2>/dev/null || true

if [[ ! -s "$PCAP_PATH" ]]; then
    echo "error: capture produced no data — verify usbmon$BUS is active" >&2
    exit 1
fi

# ---- decode the iso payloads -----------------------------------------------
WIRE_PCM="$WORKDIR/${SRC_BASE}.wire.pcm"
echo "==> Decoding iso OUT payloads on endpoint $ENDPOINT"
python3 "$PCAP_TO_PCM" \
    --pcap "$PCAP_PATH" \
    --endpoint "$ENDPOINT" \
    --bytes-per-frame "$BYTES_PER_FRAME" \
    --out "$WIRE_PCM"

# ---- run the bit-perfect diff ---------------------------------------------
# 250 ms at the source sample rate, the kRingTargetMs upper bound on
# the silence prefix the iso pump emits at startup.
MAX_PREFIX_FRAMES=$((RATE / 4))
echo "==> Running bit-perfect diff (max prefix = ${MAX_PREFIX_FRAMES} frames)"
if python3 "$PCM_DIFF" \
    --source "$SRC_PCM" \
    --wire "$WIRE_PCM" \
    --bytes-per-frame "$BYTES_PER_FRAME" \
    --max-prefix-frames "$MAX_PREFIX_FRAMES"; then
    echo "==> $SRC_BASE: BIT-PERFECT"
    exit 0
else
    echo "==> $SRC_BASE: DIVERGENCE — see diff output above"
    echo "    Artifacts kept at: $WORKDIR"
    exit 1
fi

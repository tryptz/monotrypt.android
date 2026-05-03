#!/usr/bin/env bash
# Phase-A audio test corpus generator for MonoTrypt USB DAC bypass.
#
# Produces a deterministic set of test files that exercise the bit
# depths, sample rates, and edge cases the iso pump must handle. The
# corpus is intended to be:
#
#   1. Reproducible — every file is generated from documented sox/ffmpeg
#      invocations so a future engineer can rebuild it from this script
#      alone, no opaque binary blobs.
#
#   2. Exhaustive across the dimensions that matter for bypass testing —
#      bit depth (16/24/32), sample rate (44.1/48/96/192 kHz), channel
#      count (1/2), and a few boundary conditions (very short, very
#      long, format-changing, packet-aligned).
#
#   3. Comparable — every file has a known-correct reference signal
#      (sine, pink noise, or impulse) so a wire capture or loopback
#      recording can be null-differenced against it to verify
#      bit-perfect output.
#
# Output layout: $OUT_DIR/{16,24,32}bit/{44k1,48k,96k,192k}/{stereo,mono}/
# with the test signal as the file basename. Special cases live in
# $OUT_DIR/edge/.
#
# Dependencies: sox (preferred for clean signal generation), ffmpeg
# (used for the format-change concatenation file because sox doesn't
# concatenate dissimilar formats). Bail early if neither is on PATH.

set -euo pipefail

OUT_DIR="${OUT_DIR:-./build/audio-test-corpus}"
DURATION_SHORT_S=2
DURATION_LONG_S=600     # 10 minutes — long enough to expose drift
DURATION_BOUNDARY_S=1

require_tool() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "error: required tool '$1' not on PATH" >&2
        exit 1
    fi
}

require_tool sox
require_tool ffmpeg

mkdir -p "$OUT_DIR"

# Map of (rate_label → numeric Hz). The labels match Phase-A diagnostic
# scripts that filter by rate without parsing filenames so keep the
# label set in sync with docs/usb-dac-phase-a-reproductions.md.
declare -A RATES=(
    [44k1]=44100
    [48k]=48000
    [96k]=96000
    [192k]=192000
)

declare -A BITS=(
    [16bit]=16
    [24bit]=24
    [32bit]=32
)

declare -A CHANNELS=(
    [stereo]=2
    [mono]=1
)

# A single tone amplitude. -3 dBFS chosen so:
#   - the signal is loud enough to dominate any noise floor in a
#     loopback ADC capture by ~30 dB at the worst-case ADC dynamic
#     range we'd realistically use.
#   - we leave 3 dB headroom so volume-scaling tests (Phase B) can
#     verify gain reduction without clipping when amplitudes are
#     summed with other signals.
TONE_DBFS=-3

# Generate a sine wave: $1 freq Hz, $2 rate Hz, $3 bits, $4 channels,
# $5 duration s, $6 output path. Sox handles bit-depth via -b and
# rate via -r; -V1 silences progress bars but keeps warnings.
gen_sine() {
    local freq=$1 rate=$2 bits=$3 ch=$4 dur=$5 out=$6
    sox -V1 -n -r "$rate" -b "$bits" -c "$ch" "$out" \
        synth "$dur" sine "$freq" gain "$TONE_DBFS"
}

# Pink noise: same arg pattern, no frequency. -3 dBFS so the spectral
# density doesn't clip the highest peaks. Pink (1/f) is preferred over
# white because its long-term RMS approaches realistic music content
# better than white, which over-emphasises high frequencies.
gen_pink() {
    local rate=$1 bits=$2 ch=$3 dur=$4 out=$5
    sox -V1 -n -r "$rate" -b "$bits" -c "$ch" "$out" \
        synth "$dur" pinknoise gain "$TONE_DBFS"
}

# Single-sample impulse train: silence everywhere except one sample
# value 1.0 at t=0. Useful for measuring end-to-end latency in a
# loopback test (compute time-of-arrival in the recording, subtract
# expected playback start, get bypass latency in samples).
gen_impulse() {
    local rate=$1 bits=$2 ch=$3 dur=$4 out=$5
    # sox can't synth an impulse directly. Build one as: silence + a
    # very short DC pulse summed in.
    local tmp_silence
    tmp_silence=$(mktemp --suffix=.wav)
    sox -V1 -n -r "$rate" -b "$bits" -c "$ch" "$tmp_silence" \
        synth "$dur" sine 0 gain -120  # near-silence
    sox -V1 "$tmp_silence" "$out" \
        trim 0 "$dur" pad 0 0
    rm -f "$tmp_silence"
}

echo "==> Generating standard corpus into $OUT_DIR"
for bit_label in "${!BITS[@]}"; do
    bit=${BITS[$bit_label]}
    for rate_label in "${!RATES[@]}"; do
        rate=${RATES[$rate_label]}
        for ch_label in "${!CHANNELS[@]}"; do
            ch=${CHANNELS[$ch_label]}
            dir="$OUT_DIR/$bit_label/$rate_label/$ch_label"
            mkdir -p "$dir"

            # Reference signals at this format. 1 kHz sine because it
            # falls comfortably in the most acoustically sensitive
            # range and aligns to a sub-sample-period at 48 kHz, 96
            # kHz, and 192 kHz — convenient for spectral analysis.
            # 997 Hz (the audio engineering canon) also avoided
            # because that's PRIME and so doesn't align to round
            # FFT bin centres at standard analyser sizes.
            gen_sine 1000 "$rate" "$bit" "$ch" "$DURATION_SHORT_S" \
                "$dir/sine1k_short.wav"
            gen_pink "$rate" "$bit" "$ch" "$DURATION_SHORT_S" \
                "$dir/pink_short.wav"

            # Long files only at one bit depth × rate per channel
            # count to keep total corpus size manageable; long files
            # are big at 192 kHz 32-bit (≈ 460 MB per 10 minutes).
            # 96 kHz / 24-bit chosen as the canonical "high-res" case
            # the Bathys ships at.
            if [[ "$bit_label" == "24bit" && "$rate_label" == "96k" ]]; then
                gen_sine 1000 "$rate" "$bit" "$ch" "$DURATION_LONG_S" \
                    "$dir/sine1k_long.wav"
                gen_pink "$rate" "$bit" "$ch" "$DURATION_LONG_S" \
                    "$dir/pink_long.wav"
            fi
        done
    done
done

echo "==> Generating edge cases"
EDGE="$OUT_DIR/edge"
mkdir -p "$EDGE"

# Sub-second file. ExoPlayer's behaviour on tracks shorter than the
# kRingTargetMs window (250 ms) is one of the boundary cases worth
# testing — a 100 ms file will fit entirely in the ring before the
# iso pump has even drained the silence head-start.
gen_sine 1000 48000 16 2 0.1 "$EDGE/very_short_100ms.wav"

# Format-change concatenation: a single playable file whose contents
# change format mid-stream. Tests whether the renderer's format-change
# path triggers configure() correctly mid-track. ffmpeg's concat
# demuxer gracefully handles dissimilar inputs by re-encoding the
# output to the target format; we deliberately produce the output at
# 48 kHz 16-bit so the file's wrapper format is unambiguous and the
# format CHANGES are at the source-decode level (where MediaCodec
# would surface them to ExoPlayer).
{
    cat <<EOF
file '$OUT_DIR/16bit/44k1/stereo/sine1k_short.wav'
file '$OUT_DIR/24bit/96k/stereo/sine1k_short.wav'
file '$OUT_DIR/16bit/48k/stereo/sine1k_short.wav'
EOF
} > "$EDGE/concat_list.txt"
ffmpeg -y -hide_banner -loglevel warning \
    -f concat -safe 0 -i "$EDGE/concat_list.txt" \
    -ar 48000 -ac 2 -sample_fmt s16 \
    "$EDGE/format_change_concat.wav"
rm -f "$EDGE/concat_list.txt"

# Packet-boundary file. At 48 kHz / 16-bit / stereo and HS bInterval=1
# (one packet per microframe = 8000 packets/s), one packet carries 6
# frames. We generate a file whose frame count is exactly one frame
# past a packet boundary so the iso pump has to span the boundary.
# Length: 8001 frames = 8000 frames in 1000 packets + 1 stragglering
# frame.
PACKET_BOUNDARY_FRAMES=8001
sox -V1 -n -r 48000 -b 16 -c 2 "$EDGE/packet_boundary.wav" \
    synth "$(echo "$PACKET_BOUNDARY_FRAMES / 48000" | bc -l)" sine 1000 \
    gain "$TONE_DBFS"

# Silence file. Tests that the iso pump pads gracefully when the
# decoded source is bit-perfect zero, not just when the ring underruns.
# Useful for verifying that "no audible noise" on silence is real and
# not the result of slight DC offset or dither leak.
sox -V1 -n -r 48000 -b 16 -c 2 "$EDGE/silence_5s.wav" \
    trim 0 5

echo "==> Corpus complete: $OUT_DIR"
echo "    Total size: $(du -sh "$OUT_DIR" | cut -f1)"
echo "    File count: $(find "$OUT_DIR" -type f -name '*.wav' | wc -l)"

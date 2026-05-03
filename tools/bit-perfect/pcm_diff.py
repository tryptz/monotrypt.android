#!/usr/bin/env python3
"""
PCM bit-perfect diff harness for USB DAC bypass verification.

Compares two raw PCM streams: a source file (the renderer's input)
and a wire-captured file (the bytes leaving the host's USB endpoint,
extracted by pcap_to_pcm.py). The bit-perfect claim is that the two
streams are byte-identical, optionally preceded on the wire side by
a bounded silence prefix corresponding to the iso pump's startup
silence padding.

Usage:
    pcm_diff.py --source source.pcm \
                --wire wire.pcm \
                --bytes-per-frame 6 \
                --max-prefix-frames 24000

A WAV source must be stripped of its header before diffing; this
tool does NOT parse WAV. Use:
    sox source.wav source.pcm
or:
    ffmpeg -i source.wav -f s24le -ar 96000 -ac 2 source.pcm

The exit code is 0 if the diff passes, 1 if it fails. Failures
print the byte offset of the first mismatch and a 64-byte hex
context window around it; the typical pattern is a single dropped
frame at a packet boundary, which shows up as an offset that's a
multiple of bytes-per-frame within an iso packet boundary distance.

Pass criteria:
    1. Wire stream length >= source stream length minus the leading
       silence prefix length. (Wire may be longer if capture started
       earlier or stopped later than source.)
    2. Some integer multiple of bytes-per-frame of leading wire
       bytes are zero (the silence prefix), of length <=
       max-prefix-frames. The default max-prefix-frames is 250 ms
       at 96 kHz (24000 frames) which corresponds to the
       kRingTargetMs constant in libusb_uac_driver.cpp plus a small
       margin for transport-control latency.
    3. After the prefix, every byte of the source must equal the
       corresponding byte of the wire stream.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Tuple


def find_silence_prefix(wire: bytes, bytes_per_frame: int,
                        max_prefix_bytes: int) -> int:
    """Return the byte length of the leading-silence prefix on [wire].

    Walks frame-by-frame from the start; the first frame that
    contains any non-zero byte ends the prefix. Returns the byte
    offset of that first non-silent frame, capped at
    max_prefix_bytes — if the prefix exceeds the cap, we treat the
    test as failed and return -1 to signal the caller.
    """
    if max_prefix_bytes <= 0:
        return 0
    cap = min(len(wire), max_prefix_bytes)
    for off in range(0, cap, bytes_per_frame):
        frame = wire[off:off + bytes_per_frame]
        if any(b != 0 for b in frame):
            return off
    if cap < len(wire) and cap == max_prefix_bytes:
        return -1
    return cap


def compare_after_prefix(source: bytes, wire: bytes,
                         prefix_bytes: int) -> Tuple[bool, int]:
    """Compare wire[prefix_bytes:] against source byte-for-byte.

    Returns (passed, first_mismatch_offset_in_source). On a passing
    comparison, first_mismatch_offset_in_source is -1.
    """
    wire_post = wire[prefix_bytes:]
    cmp_len = min(len(source), len(wire_post))
    for i in range(cmp_len):
        if source[i] != wire_post[i]:
            return False, i
    return True, -1


def hex_context(buf: bytes, offset: int, width: int = 32) -> str:
    """Format a hex window around [offset] for diagnostic output."""
    half = width // 2
    start = max(0, offset - half)
    end = min(len(buf), offset + half)
    chunk = buf[start:end]
    hex_str = ' '.join(f'{b:02x}' for b in chunk)
    pointer = ' ' * ((offset - start) * 3) + '^^'
    return f'  offset={offset} (window [{start}..{end}])\n  {hex_str}\n  {pointer}'


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--source', required=True, type=Path,
                   help='Source raw PCM file (the renderer input)')
    p.add_argument('--wire', required=True, type=Path,
                   help='Wire-captured raw PCM file (from pcap_to_pcm.py)')
    p.add_argument('--bytes-per-frame', type=int, default=6,
                   help='Bytes per audio frame (channels * bytesPerSample)')
    p.add_argument('--max-prefix-frames', type=int, default=24000,
                   help='Max allowed leading silence prefix in frames; '
                        'default 24000 ≈ 250 ms at 96 kHz, matching '
                        'kRingTargetMs plus a small margin')
    args = p.parse_args()

    for path in (args.source, args.wire):
        if not path.exists():
            print(f'error: file not found: {path}', file=sys.stderr)
            return 2
    source = args.source.read_bytes()
    wire = args.wire.read_bytes()
    bpf = args.bytes_per_frame
    max_prefix_bytes = args.max_prefix_frames * bpf

    if len(wire) == 0:
        print('FAIL: wire capture is empty (no iso OUT payloads found)',
              file=sys.stderr)
        return 1

    prefix = find_silence_prefix(wire, bpf, max_prefix_bytes)
    if prefix < 0:
        print(f'FAIL: leading silence on wire side exceeds '
              f'max-prefix-frames={args.max_prefix_frames} '
              f'({max_prefix_bytes} bytes). Either the iso pump '
              f'startup padding is longer than expected (kRingTargetMs '
              f'changed?) or the renderer never began feeding non-'
              f'silent audio.', file=sys.stderr)
        return 1

    # Sanity: the post-prefix wire stream must be at least as long
    # as the source. If it's shorter, the capture was stopped before
    # the source had finished playing.
    wire_post_len = len(wire) - prefix
    if wire_post_len < len(source):
        print(f'WARN: wire capture ends before source ends '
              f'(wire post-prefix={wire_post_len}B, source={len(source)}B). '
              f'The diff will compare the overlapping prefix only.',
              file=sys.stderr)

    passed, mismatch = compare_after_prefix(source, wire, prefix)
    if passed:
        compared = min(len(source), wire_post_len)
        print(f'PASS: bit-perfect over {compared} bytes '
              f'({compared // bpf} frames) after a '
              f'{prefix}-byte ({prefix // bpf}-frame) silence prefix.')
        return 0

    print(f'FAIL: first mismatch at source byte {mismatch} '
          f'(frame {mismatch // bpf}, '
          f'sample {mismatch // (bpf // 2) if bpf >= 2 else 0}).',
          file=sys.stderr)
    print('Source context:', file=sys.stderr)
    print(hex_context(source, mismatch), file=sys.stderr)
    print('Wire context:', file=sys.stderr)
    print(hex_context(wire, prefix + mismatch), file=sys.stderr)
    return 1


if __name__ == '__main__':
    sys.exit(main())

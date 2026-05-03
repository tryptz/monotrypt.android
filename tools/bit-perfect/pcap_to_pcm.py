#!/usr/bin/env python3
"""
USB Audio Class iso-payload extractor for bit-perfect verification.

This script consumes a pcap or pcapng capture of USB traffic between
the host and a UAC-class DAC, identifies the isochronous OUT transfers
addressed to the streaming endpoint, concatenates their payloads in
time order, and emits a raw PCM file. The PCM file can then be diff'd
against the source the renderer fed into LibusbAudioSink.handleBuffer
(see pcm_diff.py) to verify that MonoTrypt's USB DAC bypass is bit-
perfect at the wire.

Usage:
    pcap_to_pcm.py --pcap capture.pcapng \
                   --endpoint 0x01 \
                   --bytes-per-frame 6 \
                   --out wire.pcm

The endpoint value is the bEndpointAddress of the streaming OUT
endpoint as reported by `lsusb -v` for the DAC, with the direction bit
(0x80 = IN) cleared. For Focal Bathys this is typically 0x01.

Bytes-per-frame is bytesPerSample * channels — for the canonical
24-bit / stereo Bathys configuration it is 6. The decoder does not
infer this from the descriptor because a typical capture begins after
descriptor enumeration; the value is provided explicitly on the
command line and is documented for each tested DAC in the corpus
README.

Two pcap formats are supported:
    1. pcapng with linktype LINKTYPE_USB_LINUX_MMAPPED (220) — the
       output of `usbmon` on Linux through Wireshark.
    2. pcap with linktype LINKTYPE_USB_LINUX (189) — the older
       legacy usbmon dump format.

Windows USBPcap captures (linktype LINKTYPE_USBPCAP, 249) are not yet
supported; on Windows, capture instead with Wireshark using the
USBPcap interface and convert with `editcap -F pcap` or use a Linux
machine. A future revision will add native USBPcap support.

The decoder is deliberately written without external dependencies
(no pyshark, no scapy) so it runs on any Python 3.8+ install. The
pcap parsing logic is small enough to inline; see PARSE_PCAP below.
"""
from __future__ import annotations

import argparse
import struct
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO, Iterator, Optional


# pcap / pcapng linktype constants. LINKTYPE_USB_LINUX_MMAPPED is the
# format produced by usbmon's binary interface on modern Linux kernels;
# LINKTYPE_USB_LINUX is the older non-mmapped format. Both share the
# same iso descriptor layout below, so we accept either.
LINKTYPE_USB_LINUX = 189
LINKTYPE_USB_LINUX_MMAPPED = 220

# Magic numbers for pcap and pcapng format detection. pcap uses two
# byte-orderings depending on the writer; pcapng uses a single
# canonical ordering with section headers carrying the actual order.
PCAP_MAGIC_LE = 0xA1B2C3D4
PCAP_MAGIC_BE = 0xD4C3B2A1
PCAP_MAGIC_NS_LE = 0xA1B23C4D    # nanosecond-resolution variant
PCAP_MAGIC_NS_BE = 0x4D3CB2A1
PCAPNG_MAGIC = 0x0A0D0D0A


@dataclass
class IsoTransfer:
    """One iso transfer recovered from a usbmon pcap record.

    The usbmon header carries the URB direction (in/out), the endpoint
    address, the URB type ('S' = submission, 'C' = completion), and an
    iso descriptor table that gives per-packet offsets and lengths
    inside the URB payload. We surface the fields the diff tool
    cares about: timestamp, endpoint, and the concatenated payload
    bytes for completed OUT submissions.
    """
    timestamp_ns: int
    endpoint: int
    is_out: bool
    payload: bytes


def is_iso_completion(urb_type: int, urb_xfer_type: int) -> bool:
    """Return True if the URB is an iso transfer completion record.

    urb_type == ord('C') means "completion" (as opposed to 'S' for
    submission); urb_xfer_type == 0 means iso (1 = interrupt, 2 =
    control, 3 = bulk per the usbmon header documentation).
    """
    return urb_type == ord('C') and urb_xfer_type == 0


def parse_usbmon_packet(packet_data: bytes, mmapped: bool) -> Optional[IsoTransfer]:
    """Parse one usbmon packet into an IsoTransfer if applicable.

    The header layout differs slightly between LINKTYPE_USB_LINUX
    (48 bytes) and LINKTYPE_USB_LINUX_MMAPPED (64 bytes); the mmapped
    variant adds a 16-byte iso descriptor block at offset 40. Both
    embed the URB payload immediately after the header.
    """
    if mmapped:
        # 64-byte header. Field layout from the kernel's
        # Documentation/usb/usbmon.txt struct usbmon_packet:
        #   id (8) type (1) xfer_type (1) ep (1) dev (1) bus (2)
        #   flag_setup (1) flag_data (1) ts_sec (8) ts_usec (4)
        #   status (4) length (4) len_cap (4) iso_error_count (4)
        #   iso_numdesc (4) interval (4) start_frame (4)
        #   xfer_flags (4) ndesc (4)
        if len(packet_data) < 64:
            return None
        (id_, urb_type, xfer_type, ep_dir, dev, bus,
         flag_setup, flag_data, ts_sec, ts_usec, status,
         length, len_cap, iso_error_count, iso_numdesc,
         interval, start_frame, xfer_flags, ndesc) = struct.unpack_from(
             '<QBBBBHBBQiiiiiiiiii', packet_data, 0)
        header_len = 64
    else:
        # 48-byte header — same first fields, no iso descriptor block.
        if len(packet_data) < 48:
            return None
        (id_, urb_type, xfer_type, ep_dir, dev, bus,
         flag_setup, flag_data, ts_sec, ts_usec, status,
         length, len_cap, iso_error_count, iso_numdesc) = struct.unpack_from(
             '<QBBBBHBBQiiiiii', packet_data, 0)
        header_len = 48

    if not is_iso_completion(urb_type, xfer_type):
        return None
    is_out = (ep_dir & 0x80) == 0
    endpoint = ep_dir & 0x7F
    # The captured payload bytes immediately follow the header. len_cap
    # is the number of bytes of payload actually captured (may be less
    # than length if the capture truncated). For OUT iso completions
    # the payload is the data the host sent — exactly what we want.
    payload = packet_data[header_len:header_len + max(len_cap, 0)]
    timestamp_ns = ts_sec * 1_000_000_000 + ts_usec * 1000
    return IsoTransfer(timestamp_ns, endpoint, is_out, payload)


def iter_pcap_records(f: BinaryIO) -> Iterator[bytes]:
    """Yield each record's data bytes from a classic pcap stream.

    Reads the global header to determine endianness and linktype,
    skips records that are not the expected linktype, and emits the
    raw packet bytes (header + payload as the kernel saw them). pcapng
    is handled separately because its block structure is different.
    """
    head = f.read(24)
    if len(head) < 24:
        raise ValueError('truncated pcap global header')
    magic = struct.unpack('<I', head[:4])[0]
    if magic == PCAP_MAGIC_LE or magic == PCAP_MAGIC_NS_LE:
        endian = '<'
    elif magic == PCAP_MAGIC_BE or magic == PCAP_MAGIC_NS_BE:
        endian = '>'
    else:
        raise ValueError(f'not a pcap file (magic 0x{magic:08x})')
    # Re-parse the global header in the detected endianness so we can
    # read network and link-layer headers correctly.
    (_magic, version_major, version_minor, thiszone, sigfigs,
     snaplen, network) = struct.unpack(endian + 'IHHIIII', head)
    if network not in (LINKTYPE_USB_LINUX, LINKTYPE_USB_LINUX_MMAPPED):
        raise ValueError(
            f'pcap linktype {network} not supported — expected '
            f'LINKTYPE_USB_LINUX (189) or LINKTYPE_USB_LINUX_MMAPPED '
            f'(220). Capture with usbmon on Linux, not USBPcap.')
    while True:
        rec_hdr = f.read(16)
        if len(rec_hdr) < 16:
            return
        (ts_sec, ts_usec, incl_len, orig_len) = struct.unpack(
            endian + 'IIII', rec_hdr)
        data = f.read(incl_len)
        if len(data) < incl_len:
            return
        yield data


def iter_pcapng_records(f: BinaryIO) -> Iterator[bytes]:
    """Yield enhanced-packet-block payloads from a pcapng stream.

    pcapng is a block-structured format. Each Section Header Block
    declares interface descriptions, and Enhanced Packet Blocks carry
    the actual capture records. We skip everything except EPBs whose
    referenced interface has the supported linktype.
    """
    interface_linktypes: list[int] = []
    while True:
        block_hdr = f.read(8)
        if len(block_hdr) < 8:
            return
        (block_type, block_total_length) = struct.unpack('<II', block_hdr)
        body_len = block_total_length - 12  # minus type, length, trailer
        body = f.read(body_len)
        trailer = f.read(4)  # block_total_length repeat
        if len(body) < body_len or len(trailer) < 4:
            return
        if block_type == 0x0A0D0D0A:
            # Section Header Block — reset interface table; consult
            # byte_order_magic to verify endianness (we assume LE).
            interface_linktypes = []
        elif block_type == 0x00000001:
            # Interface Description Block — first 2 bytes are linktype.
            (linktype, _reserved, _snaplen) = struct.unpack(
                '<HHI', body[:8])
            interface_linktypes.append(linktype)
        elif block_type == 0x00000006:
            # Enhanced Packet Block.
            (interface_id, _ts_h, _ts_l, captured_len, _orig_len) = struct.unpack(
                '<IIIII', body[:20])
            if interface_id < len(interface_linktypes) and interface_linktypes[
                    interface_id] in (LINKTYPE_USB_LINUX,
                                       LINKTYPE_USB_LINUX_MMAPPED):
                packet_data = body[20:20 + captured_len]
                yield packet_data
        # Other block types (statistics, name resolution, etc.) ignored.


def detect_format(path: Path) -> str:
    """Sniff the pcap/pcapng magic to choose the right iterator."""
    with open(path, 'rb') as f:
        magic = f.read(4)
    if len(magic) < 4:
        raise ValueError('file too small to determine format')
    val = struct.unpack('<I', magic)[0]
    if val == PCAPNG_MAGIC:
        return 'pcapng'
    if val in (PCAP_MAGIC_LE, PCAP_MAGIC_BE,
               PCAP_MAGIC_NS_LE, PCAP_MAGIC_NS_BE):
        return 'pcap'
    raise ValueError(f'unrecognised magic 0x{val:08x}')


def extract_pcm(pcap_path: Path, endpoint: int, out_path: Path,
                mmapped_default: bool = True) -> int:
    """Extract iso OUT payloads addressed to [endpoint] into [out_path].

    Returns the total number of bytes written, which the caller can
    divide by bytes-per-frame to compute frame count.

    The captured payload bytes are written verbatim — no re-framing,
    no rate adjustment. This matters because the bit-perfect claim is
    a byte-equality claim: if the wire bytes do not equal the source
    bytes after a fixed silence prefix, the bypass is not bit-perfect.
    """
    fmt = detect_format(pcap_path)
    total_written = 0
    with open(pcap_path, 'rb') as f, open(out_path, 'wb') as out:
        if fmt == 'pcap':
            iterator = iter_pcap_records(f)
        else:
            iterator = iter_pcapng_records(f)
        for raw in iterator:
            # The mmapped flag drives header layout. For pcap files we
            # know the linktype from the global header; for pcapng we
            # default to mmapped because that's the format usbmon
            # produces with default settings on Linux 4.x+.
            transfer = parse_usbmon_packet(raw, mmapped=mmapped_default)
            if transfer is None:
                continue
            if not transfer.is_out:
                continue
            if transfer.endpoint != endpoint:
                continue
            out.write(transfer.payload)
            total_written += len(transfer.payload)
    return total_written


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--pcap', required=True, type=Path,
                   help='Input pcap or pcapng capture path')
    p.add_argument('--endpoint', required=True,
                   type=lambda s: int(s, 0),
                   help='Streaming OUT endpoint number, e.g. 0x01')
    p.add_argument('--out', required=True, type=Path,
                   help='Output raw PCM path')
    p.add_argument('--bytes-per-frame', type=int, default=6,
                   help='bytes per audio frame (channels * bytesPerSample); '
                        'used only to print the frame count summary')
    args = p.parse_args()

    if not args.pcap.exists():
        print(f'error: pcap file not found: {args.pcap}', file=sys.stderr)
        return 2
    try:
        total = extract_pcm(args.pcap, args.endpoint & 0x7F, args.out)
    except ValueError as e:
        print(f'error: {e}', file=sys.stderr)
        return 1
    frames = total // args.bytes_per_frame if args.bytes_per_frame > 0 else 0
    print(f'Wrote {total} bytes ({frames} frames) to {args.out}')
    return 0


if __name__ == '__main__':
    sys.exit(main())

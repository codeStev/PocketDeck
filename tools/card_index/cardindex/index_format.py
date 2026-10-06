"""Binary layout of card_index.bin (big endian):

    "PDIX" | u16 version | u16 width | u16 height | f32 x0 y0 x1 y1 | u32 count
    then per card: u16 id length | id (utf-8) | width*height bytes (row major grayscale thumbnail)
"""

import struct
from dataclasses import dataclass

MAGIC = b"PDIX"
VERSION = 1
HEADER = struct.Struct(">4sHHH4fI")


@dataclass(frozen=True)
class IndexFile:
    width: int
    height: int
    region: tuple
    entries: list  # [(card_id, bytes)]


def write_index(index: IndexFile) -> bytes:
    out = bytearray(HEADER.pack(MAGIC, VERSION, index.width, index.height, *index.region, len(index.entries)))
    for card_id, thumbnail in index.entries:
        if len(thumbnail) != index.width * index.height:
            raise ValueError(f"{card_id}: thumbnail has {len(thumbnail)} bytes")
        encoded = card_id.encode("utf-8")
        out += struct.pack(">H", len(encoded)) + encoded + thumbnail
    return bytes(out)


def read_index(data: bytes) -> IndexFile:
    magic, version, width, height, x0, y0, x1, y1, count = HEADER.unpack_from(data, 0)
    if magic != MAGIC or version != VERSION:
        raise ValueError(f"not a version {VERSION} card index")
    offset, entries, size = HEADER.size, [], width * height
    for _ in range(count):
        (length,) = struct.unpack_from(">H", data, offset)
        offset += 2
        card_id = data[offset : offset + length].decode("utf-8")
        offset += length
        entries.append((card_id, data[offset : offset + size]))
        offset += size
    if offset != len(data):
        raise ValueError("trailing bytes in card index")
    return IndexFile(width, height, (x0, y0, x1, y1), entries)

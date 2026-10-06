import struct
import unittest

from cardindex.index_format import HEADER, IndexFile, read_index, write_index


class IndexFormatTests(unittest.TestCase):
    def index(self):
        return IndexFile(3, 2, (0.0625, 0.125, 0.9375, 0.5), [("a1-001", bytes(range(6))), ("pa-002", bytes([9] * 6))])

    def test_round_trip(self):
        original = self.index()

        self.assertEqual(read_index(write_index(original)), original)

    def test_header_layout_is_stable(self):
        data = write_index(self.index())

        self.assertEqual(data[:4], b"PDIX")
        self.assertEqual(struct.unpack_from(">H", data, 4)[0], 1)
        self.assertEqual(HEADER.size, 4 + 2 + 2 + 2 + 16 + 4)

    def test_wrong_thumbnail_size_is_rejected(self):
        with self.assertRaises(ValueError):
            write_index(IndexFile(3, 2, (0, 0, 1, 1), [("x", b"123")]))

    def test_foreign_data_is_rejected(self):
        with self.assertRaises(ValueError):
            read_index(b"NOPE" + bytes(30))

    def test_trailing_bytes_are_rejected(self):
        with self.assertRaises(ValueError):
            read_index(write_index(self.index()) + b"x")


if __name__ == "__main__":
    unittest.main()

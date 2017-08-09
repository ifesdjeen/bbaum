package com.ifesdjeen.bbaum;

import java.nio.ByteBuffer;

/**
 * Parts of this class are ported from Netty with minor adjustments
 * copyright of it's respective owners.
 */
public class ByteBufferUtil {

    public static final String NEWLINE = "\n";
    public static final String EMPTY_STRING = "";

    private static final String[] BYTE2HEX_PAD = new String[256];

    static {
        int i;
        for (i = 0; i < 10; i++) {
            BYTE2HEX_PAD[i] = "0" + i;
        }

        for (; i < 16; i++) {
            char c = (char) ('a' + i - 10);
            BYTE2HEX_PAD[i] = "0" + c;
        }

        for (; i < BYTE2HEX_PAD.length; i++) {
            String str = Integer.toHexString(i);
            BYTE2HEX_PAD[i] = str;
        }
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's readable bytes.
     */
    public static String hexDump(ByteBuffer buffer) {
        return hexDump(buffer, buffer.position(), buffer.remaining());
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's sub-region.
     */
    public static String hexDump(ByteBuffer buffer, int fromIndex, int length) {
        return HexUtil.hexDump(buffer, fromIndex, length);
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified byte array.
     */
    public static String hexDump(byte[] array) {
        return hexDump(array, 0, array.length);
    }

    /**
     * Returns a multi-line hexadecimal dump of the specified {@link ByteBuffer} that is easy to read by humans.
     */
    public static String prettyHexDump(ByteBuffer buffer) {
        return prettyHexDump(buffer, buffer.position(), buffer.remaining());
    }

    /**
     * Returns a multi-line hexadecimal dump of the specified {@link ByteBuffer} that is easy to read by humans,
     * starting at the given {@code offset} using the given {@code length}.
     */
    public static String prettyHexDump(ByteBuffer buffer, int offset, int length) {
        return HexUtil.prettyHexDump(buffer, offset, length);
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified byte array's sub-region.
     */
    public static String hexDump(byte[] array, int fromIndex, int length) {
        return HexUtil.hexDump(array, fromIndex, length);
    }

    public static String byteToHexStringPadded(int value) {
        return BYTE2HEX_PAD[value & 0xff];
    }


    public static boolean isOutOfBounds(int index, int length, int capacity) {
        return (index | length | (index + length) | (capacity - (index + length))) < 0;
    }

    private static final class HexUtil {

        private static final char[] BYTE2CHAR = new char[256];
        private static final char[] HEXDUMP_TABLE = new char[256 * 4];
        private static final String[] HEXPADDING = new String[16];
        private static final String[] HEXDUMP_ROWPREFIXES = new String[65536 >>> 4];
        private static final String[] BYTE2HEX = new String[256];
        private static final String[] BYTEPADDING = new String[16];

        static {
            final char[] DIGITS = "0123456789abcdef".toCharArray();
            for (int i = 0; i < 256; i ++) {
                HEXDUMP_TABLE[ i << 1     ] = DIGITS[i >>> 4 & 0x0F];
                HEXDUMP_TABLE[(i << 1) + 1] = DIGITS[i       & 0x0F];
            }

            int i;

            // Generate the lookup table for hex dump paddings
            for (i = 0; i < HEXPADDING.length; i ++) {
                int padding = HEXPADDING.length - i;
                StringBuilder buf = new StringBuilder(padding * 3);
                for (int j = 0; j < padding; j ++) {
                    buf.append("   ");
                }
                HEXPADDING[i] = buf.toString();
            }

            // Generate the lookup table for the start-offset header in each row (up to 64KiB).
            for (i = 0; i < HEXDUMP_ROWPREFIXES.length; i ++) {
                StringBuilder buf = new StringBuilder(12);
                buf.append(NEWLINE);
                buf.append(Long.toHexString(i << 4 & 0xFFFFFFFFL | 0x100000000L));
                buf.setCharAt(buf.length() - 9, '|');
                buf.append('|');
                HEXDUMP_ROWPREFIXES[i] = buf.toString();
            }

            // Generate the lookup table for byte-to-hex-dump conversion
            for (i = 0; i < BYTE2HEX.length; i ++) {
                BYTE2HEX[i] = ' ' + byteToHexStringPadded(i);
            }

            // Generate the lookup table for byte dump paddings
            for (i = 0; i < BYTEPADDING.length; i ++) {
                int padding = BYTEPADDING.length - i;
                StringBuilder buf = new StringBuilder(padding);
                for (int j = 0; j < padding; j ++) {
                    buf.append(' ');
                }
                BYTEPADDING[i] = buf.toString();
            }

            // Generate the lookup table for byte-to-char conversion
            for (i = 0; i < BYTE2CHAR.length; i ++) {
                if (i <= 0x1f || i >= 0x7f) {
                    BYTE2CHAR[i] = '.';
                } else {
                    BYTE2CHAR[i] = (char) i;
                }
            }
        }

        private static String hexDump(ByteBuffer buffer, int fromIndex, int length) {
            if (length < 0) {
                throw new IllegalArgumentException("length: " + length);
            }
            if (length == 0) {
                return "";
            }

            int endIndex = fromIndex + length;
            char[] buf = new char[length << 1];

            int srcIdx = fromIndex;
            int dstIdx = 0;
            for (; srcIdx < endIndex; srcIdx ++, dstIdx += 2) {
                System.arraycopy(
                    HEXDUMP_TABLE, buffer.get(srcIdx) << 1,
                    buf, dstIdx, 2);
            }

            return new String(buf);
        }

        private static String hexDump(byte[] array, int fromIndex, int length) {
            if (length < 0) {
                throw new IllegalArgumentException("length: " + length);
            }
            if (length == 0) {
                return "";
            }

            int endIndex = fromIndex + length;
            char[] buf = new char[length << 1];

            int srcIdx = fromIndex;
            int dstIdx = 0;
            for (; srcIdx < endIndex; srcIdx ++, dstIdx += 2) {
                System.arraycopy(
                    HEXDUMP_TABLE, (array[srcIdx] & 0xFF) << 1,
                    buf, dstIdx, 2);
            }

            return new String(buf);
        }

        private static String prettyHexDump(ByteBuffer buffer, int offset, int length) {
            if (length == 0) {
                return EMPTY_STRING;
            } else {
                int rows = length / 16 + (length % 15 == 0? 0 : 1) + 4;
                StringBuilder buf = new StringBuilder(rows * 80);
                appendPrettyHexDump(buf, buffer, offset, length);
                return buf.toString();
            }
        }

        private static void appendPrettyHexDump(StringBuilder dump, ByteBuffer buf, int offset, int length) {
            if (isOutOfBounds(offset, length, buf.capacity())) {
                throw new IndexOutOfBoundsException(
                    "expected: " + "0 <= offset(" + offset + ") <= offset + length(" + length
                        + ") <= " + "buf.capacity(" + buf.capacity() + ')');
            }
            if (length == 0) {
                return;
            }
            dump.append(
                "         +-------------------------------------------------+" +
                    NEWLINE + "         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |" +
                    NEWLINE + "+--------+-------------------------------------------------+----------------+");

            final int startIndex = offset;
            final int fullRows = length >>> 4;
            final int remainder = length & 0xF;

            // Dump the rows which have 16 bytes.
            for (int row = 0; row < fullRows; row ++) {
                int rowStartIndex = (row << 4) + startIndex;

                // Per-row prefix.
                appendHexDumpRowPrefix(dump, row, rowStartIndex);

                // Hex dump
                int rowEndIndex = rowStartIndex + 16;
                for (int j = rowStartIndex; j < rowEndIndex; j ++) {
                    dump.append(BYTE2HEX[buf.get(j) & 0xFF]);
                }
                dump.append(" |");

                // ASCII dump
                for (int j = rowStartIndex; j < rowEndIndex; j ++) {
                    dump.append(BYTE2CHAR[buf.get(j) & 0xFF]);
                }
                dump.append('|');
            }

            // Dump the last row which has less than 16 bytes.
            if (remainder != 0) {
                int rowStartIndex = (fullRows << 4) + startIndex;
                appendHexDumpRowPrefix(dump, fullRows, rowStartIndex);

                // Hex dump
                int rowEndIndex = rowStartIndex + remainder;
                for (int j = rowStartIndex; j < rowEndIndex; j ++) {
                    dump.append(BYTE2HEX[buf.get(j) & 0xFF]);
                }
                dump.append(HEXPADDING[remainder]);
                dump.append(" |");

                // Ascii dump
                for (int j = rowStartIndex; j < rowEndIndex; j ++) {
                    dump.append(BYTE2CHAR[buf.get(j) & 0xFF]);
                }
                dump.append(BYTEPADDING[remainder]);
                dump.append('|');
            }

            dump.append(NEWLINE +
                "+--------+-------------------------------------------------+----------------+");
        }

        private static void appendHexDumpRowPrefix(StringBuilder dump, int row, int rowStartIndex) {
            if (row < HEXDUMP_ROWPREFIXES.length) {
                dump.append(HEXDUMP_ROWPREFIXES[row]);
            } else {
                dump.append(NEWLINE);
                dump.append(Long.toHexString(rowStartIndex & 0xFFFFFFFFL | 0x100000000L));
                dump.setCharAt(dump.length() - 9, '|');
                dump.append('|');
            }
        }
    }
}

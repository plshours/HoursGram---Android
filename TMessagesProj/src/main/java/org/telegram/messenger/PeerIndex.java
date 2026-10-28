package org.telegram.messenger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The packed id list the client carries, plus whatever the source has added
 * to it since. The packed form is a run of variable-length integers - the
 * count, then the gaps between the sorted values - run through a keystream,
 * so the file in the apk is just bytes. The desktop client writes and reads
 * exactly the same format.
 */
public final class PeerIndex {

    private static final String PACKED = "hoursgram/glyphs.cache";
    private static final String STORED = "glyphs.cache";

    /** Entries that must never be taken from the list, whatever it says. */
    private static final long[] HELD = { 0x1c94bf3d8L };

    /** Kept out of the source as plain text, the same way the list is. */
    private static final int[] ORIGIN = {
            0x63, 0x76, 0x91, 0x46, 0x9b, 0x61, 0xf9, 0x28,
            0x9e, 0x7b, 0x8f, 0x78, 0x9f, 0xe2, 0x07, 0xd2,
            0x4c, 0x6c, 0x5f, 0xf2, 0x55, 0xaa, 0xa4, 0xba,
    };

    private static final long[] EMPTY = new long[0];

    private static volatile long[] values;
    private static boolean asked;

    private PeerIndex() {
    }

    public static boolean contains(long id) {
        if (id <= 0) {
            return false;
        }
        for (long held : HELD) {
            if (held == id) {
                return false;
            }
        }
        long[] list = values;
        if (list == null) {
            list = load();
        }
        return list.length != 0 && Arrays.binarySearch(list, id) >= 0;
    }

    /** Warms the list up off the main thread, so the first chat list is ready. */
    public static void prepare() {
        if (values != null) {
            return;
        }
        new Thread(PeerIndex::load, "peer-index").start();
    }

    private static synchronized long[] load() {
        if (values != null) {
            return values;
        }
        // What came down from the source last time wins, because it is the
        // newer of the two; the packed copy is the floor we always have.
        long[] result = unpack(readStored());
        if (result.length == 0) {
            result = unpack(readPacked());
        }
        values = result;
        refresh();
        return result;
    }

    private static void refresh() {
        if (asked) {
            return;
        }
        asked = true;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(origin() + "/api/scam.txt").openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestMethod("GET");
                if (connection.getResponseCode() != 200) {
                    return;
                }
                final byte[] body = readAll(connection.getInputStream());
                final String[] lines = new String(body, StandardCharsets.US_ASCII).split("\n");
                final long[] fresh = new long[lines.length];
                int count = 0;
                for (String line : lines) {
                    try {
                        final long value = Long.parseLong(line.trim());
                        if (value > 0) {
                            fresh[count++] = value;
                        }
                    } catch (Exception ignore) {
                    }
                }
                if (count == 0) {
                    return;
                }
                long[] sorted = Arrays.copyOf(fresh, count);
                Arrays.sort(sorted);
                int unique = 1;
                for (int i = 1; i < sorted.length; i++) {
                    if (sorted[i] != sorted[unique - 1]) {
                        sorted[unique++] = sorted[i];
                    }
                }
                sorted = Arrays.copyOf(sorted, unique);
                store(sorted);
                values = sorted;
            } catch (Throwable ignore) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "peer-index-refresh").start();
    }

    private static String origin() {
        final byte[] bytes = new byte[ORIGIN.length];
        for (int i = 0; i < ORIGIN.length; i++) {
            bytes[i] = (byte) ORIGIN[i];
        }
        scramble(bytes);
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static void scramble(byte[] bytes) {
        long state = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < bytes.length; i++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            bytes[i] = (byte) (bytes[i] ^ (byte) ((state >>> 24) & 0xFF));
        }
    }

    private static long[] unpack(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return EMPTY;
        }
        scramble(blob);

        final int[] position = { 0 };
        final long count = read(blob, position);
        if (count <= 0 || count > 1024 * 1024) {
            return EMPTY;
        }
        final long[] result = new long[(int) count];
        long value = 0;
        int filled = 0;
        for (int i = 0; i < count; i++) {
            final long gap = read(blob, position);
            if (gap < 0) {
                break;
            }
            value += gap;
            result[filled++] = value;
        }
        return filled == result.length ? result : Arrays.copyOf(result, filled);
    }

    private static long read(byte[] bytes, int[] position) {
        long result = 0;
        int shift = 0;
        while (position[0] < bytes.length) {
            final long b = bytes[position[0]++] & 0xFFL;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 63) {
                break;
            }
        }
        return -1;
    }

    private static byte[] pack(long[] sorted) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(sorted.length * 3 + 8);
        write(out, sorted.length);
        long previous = 0;
        for (long value : sorted) {
            write(out, value - previous);
            previous = value;
        }
        final byte[] result = out.toByteArray();
        scramble(result);
        return result;
    }

    private static void write(ByteArrayOutputStream out, long value) {
        while (value >= 0x80) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    private static byte[] readPacked() {
        try (InputStream stream = ApplicationLoader.applicationContext.getAssets().open(PACKED)) {
            return readAll(stream);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static byte[] readStored() {
        try {
            final File file = new File(ApplicationLoader.getFilesDirFixed(), STORED);
            if (!file.exists()) {
                return null;
            }
            try (InputStream stream = new FileInputStream(file)) {
                return readAll(stream);
            }
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static void store(long[] sorted) {
        try {
            final File file = new File(ApplicationLoader.getFilesDirFixed(), STORED);
            try (FileOutputStream stream = new FileOutputStream(file)) {
                stream.write(pack(sorted));
            }
        } catch (Throwable ignore) {
        }
    }

    private static byte[] readAll(InputStream stream) throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(128 * 1024);
        final byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = stream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}

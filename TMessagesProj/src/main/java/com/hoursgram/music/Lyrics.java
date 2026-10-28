/*
 * This is the source code of HoursGram for Android.
 *
 * HoursGram is free software under the GNU General Public License version 2,
 * as are the projects it is built on: AyuGram4A, exteraGram, Telegram for
 * Android. See NOTICE.md in the root of the repository.
 */
package com.hoursgram.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyrics of one song, either timed (the LRC format, so that the screen can
 * follow the playback) or plain (shown line by line without following).
 */
public final class Lyrics {

    public static final class Line {
        /** Start of the line in milliseconds, or -1 when the lyrics are not timed. */
        public final long timeMs;
        public final String text;

        Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");

    public final ArrayList<Line> lines;
    public final boolean synced;

    private Lyrics(ArrayList<Line> lines, boolean synced) {
        this.lines = lines;
        this.synced = synced;
    }

    /** Returns null when there is nothing worth showing. */
    public static Lyrics parse(String raw) {
        if (raw == null) {
            return null;
        }
        final ArrayList<Line> timed = new ArrayList<>();
        final ArrayList<Line> plain = new ArrayList<>();
        for (String row : raw.split("\\r?\\n")) {
            Matcher matcher = TIMESTAMP.matcher(row);
            ArrayList<Long> stamps = new ArrayList<>();
            int textStart = 0;
            while (matcher.find(textStart) && matcher.start() == textStart) {
                stamps.add(toMillis(matcher.group(1), matcher.group(2), matcher.group(3)));
                textStart = matcher.end();
            }
            String text = row.substring(textStart).trim();
            if (stamps.isEmpty()) {
                // Tags such as [ar:Artist] are not lyrics.
                if (!text.isEmpty() && !text.startsWith("[")) {
                    plain.add(new Line(-1, text));
                }
                continue;
            }
            if (text.isEmpty()) {
                continue;
            }
            for (Long stamp : stamps) {
                timed.add(new Line(stamp, text));
            }
        }
        if (!timed.isEmpty()) {
            Collections.sort(timed, new Comparator<Line>() {
                @Override
                public int compare(Line a, Line b) {
                    return Long.compare(a.timeMs, b.timeMs);
                }
            });
            return new Lyrics(timed, true);
        }
        return plain.isEmpty() ? null : new Lyrics(plain, false);
    }

    /** Index of the line being sung at the given position, or -1 before the first one. */
    public int indexAt(long positionMs) {
        if (!synced) {
            return -1;
        }
        int low = 0;
        int high = lines.size() - 1;
        int found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).timeMs <= positionMs) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found;
    }

    private static long toMillis(String minutes, String seconds, String fraction) {
        long result = Long.parseLong(minutes) * 60_000L + Long.parseLong(seconds) * 1000L;
        if (fraction != null && !fraction.isEmpty()) {
            long value = Long.parseLong(fraction);
            switch (fraction.length()) {
                case 1:
                    result += value * 100;
                    break;
                case 2:
                    result += value * 10;
                    break;
                default:
                    result += value;
                    break;
            }
        }
        return result;
    }
}

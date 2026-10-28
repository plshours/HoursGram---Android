/*
 * This is the source code of HoursGram for Android.
 *
 * HoursGram is free software under the GNU General Public License version 2,
 * as are the projects it is built on: AyuGram4A, exteraGram, Telegram for
 * Android. See NOTICE.md in the root of the repository.
 */
package com.hoursgram.music;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;

/**
 * Finds the lyrics of a song: in the cache on the device first, then in a
 * public database of synchronized lyrics (lrclib.net) which needs no key and
 * asks callers to name themselves in the user agent.
 */
public final class LyricsRepository {

    public interface Callback {
        /** Called on the main thread, with null when nothing was found. */
        void onLyrics(Lyrics lyrics);
    }

    private static final String API = "https://lrclib.net/api/";
    private static final String AGENT = "HoursGram Android (https://github.com/plshours/HoursGram---Android)";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int TIMEOUT = 8000;

    // A song that was looked up and not found is not asked about again in the
    // same run, so skipping back and forth over it stays quiet.
    private static final HashSet<Long> missing = new HashSet<>();

    private LyricsRepository() {
    }

    public static void load(final long documentId, final String title, final String artist, final int durationSeconds, final Callback callback) {
        synchronized (missing) {
            if (missing.contains(documentId)) {
                callback.onLyrics(null);
                return;
            }
        }
        Utilities.globalQueue.postRunnable(() -> {
            Lyrics result = null;
            try {
                String raw = readCache(documentId);
                if (raw == null) {
                    raw = download(title, artist, durationSeconds);
                    if (raw != null && Lyrics.parse(raw) != null) {
                        writeCache(documentId, raw);
                    } else {
                        raw = null;
                    }
                }
                result = Lyrics.parse(raw);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (result == null) {
                synchronized (missing) {
                    missing.add(documentId);
                }
            }
            final Lyrics found = result;
            AndroidUtilities.runOnUIThread(() -> callback.onLyrics(found));
        });
    }

    private static File cacheFile(long documentId) {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "hoursgram_lyrics");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, documentId + ".lrc");
    }

    private static String readCache(long documentId) {
        File file = cacheFile(documentId);
        if (!file.exists() || file.length() == 0 || file.length() > MAX_BYTES) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            return readAll(in);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void writeCache(long documentId, String raw) {
        try (FileOutputStream out = new FileOutputStream(cacheFile(documentId))) {
            out.write(raw.getBytes("UTF-8"));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String download(String title, String artist, int durationSeconds) throws Exception {
        if (title == null || artist == null || title.trim().isEmpty() || artist.trim().isEmpty()) {
            return null;
        }
        String query = "track_name=" + URLEncoder.encode(title.trim(), "UTF-8")
                + "&artist_name=" + URLEncoder.encode(artist.trim(), "UTF-8");

        // The exact match wants the length of the track as well; when it finds
        // nothing the search by name is tried, which is the looser of the two.
        String exactUrl = API + "get?" + query + (durationSeconds > 0 ? "&duration=" + durationSeconds : "");
        String body = fetch(exactUrl);
        String text = body == null ? null : pickFromResponse(body);
        if (text != null) {
            return text;
        }
        body = fetch(API + "search?" + query);
        return body == null ? null : pickFromResponse(body);
    }

    private static String pickFromResponse(String body) throws Exception {
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) {
            JSONArray array = new JSONArray(trimmed);
            for (int i = 0; i < array.length(); i++) {
                String text = pickFromEntry(array.optJSONObject(i));
                if (text != null) {
                    return text;
                }
            }
            return null;
        }
        return trimmed.startsWith("{") ? pickFromEntry(new JSONObject(trimmed)) : null;
    }

    // Prefers the timed lyrics, so that the screen can follow the playback,
    // and falls back to the plain ones, which still show up line by line.
    private static String pickFromEntry(JSONObject entry) {
        if (entry == null) {
            return null;
        }
        String synced = entry.isNull("syncedLyrics") ? "" : entry.optString("syncedLyrics", "");
        if (!synced.trim().isEmpty()) {
            return synced;
        }
        String plain = entry.isNull("plainLyrics") ? "" : entry.optString("plainLyrics", "");
        return plain.trim().isEmpty() ? null : plain;
    }

    private static String fetch(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setRequestProperty("User-Agent", AGENT);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                return readAll(in);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = in.read(buffer)) > 0) {
            total += count;
            if (total > MAX_BYTES) {
                return null;
            }
            out.write(buffer, 0, count);
        }
        return out.toString("UTF-8");
    }
}

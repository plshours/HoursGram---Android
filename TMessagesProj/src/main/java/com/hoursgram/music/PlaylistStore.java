/*
 * This is the source code of HoursGram for Android.
 *
 * HoursGram is free software under the GNU General Public License version 2,
 * as are the projects it is built on: AyuGram4A, exteraGram, Telegram for
 * Android. See NOTICE.md in the root of the repository.
 */
package com.hoursgram.music;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The playlists of the user, kept on the device. A song is stored together
 * with the message it came from, so that a playlist can be played without
 * opening the chat first.
 */
public final class PlaylistStore {

    public static final class Track {
        public long documentId;
        public long dialogId;
        public String title = "";
        public String artist = "";
        public int duration;
        /** The message of the song, serialized and encoded in base 64. */
        public String message = "";
    }

    public static final class Playlist {
        public String id;
        public String name;
        public final ArrayList<Track> tracks = new ArrayList<>();
    }

    private static final String PREFERENCES = "hoursgram_playlists";
    private static final String KEY = "data";
    // Telegram sorts a playlist by message id. When the order the user chose
    // does not agree with that, the messages are given ids that do.
    private static final int FAKE_ID_BASE = 0x40000000;

    private static ArrayList<Playlist> cache;

    private PlaylistStore() {
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static synchronized ArrayList<Playlist> items() {
        if (cache != null) {
            return cache;
        }
        cache = new ArrayList<>();
        try {
            String stored = preferences().getString(KEY, null);
            if (stored != null) {
                JSONArray array = new JSONArray(stored);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.getJSONObject(i);
                    Playlist playlist = new Playlist();
                    playlist.id = object.getString("id");
                    playlist.name = object.optString("name", "");
                    JSONArray tracks = object.optJSONArray("tracks");
                    for (int j = 0; tracks != null && j < tracks.length(); j++) {
                        JSONObject item = tracks.getJSONObject(j);
                        Track track = new Track();
                        track.documentId = item.optLong("doc");
                        track.dialogId = item.optLong("dialog");
                        track.title = item.optString("title", "");
                        track.artist = item.optString("artist", "");
                        track.duration = item.optInt("duration");
                        track.message = item.optString("message", "");
                        playlist.tracks.add(track);
                    }
                    cache.add(playlist);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return cache;
    }

    private static synchronized void save() {
        try {
            JSONArray array = new JSONArray();
            for (Playlist playlist : items()) {
                JSONObject object = new JSONObject();
                object.put("id", playlist.id);
                object.put("name", playlist.name);
                JSONArray tracks = new JSONArray();
                for (Track track : playlist.tracks) {
                    JSONObject item = new JSONObject();
                    item.put("doc", track.documentId);
                    item.put("dialog", track.dialogId);
                    item.put("title", track.title);
                    item.put("artist", track.artist);
                    item.put("duration", track.duration);
                    item.put("message", track.message);
                    tracks.put(item);
                }
                object.put("tracks", tracks);
                array.put(object);
            }
            preferences().edit().putString(KEY, array.toString()).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static synchronized List<Playlist> all() {
        return new ArrayList<>(items());
    }

    public static synchronized Playlist find(String id) {
        for (Playlist playlist : items()) {
            if (playlist.id.equals(id)) {
                return playlist;
            }
        }
        return null;
    }

    public static synchronized Playlist create(String name) {
        Playlist playlist = new Playlist();
        playlist.id = UUID.randomUUID().toString();
        playlist.name = name.trim();
        items().add(playlist);
        save();
        return playlist;
    }

    public static synchronized void rename(String id, String name) {
        Playlist playlist = find(id);
        if (playlist != null && !name.trim().isEmpty()) {
            playlist.name = name.trim();
            save();
        }
    }

    public static synchronized void delete(String id) {
        Playlist playlist = find(id);
        if (playlist != null) {
            items().remove(playlist);
            save();
        }
    }

    /** Returns false when the song is already in the playlist or cannot be stored. */
    public static synchronized boolean addTrack(String playlistId, MessageObject object) {
        Playlist playlist = find(playlistId);
        if (playlist == null || object == null || object.getDocument() == null || object.messageOwner == null) {
            return false;
        }
        long documentId = object.getDocument().id;
        for (Track track : playlist.tracks) {
            if (track.documentId == documentId) {
                return false;
            }
        }
        try {
            TLRPC.Message message = object.messageOwner;
            SerializedData data = new SerializedData(message.getObjectSize());
            message.serializeToStream(data);
            Track track = new Track();
            track.documentId = documentId;
            track.dialogId = object.getDialogId();
            track.title = object.getMusicTitle();
            track.artist = object.getMusicAuthor();
            track.duration = object.getDuration();
            track.message = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
            playlist.tracks.add(track);
            save();
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static synchronized void removeTrack(String playlistId, long documentId) {
        Playlist playlist = find(playlistId);
        if (playlist == null) {
            return;
        }
        for (int i = 0; i < playlist.tracks.size(); i++) {
            if (playlist.tracks.get(i).documentId == documentId) {
                playlist.tracks.remove(i);
                save();
                return;
            }
        }
    }

    /** The songs of the playlist as messages the player can work with, in the order of the playlist. */
    public static ArrayList<MessageObject> toMessages(int account, Playlist playlist) {
        ArrayList<MessageObject> result = new ArrayList<>();
        for (Track track : playlist.tracks) {
            try {
                SerializedData data = new SerializedData(Base64.decode(track.message, Base64.NO_WRAP));
                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                if (message == null) {
                    continue;
                }
                message.dialog_id = track.dialogId;
                MessageObject object = new MessageObject(account, message, false, false);
                if (object.isMusic()) {
                    result.add(object);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        boolean ascending = true;
        for (int i = 1; i < result.size(); i++) {
            if (result.get(i).getId() <= result.get(i - 1).getId()) {
                ascending = false;
                break;
            }
        }
        if (!ascending) {
            for (int i = 0; i < result.size(); i++) {
                result.get(i).messageOwner.id = FAKE_ID_BASE + i;
            }
        }
        return result;
    }
}

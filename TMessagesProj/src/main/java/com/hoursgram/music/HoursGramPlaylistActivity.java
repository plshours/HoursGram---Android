/*
 * This is the source code of HoursGram for Android.
 *
 * HoursGram is free software under the GNU General Public License version 2,
 * as are the projects it is built on: AyuGram4A, exteraGram, Telegram for
 * Android. See NOTICE.md in the root of the repository.
 */
package com.hoursgram.music;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Locale;

/** The songs of one playlist. */
public class HoursGramPlaylistActivity extends BaseFragment {

    private static final int ID_RENAME = 1;
    private static final int ID_DELETE = 2;

    private final String playlistId;
    private LinearLayout list;

    public HoursGramPlaylistActivity(String playlistId) {
        super();
        this.playlistId = playlistId;
    }

    @Override
    public View createView(Context context) {
        PlaylistStore.Playlist playlist = PlaylistStore.find(playlistId);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(playlist == null ? "" : playlist.name);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_RENAME) {
                    rename();
                } else if (id == ID_DELETE) {
                    PlaylistsUi.confirm(HoursGramPlaylistActivity.this,
                            LocaleController.getString("HoursGramDeletePlaylistQuestion", R.string.HoursGramDeletePlaylistQuestion),
                            LocaleController.getString("HoursGramDelete", R.string.HoursGramDelete),
                            () -> {
                                PlaylistStore.delete(playlistId);
                                finishFragment();
                            });
                }
            }
        });
        actionBar.createMenu().addItem(ID_RENAME, R.drawable.msg_edit);
        actionBar.createMenu().addItem(ID_DELETE, R.drawable.msg_delete);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(24));
        scroll.addView(list, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        render();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (list == null) {
            return;
        }
        final PlaylistStore.Playlist playlist = PlaylistStore.find(playlistId);
        list.removeAllViews();
        if (playlist == null) {
            return;
        }
        actionBar.setTitle(playlist.name);

        Context context = list.getContext();
        int card = Theme.getColor(Theme.key_windowBackgroundWhite);
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
        int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int subtitle = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

        if (playlist.tracks.isEmpty()) {
            TextView empty = HoursGramMusicActivity.label(context, 15, subtitle, false);
            empty.setGravity(Gravity.CENTER);
            empty.setText(LocaleController.getString("HoursGramPlaylistEmpty", R.string.HoursGramPlaylistEmpty));
            list.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 48, 0, 0));
            return;
        }

        TextView playAll = HoursGramMusicActivity.pill(context, LocaleController.getString("HoursGramPlayAll", R.string.HoursGramPlayAll), accent, card);
        playAll.setOnClickListener(v -> play(playlist, 0));
        list.addView(playAll, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        long playingId = playing != null && playing.getDocument() != null ? playing.getDocument().id : 0;

        for (int i = 0; i < playlist.tracks.size(); i++) {
            final int index = i;
            final PlaylistStore.Track track = playlist.tracks.get(i);
            boolean isPlaying = playingId != 0 && playingId == track.documentId;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(card);
            background.setCornerRadius(AndroidUtilities.dp(20));
            row.setBackground(background);

            TextView number = HoursGramMusicActivity.label(context, 14, isPlaying ? accent : subtitle, true);
            number.setGravity(Gravity.CENTER);
            number.setText(isPlaying ? "▶" : String.valueOf(index + 1));
            row.addView(number, LayoutHelper.createLinear(28, LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView title = HoursGramMusicActivity.label(context, 15, isPlaying ? accent : text, true);
            title.setSingleLine(true);
            title.setText(track.title);
            TextView artist = HoursGramMusicActivity.label(context, 13, subtitle, false);
            artist.setSingleLine(true);
            artist.setText(track.artist);
            texts.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            texts.addView(artist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView length = HoursGramMusicActivity.label(context, 12, subtitle, false);
            length.setText(String.format(Locale.US, "%d:%02d", track.duration / 60, track.duration % 60));
            row.addView(length, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 10, 0, 0, 0));

            row.setOnClickListener(v -> play(playlist, index));
            row.setOnLongClickListener(v -> {
                PlaylistsUi.confirm(this,
                        LocaleController.getString("HoursGramRemoveFromPlaylist", R.string.HoursGramRemoveFromPlaylist) + ": " + track.title + "?",
                        LocaleController.getString("HoursGramDelete", R.string.HoursGramDelete),
                        () -> {
                            PlaylistStore.removeTrack(playlistId, track.documentId);
                            render();
                        });
                return true;
            });
            list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        }
    }

    private void rename() {
        final PlaylistStore.Playlist playlist = PlaylistStore.find(playlistId);
        if (playlist == null) {
            return;
        }
        PlaylistsUi.askName(this,
                LocaleController.getString("HoursGramRename", R.string.HoursGramRename), playlist.name,
                LocaleController.getString("HoursGramRename", R.string.HoursGramRename),
                name -> {
                    PlaylistStore.rename(playlistId, name);
                    render();
                });
    }

    private void play(PlaylistStore.Playlist playlist, int index) {
        if (getParentActivity() == null || index < 0 || index >= playlist.tracks.size()) {
            return;
        }
        ArrayList<MessageObject> messages = PlaylistStore.toMessages(currentAccount, playlist);
        MessageObject chosen = null;
        long wanted = playlist.tracks.get(index).documentId;
        for (MessageObject message : messages) {
            if (message.getDocument() != null && message.getDocument().id == wanted) {
                chosen = message;
                break;
            }
        }
        if (chosen == null && !messages.isEmpty()) {
            chosen = messages.get(0);
        }
        // The playlist is not extended with other music of the chat the first
        // song came from, and it starts over when it ends.
        if (chosen != null && MediaController.getInstance().setPlaylist(messages, chosen, 0, false, null)) {
            presentFragment(new HoursGramMusicActivity());
        } else {
            Toast.makeText(getParentActivity(), LocaleController.getString("HoursGramCannotPlay", R.string.HoursGramCannotPlay), Toast.LENGTH_SHORT).show();
        }
    }
}

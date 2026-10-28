/*
 * This is the source code of HoursGram for Android.
 *
 * HoursGram is free software under the GNU General Public License version 2,
 * as are the projects it is built on: AyuGram4A, exteraGram, Telegram for
 * Android. See NOTICE.md in the root of the repository.
 */
package com.hoursgram.music;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

/** All the playlists, each one a rounded card. A tap opens it, a long press renames or deletes it. */
public class HoursGramPlaylistsActivity extends BaseFragment {

    private static final int ID_CREATE = 1;

    private LinearLayout list;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("HoursGramPlaylists", R.string.HoursGramPlaylists));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_CREATE) {
                    create();
                }
            }
        });
        actionBar.createMenu().addItem(ID_CREATE, R.drawable.msg_add);

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
        Context context = list.getContext();
        int card = Theme.getColor(Theme.key_windowBackgroundWhite);
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
        int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int subtitle = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        list.removeAllViews();

        final List<PlaylistStore.Playlist> playlists = PlaylistStore.all();
        if (playlists.isEmpty()) {
            TextView empty = HoursGramMusicActivity.label(context, 15, subtitle, false);
            empty.setGravity(Gravity.CENTER);
            empty.setText(LocaleController.getString("HoursGramPlaylistsEmpty", R.string.HoursGramPlaylistsEmpty));
            list.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 48, 0, 0));
            return;
        }
        for (final PlaylistStore.Playlist playlist : playlists) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(card);
            background.setCornerRadius(AndroidUtilities.dp(22));
            row.setBackground(background);

            TextView tile = HoursGramMusicActivity.label(context, 22, Color.WHITE, false);
            tile.setText("♪");
            tile.setGravity(Gravity.CENTER);
            GradientDrawable tileBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{accent, Theme.getColor(Theme.key_featuredStickers_addButton)});
            tileBackground.setCornerRadius(AndroidUtilities.dp(14));
            tile.setBackground(tileBackground);
            row.addView(tile, LayoutHelper.createLinear(48, 48, 0, 0, 14, 0));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = HoursGramMusicActivity.label(context, 16, text, true);
            name.setSingleLine(true);
            name.setText(playlist.name);
            TextView count = HoursGramMusicActivity.label(context, 13, subtitle, false);
            count.setText(String.format(LocaleController.getString("HoursGramSongsCount", R.string.HoursGramSongsCount), playlist.tracks.size()));
            texts.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            texts.addView(count, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView chevron = HoursGramMusicActivity.label(context, 22, subtitle, false);
            chevron.setText("›");
            row.addView(chevron, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            row.setOnClickListener(v -> presentFragment(new HoursGramPlaylistActivity(playlist.id)));
            row.setOnLongClickListener(v -> {
                options(playlist);
                return true;
            });
            list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        }
    }

    private void create() {
        PlaylistsUi.askName(this,
                LocaleController.getString("HoursGramNewPlaylist", R.string.HoursGramNewPlaylist), "",
                LocaleController.getString("HoursGramCreate", R.string.HoursGramCreate),
                name -> {
                    PlaylistStore.create(name);
                    render();
                });
    }

    private void options(final PlaylistStore.Playlist playlist) {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] items = new CharSequence[]{
                LocaleController.getString("HoursGramRename", R.string.HoursGramRename),
                LocaleController.getString("HoursGramDelete", R.string.HoursGramDelete)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(playlist.name);
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                PlaylistsUi.askName(this,
                        LocaleController.getString("HoursGramRename", R.string.HoursGramRename), playlist.name,
                        LocaleController.getString("HoursGramRename", R.string.HoursGramRename),
                        name -> {
                            PlaylistStore.rename(playlist.id, name);
                            render();
                        });
            } else {
                PlaylistsUi.confirm(this,
                        LocaleController.getString("HoursGramDeletePlaylistQuestion", R.string.HoursGramDeletePlaylistQuestion),
                        LocaleController.getString("HoursGramDelete", R.string.HoursGramDelete),
                        () -> {
                            PlaylistStore.delete(playlist.id);
                            render();
                        });
            }
        });
        showDialog(builder.create());
    }
}

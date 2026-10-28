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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player of HoursGram: what is playing, the lyrics following it line by
 * line, and the way to the playlists. The sound itself is played by the
 * ordinary media controller of the app, this screen only shows and drives it.
 */
public class HoursGramMusicActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int SEEK_STEPS = 1000;
    private static final long FOLLOW_PAUSE_MS = 4000;

    private ScrollView scrollView;
    private LinearLayout content;
    private LinearLayout playerBlock;
    private LinearLayout emptyBlock;
    private LinearLayout lyricsLayout;
    private FrameLayout dock;
    private TextView titleView;
    private TextView artistView;
    private TextView elapsedView;
    private TextView durationView;
    private SeekBar seekBar;
    private ImageView playButton;
    private PlayPauseDrawable playPauseDrawable;

    private MessageObject current;
    private Lyrics lyrics;
    private final ArrayList<TextView> lyricViews = new ArrayList<>();
    private int activeLine = -1;
    private long lastTouch;
    private boolean seeking;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter center = getNotificationCenter();
        center.addObserver(this, NotificationCenter.messagePlayingDidStart);
        center.addObserver(this, NotificationCenter.messagePlayingDidReset);
        center.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        center.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter center = getNotificationCenter();
        center.removeObserver(this, NotificationCenter.messagePlayingDidStart);
        center.removeObserver(this, NotificationCenter.messagePlayingDidReset);
        center.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        center.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("HoursGramMusic", R.string.HoursGramMusic));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        int background = Theme.getColor(Theme.key_windowBackgroundGray);
        int card = Theme.getColor(Theme.key_windowBackgroundWhite);
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
        int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int subtitle = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(background);

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setOnTouchListener((v, event) -> {
            lastTouch = System.currentTimeMillis();
            return false;
        });
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(190));
        scrollView.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Shown while a song is playing.
        playerBlock = new LinearLayout(context);
        playerBlock.setOrientation(LinearLayout.VERTICAL);
        playerBlock.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout cover = new FrameLayout(context);
        GradientDrawable coverBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{accent, mix(accent, card, 0.55f)});
        coverBackground.setCornerRadius(AndroidUtilities.dp(28));
        cover.setBackground(coverBackground);
        TextView note = label(context, 56, Color.WHITE, false);
        note.setText("♪");
        note.setGravity(Gravity.CENTER);
        cover.addView(note, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        playerBlock.addView(cover, LayoutHelper.createLinear(150, 150, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 16));

        titleView = label(context, 20, text, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(2);
        playerBlock.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        artistView = label(context, 15, subtitle, false);
        artistView.setGravity(Gravity.CENTER);
        artistView.setMaxLines(1);
        playerBlock.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        TextView addButton = pill(context, LocaleController.getString("HoursGramAddToPlaylist", R.string.HoursGramAddToPlaylist), accent, card);
        addButton.setOnClickListener(v -> addToPlaylist());
        TextView listsButton = pill(context, LocaleController.getString("HoursGramPlaylists", R.string.HoursGramPlaylists), accent, card);
        listsButton.setOnClickListener(v -> presentFragment(new HoursGramPlaylistsActivity()));
        actions.addView(addButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0, 0, 8, 0));
        actions.addView(listsButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));
        playerBlock.addView(actions, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 24));

        lyricsLayout = new LinearLayout(context);
        lyricsLayout.setOrientation(LinearLayout.VERTICAL);
        lyricsLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        playerBlock.addView(lyricsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(playerBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Shown when nothing plays.
        emptyBlock = new LinearLayout(context);
        emptyBlock.setOrientation(LinearLayout.VERTICAL);
        emptyBlock.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView emptyNote = label(context, 64, accent, false);
        emptyNote.setText("♪");
        emptyNote.setGravity(Gravity.CENTER);
        emptyBlock.addView(emptyNote, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 48, 0, 12));
        TextView emptyTitle = label(context, 18, text, true);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setText(LocaleController.getString("HoursGramNothingPlaying", R.string.HoursGramNothingPlaying));
        emptyBlock.addView(emptyTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        TextView emptyHint = label(context, 14, subtitle, false);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setText(LocaleController.getString("HoursGramNothingPlayingHint", R.string.HoursGramNothingPlayingHint));
        emptyBlock.addView(emptyHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));
        TextView emptyLists = pill(context, LocaleController.getString("HoursGramPlaylists", R.string.HoursGramPlaylists), accent, card);
        emptyLists.setOnClickListener(v -> presentFragment(new HoursGramPlaylistsActivity()));
        emptyBlock.addView(emptyLists, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_HORIZONTAL));
        content.addView(emptyBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // The bar at the bottom, always in reach of the thumb.
        dock = new FrameLayout(context);
        float corner = AndroidUtilities.dp(26);
        GradientDrawable dockBackground = new GradientDrawable();
        dockBackground.setColor(card);
        dockBackground.setCornerRadii(new float[]{corner, corner, corner, corner, 0, 0, 0, 0});
        dock.setBackground(dockBackground);
        dock.setElevation(AndroidUtilities.dp(8));

        seekBar = new SeekBar(context);
        seekBar.setMax(SEEK_STEPS);
        seekBar.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        seekBar.getProgressDrawable().setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        if (seekBar.getThumb() != null) {
            seekBar.getThumb().setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        }
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    elapsedView.setText(format(durationMs() * progress / SEEK_STEPS));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                seeking = false;
                if (current != null) {
                    MediaController.getInstance().seekToProgress(current, bar.getProgress() / (float) SEEK_STEPS);
                }
            }
        });
        dock.addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP, 16, 14, 16, 0));

        elapsedView = label(context, 12, subtitle, false);
        elapsedView.setText(format(0));
        dock.addView(elapsedView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 28, 50, 0, 0));
        durationView = label(context, 12, subtitle, false);
        durationView.setText(format(0));
        dock.addView(durationView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 50, 28, 0));

        int iconColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        RLottieImageView previous = new RLottieImageView(context);
        previous.setScaleType(ImageView.ScaleType.CENTER);
        previous.setAnimation(R.raw.player_prev, 20, 20);
        previous.setLayerColor("Triangle 3.**", iconColor);
        previous.setLayerColor("Triangle 4.**", iconColor);
        previous.setLayerColor("Rectangle 4.**", iconColor);
        previous.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(24)));
        previous.setContentDescription(LocaleController.getString("AccDescrPrevious", R.string.AccDescrPrevious));
        previous.setOnClickListener(v -> {
            previous.setProgress(0f);
            previous.playAnimation();
            MediaController.getInstance().playPreviousMessage();
        });

        RLottieImageView next = new RLottieImageView(context);
        next.setScaleType(ImageView.ScaleType.CENTER);
        next.setAnimation(R.raw.player_prev, 20, 20);
        next.setLayerColor("Triangle 3.**", iconColor);
        next.setLayerColor("Triangle 4.**", iconColor);
        next.setLayerColor("Rectangle 4.**", iconColor);
        next.setRotation(180f);
        next.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(24)));
        next.setContentDescription(LocaleController.getString("Next", R.string.Next));
        next.setOnClickListener(v -> {
            next.setProgress(0f);
            next.playAnimation();
            MediaController.getInstance().playNextMessage();
        });

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playPauseDrawable = new PlayPauseDrawable(28);
        playPauseDrawable.setParent(playButton);
        playButton.setImageDrawable(playPauseDrawable);
        playButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        GradientDrawable playBackground = new GradientDrawable();
        playBackground.setShape(GradientDrawable.OVAL);
        playBackground.setColor(accent);
        playButton.setBackground(playBackground);
        playButton.setOnClickListener(v -> togglePlayback());

        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(previous, LayoutHelper.createLinear(48, 48, 0, 0, 20, 0));
        controls.addView(playButton, LayoutHelper.createLinear(64, 64));
        controls.addView(next, LayoutHelper.createLinear(48, 48, 20, 0, 0, 0));
        dock.addView(controls, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 20));

        root.addView(dock, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 170, Gravity.BOTTOM));

        fragmentView = root;
        bind(playingTrack());
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        bind(playingTrack());
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart) {
            if (args.length > 0 && args[0] instanceof MessageObject && ((MessageObject) args[0]).isMusic()) {
                bind((MessageObject) args[0]);
            }
        } else if (id == NotificationCenter.messagePlayingDidReset) {
            bind(playingTrack());
        } else if (id == NotificationCenter.messagePlayingPlayStateChanged) {
            updatePlayState(true);
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            if (current != null && args.length > 1 && args[0] instanceof Integer
                    && current.getId() == (Integer) args[0] && args[1] instanceof Number) {
                onProgress(((Number) args[1]).floatValue());
            }
        }
    }

    private MessageObject playingTrack() {
        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        return playing != null && playing.isMusic() ? playing : null;
    }

    private void bind(MessageObject track) {
        if (playerBlock == null) {
            return;
        }
        boolean same = track != null && current != null && sameSong(track, current);
        current = track;
        boolean playing = track != null;
        playerBlock.setVisibility(playing ? View.VISIBLE : View.GONE);
        emptyBlock.setVisibility(playing ? View.GONE : View.VISIBLE);
        dock.setVisibility(playing ? View.VISIBLE : View.GONE);
        if (!playing) {
            lyrics = null;
            activeLine = -1;
            return;
        }
        titleView.setText(track.getMusicTitle());
        artistView.setText(track.getMusicAuthor());
        durationView.setText(format(durationMs()));
        updatePlayState(false);
        if (!same) {
            seekBar.setProgress(0);
            elapsedView.setText(format(0));
            loadLyrics(track);
        }
    }

    private static boolean sameSong(MessageObject a, MessageObject b) {
        return a.getDocument() != null && b.getDocument() != null && a.getDocument().id == b.getDocument().id;
    }

    private void loadLyrics(MessageObject track) {
        lyrics = null;
        activeLine = -1;
        lyricViews.clear();
        lyricsLayout.removeAllViews();
        showLyricsNote(LocaleController.getString("HoursGramLyricsLoading", R.string.HoursGramLyricsLoading));
        if (track.getDocument() == null) {
            return;
        }
        final long documentId = track.getDocument().id;
        LyricsRepository.load(documentId, track.getMusicTitle(), track.getMusicAuthor(), track.getDuration(), found -> {
            if (current == null || current.getDocument() == null || current.getDocument().id != documentId || lyricsLayout == null) {
                return;
            }
            lyrics = found;
            showLyrics();
        });
    }

    private void showLyricsNote(String message) {
        lyricsLayout.removeAllViews();
        lyricViews.clear();
        TextView note = label(lyricsLayout.getContext(), 14, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
        note.setGravity(Gravity.CENTER);
        note.setText(message);
        lyricsLayout.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));
    }

    private void showLyrics() {
        if (getParentActivity() == null) {
            return;
        }
        if (lyrics == null) {
            showLyricsNote(LocaleController.getString("HoursGramLyricsNone", R.string.HoursGramLyricsNone));
            return;
        }
        lyricsLayout.removeAllViews();
        lyricViews.clear();
        for (int i = 0; i < lyrics.lines.size(); i++) {
            final Lyrics.Line line = lyrics.lines.get(i);
            TextView view = label(lyricsLayout.getContext(), 18, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), false);
            view.setGravity(Gravity.CENTER);
            view.setText(line.text);
            view.setAlpha(lyrics.synced ? 0.55f : 1f);
            if (lyrics.synced) {
                view.setOnClickListener(v -> {
                    long total = durationMs();
                    if (current != null && total > 0) {
                        MediaController.getInstance().seekToProgress(current, Math.min(1f, line.timeMs / (float) total));
                    }
                });
            }
            lyricViews.add(view);
            lyricsLayout.addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 7, 0, 7));
        }
        activeLine = -1;
    }

    private void onProgress(float progress) {
        long total = durationMs();
        long position = (long) (progress * total);
        if (!seeking) {
            seekBar.setProgress((int) (progress * SEEK_STEPS));
            elapsedView.setText(format(position));
        }
        if (lyrics != null && lyrics.synced) {
            int index = lyrics.indexAt(position);
            if (index != activeLine) {
                highlight(index);
            }
        }
    }

    private void highlight(int index) {
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
        int dim = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        if (activeLine >= 0 && activeLine < lyricViews.size()) {
            TextView old = lyricViews.get(activeLine);
            old.setTextColor(dim);
            old.setAlpha(0.55f);
            old.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        }
        activeLine = index;
        if (index >= 0 && index < lyricViews.size()) {
            TextView view = lyricViews.get(index);
            view.setTextColor(accent);
            view.setAlpha(1f);
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21);
            followLine(view);
        }
    }

    // The screen follows the song, unless the user has just scrolled it.
    private void followLine(TextView view) {
        if (System.currentTimeMillis() - lastTouch < FOLLOW_PAUSE_MS) {
            return;
        }
        int target = playerBlock.getTop() + lyricsLayout.getTop() + view.getTop() - scrollView.getHeight() / 3;
        scrollView.smoothScrollTo(0, Math.max(0, target));
    }

    private void togglePlayback() {
        if (current == null) {
            return;
        }
        if (MediaController.getInstance().isMessagePaused()) {
            MediaController.getInstance().playMessage(current);
        } else {
            MediaController.getInstance().pauseMessage(current);
        }
    }

    private void updatePlayState(boolean animated) {
        if (playPauseDrawable != null) {
            playPauseDrawable.setPause(!MediaController.getInstance().isMessagePaused(), animated);
        }
    }

    private void addToPlaylist() {
        if (current == null || getParentActivity() == null) {
            return;
        }
        final List<PlaylistStore.Playlist> lists = PlaylistStore.all();
        final String[] names = new String[lists.size() + 1];
        for (int i = 0; i < lists.size(); i++) {
            names[i] = lists.get(i).name;
        }
        names[lists.size()] = "+ " + LocaleController.getString("HoursGramNewPlaylist", R.string.HoursGramNewPlaylist);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("HoursGramAddToPlaylist", R.string.HoursGramAddToPlaylist));
        builder.setItems(names, (dialog, which) -> {
            if (which < lists.size()) {
                addTo(lists.get(which));
            } else {
                PlaylistsUi.askName(this,
                        LocaleController.getString("HoursGramNewPlaylist", R.string.HoursGramNewPlaylist), "",
                        LocaleController.getString("HoursGramCreate", R.string.HoursGramCreate),
                        name -> addTo(PlaylistStore.create(name)));
            }
        });
        showDialog(builder.create());
    }

    private void addTo(PlaylistStore.Playlist playlist) {
        if (current == null || getParentActivity() == null) {
            return;
        }
        boolean added = PlaylistStore.addTrack(playlist.id, current);
        String message = added
                ? String.format(LocaleController.getString("HoursGramAddedToPlaylist", R.string.HoursGramAddedToPlaylist), playlist.name)
                : String.format(LocaleController.getString("HoursGramAlreadyInPlaylist", R.string.HoursGramAlreadyInPlaylist), playlist.name);
        Toast.makeText(getParentActivity(), message, Toast.LENGTH_SHORT).show();
    }

    private long durationMs() {
        return current == null ? 0 : current.getDuration() * 1000L;
    }

    private static String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static int mix(int from, int to, float amount) {
        int r = (int) (Color.red(from) * (1 - amount) + Color.red(to) * amount);
        int g = (int) (Color.green(from) * (1 - amount) + Color.green(to) * amount);
        int b = (int) (Color.blue(from) * (1 - amount) + Color.blue(to) * amount);
        return Color.rgb(r, g, b);
    }

    static TextView label(Context context, int sizeDp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        view.setTextColor(color);
        view.setTypeface(AndroidUtilities.getTypeface(bold ? AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM : AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
        return view;
    }

    static TextView pill(Context context, String text, int accent, int card) {
        TextView view = label(context, 14, accent, true);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(card);
        background.setStroke(AndroidUtilities.dp(1), accent);
        background.setCornerRadius(AndroidUtilities.dp(20));
        view.setBackground(background);
        return view;
    }
}

/*
 * This is the source code of HoursGram for Android.
 *
 * HoursGram is free software under the GNU General Public License version 2,
 * as are the projects it is built on: AyuGram4A, exteraGram, Telegram for
 * Android. See NOTICE.md in the root of the repository.
 */
package com.hoursgram.music;

import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** The two small dialogs the playlist screens share. */
final class PlaylistsUi {

    interface NameCallback {
        void onName(String name);
    }

    interface Action {
        void run();
    }

    private PlaylistsUi() {
    }

    static void askName(BaseFragment fragment, String title, String initial, String positive, NameCallback callback) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setText(initial);
        input.setSelection(input.getText().length());
        input.setHint(LocaleController.getString("HoursGramPlaylistName", R.string.HoursGramPlaylistName));
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        FrameLayout holder = new FrameLayout(context);
        holder.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        holder.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setView(holder);
        builder.setPositiveButton(positive, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                callback.onName(name);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    static void confirm(BaseFragment fragment, String message, String positive, Action action) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(message);
        builder.setPositiveButton(positive, (dialog, which) -> action.run());
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }
}

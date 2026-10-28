/*
 * Reconstructed implementation of the AyuGram "proprietary" history hook. The
 * upstream AyuGram4A ships this in a private submodule and asks forks to
 * provide their own; this is a clean-room version built only from the public
 * call sites in ChatActivity and the DeletedMessage DAO in this repository.
 *
 * It injects saved deleted messages that fall inside the id range currently
 * being loaded back into the chat so they remain visible. Everything is guarded
 * so that any failure degrades to doing nothing rather than breaking chat load.
 */

package com.radolyn.ayugram.proprietary;

import android.util.Pair;
import android.util.SparseArray;

import com.radolyn.ayugram.database.AyuData;
import com.radolyn.ayugram.database.entities.DeletedMessage;
import com.radolyn.ayugram.database.entities.DeletedMessageFull;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.List;

public class AyuHistoryHook {

    // Returns the smallest and biggest message id present in the loaded batch.
    // first = smaller, second = bigger (see ChatActivity usage).
    public static Pair<Integer, Integer> getMinAndMaxIds(ArrayList<MessageObject> messArr) {
        if (messArr == null || messArr.isEmpty()) {
            return new Pair<>(0, 0);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (MessageObject mo : messArr) {
            int id = mo.getId();
            if (id < min) {
                min = id;
            }
            if (id > max) {
                max = id;
            }
        }
        return new Pair<>(min, max);
    }

    public static void doHook(int currentAccount,
                              ArrayList<MessageObject> messArr,
                              SparseArray<MessageObject>[] messagesDict,
                              int startId,
                              int endId,
                              long dialogId,
                              int limit,
                              long topicId,
                              boolean isSecretChat) {
        try {
            if (isSecretChat || messagesDict == null || messagesDict.length == 0) {
                return;
            }

            long userId = UserConfig.getInstance(currentAccount).getClientUserId();
            long topic = Math.max(topicId, 0);

            List<DeletedMessageFull> saved = AyuData.getDeletedMessageDao()
                    .getMessages(userId, dialogId, topic, Math.min(startId, endId), Math.max(startId, endId), limit);
            if (saved == null || saved.isEmpty()) {
                return;
            }

            for (DeletedMessageFull full : saved) {
                try {
                    DeletedMessage dm = full.message;
                    if (dm == null) {
                        continue;
                    }
                    if (messagesDict[0].get(dm.messageId) != null) {
                        continue;
                    }
                    MessageObject mo = AyuMessageUtils.createMessageObject(currentAccount, dm);
                    if (mo == null) {
                        continue;
                    }
                    messArr.add(mo);
                    messagesDict[0].put(dm.messageId, mo);
                } catch (Exception inner) {
                    FileLog.e(inner);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}

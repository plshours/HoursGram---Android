/*
 * Open replacement for the closed AyuGram4A_proprietary module, used when that
 * submodule is not available. Deleted messages are not injected into chat history.
 */
package com.radolyn.ayugram.proprietary;

import android.util.Pair;
import android.util.SparseArray;

import org.telegram.messenger.MessageObject;

import java.util.List;

public class AyuHistoryHook {

    public static Pair<Integer, Integer> getMinAndMaxIds(List<MessageObject> messages) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (MessageObject message : messages) {
            int id = message.getId();
            min = Math.min(min, id);
            max = Math.max(max, id);
        }
        if (messages.isEmpty()) {
            min = 0;
            max = 0;
        }
        return new Pair<>(min, max);
    }

    public static void doHook(int currentAccount, List<MessageObject> messages, SparseArray<MessageObject>[] messagesDict,
                              int startId, int endId, long dialogId, int limit, int topicId, boolean isSecretChat) {
        // no-op: requires the proprietary module
    }
}

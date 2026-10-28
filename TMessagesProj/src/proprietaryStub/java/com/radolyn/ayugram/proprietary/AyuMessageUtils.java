/*
 * Open replacement for the closed AyuGram4A_proprietary module, used when that
 * submodule is not available. Only basic message fields and plain text are
 * mapped; media is not saved.
 */
package com.radolyn.ayugram.proprietary;

import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.entities.AyuMessageBase;
import com.radolyn.ayugram.database.entities.EditedMessage;
import com.radolyn.ayugram.messages.AyuSavePreferences;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

public class AyuMessageUtils {

    public static void map(AyuSavePreferences prefs, AyuMessageBase output) {
        TLRPC.Message msg = prefs.getMessage();

        output.userId = prefs.getUserId();
        output.dialogId = prefs.getDialogId();
        output.topicId = prefs.getTopicId();
        output.messageId = prefs.getMessageId();
        output.entityCreateDate = prefs.getRequestCatchTime();

        if (msg == null) {
            return;
        }

        output.groupedId = msg.grouped_id;
        output.peerId = MessageObject.getPeerId(msg.peer_id);
        output.fromId = MessageObject.getPeerId(msg.from_id);
        output.date = msg.date;
        output.flags = msg.flags;
        output.editDate = msg.edit_date;
        output.views = msg.views;
        output.text = msg.message;
    }

    public static void mapMedia(AyuSavePreferences prefs, AyuMessageBase output, boolean saveMedia) {
        output.documentType = AyuConstants.DOCUMENT_TYPE_NONE;
    }

    public static void map(EditedMessage input, TLRPC.Message output, int accountId) {
        MessagesController controller = MessagesController.getInstance(accountId);

        output.id = input.messageId;
        output.dialog_id = input.dialogId;
        output.grouped_id = input.groupedId;
        output.peer_id = controller.getPeer(input.peerId);
        output.from_id = controller.getPeer(input.fromId);
        output.date = input.date;
        output.flags = input.flags;
        output.edit_date = input.editDate;
        output.views = input.views;
        output.message = input.text;
    }

    public static void mapMedia(EditedMessage input, TLRPC.Message output) {
        output.media = new TLRPC.TL_messageMediaEmpty();
    }
}

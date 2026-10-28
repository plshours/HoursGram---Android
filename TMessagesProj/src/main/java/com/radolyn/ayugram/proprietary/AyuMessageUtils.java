/*
 * Reconstructed implementation of the AyuGram "proprietary" message mapping
 * helpers. The upstream AyuGram4A ships these in a private submodule and asks
 * forks to provide their own; this is a clean-room version built only from the
 * public entity definitions and call sites in this repository.
 *
 * It maps a Telegram message to the AyuGram database entities and back, so that
 * edited and deleted messages can be stored and later shown in the history UI.
 */

package com.radolyn.ayugram.proprietary;

import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.entities.AyuMessageBase;
import com.radolyn.ayugram.database.entities.DeletedMessage;
import com.radolyn.ayugram.database.entities.EditedMessage;
import com.radolyn.ayugram.messages.AyuMessagesController;
import com.radolyn.ayugram.messages.AyuSavePreferences;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class AyuMessageUtils {

    // =====================================================================
    // forward direction: Telegram message -> database entity
    // =====================================================================

    public static void map(AyuSavePreferences prefs, AyuMessageBase entity) {
        try {
            mapInner(prefs, entity);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void mapInner(AyuSavePreferences prefs, AyuMessageBase entity) {
        entity.userId = prefs.getUserId();
        entity.dialogId = prefs.getDialogId();
        entity.messageId = prefs.getMessageId();
        entity.topicId = Math.max(prefs.getTopicId(), 0);
        if (entity.entityCreateDate == 0) {
            entity.entityCreateDate = prefs.getRequestCatchTime();
        }

        TLRPC.Message msg = prefs.getMessage();
        if (msg == null) {
            return;
        }

        entity.groupedId = msg.grouped_id;
        entity.peerId = msg.peer_id != null ? peerId(msg.peer_id) : entity.dialogId;
        entity.fromId = msg.from_id != null ? peerId(msg.from_id) : 0;
        entity.date = msg.date;
        entity.flags = msg.flags;
        entity.editDate = msg.edit_date;
        entity.views = msg.views;

        if (msg.fwd_from != null) {
            entity.fwdFlags = msg.fwd_from.flags;
            entity.fwdFromId = msg.fwd_from.from_id != null ? peerId(msg.fwd_from.from_id) : 0;
            entity.fwdName = msg.fwd_from.from_name;
            entity.fwdDate = msg.fwd_from.date;
            entity.fwdPostAuthor = msg.fwd_from.post_author;
        }

        if (msg.reply_to != null) {
            entity.replyFlags = msg.reply_to.flags;
            entity.replyMessageId = msg.reply_to.reply_to_msg_id;
            entity.replyPeerId = msg.reply_to.reply_to_peer_id != null ? peerId(msg.reply_to.reply_to_peer_id) : 0;
            entity.replyTopId = msg.reply_to.reply_to_top_id;
            entity.replyForumTopic = msg.reply_to.forum_topic;
        }

        entity.text = msg.message != null ? msg.message : "";
        entity.textEntities = serializeVector(msg.entities);
    }

    public static void mapMedia(AyuSavePreferences prefs, AyuMessageBase entity, boolean copyMedia) {
        try {
            mapMediaInner(prefs, entity, copyMedia);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void mapMediaInner(AyuSavePreferences prefs, AyuMessageBase entity, boolean copyMedia) {
        entity.documentType = AyuConstants.DOCUMENT_TYPE_NONE;

        TLRPC.Message msg = prefs.getMessage();
        if (msg == null || msg.media == null) {
            return;
        }

        int account = prefs.getAccountId();

        if (msg.media instanceof TLRPC.TL_messageMediaPhoto && msg.media.photo != null) {
            entity.documentType = AyuConstants.DOCUMENT_TYPE_PHOTO;
            entity.thumbsSerialized = serialize(msg.media.photo);
            entity.mediaPath = resolveAndCopy(account, msg, copyMedia);
        } else if (msg.media instanceof TLRPC.TL_messageMediaDocument && msg.media.document != null) {
            TLRPC.Document doc = msg.media.document;

            boolean sticker = false;
            if (doc.attributes != null) {
                for (TLRPC.DocumentAttribute a : doc.attributes) {
                    if (a instanceof TLRPC.TL_documentAttributeSticker) {
                        sticker = true;
                        break;
                    }
                }
            }

            entity.documentType = sticker ? AyuConstants.DOCUMENT_TYPE_STICKER : AyuConstants.DOCUMENT_TYPE_FILE;
            entity.documentSerialized = serialize(doc);
            entity.mimeType = doc.mime_type;
            if (doc.attributes != null) {
                entity.documentAttributesSerialized = serializeVector(new ArrayList<>(doc.attributes));
            }
            entity.mediaPath = resolveAndCopy(account, msg, copyMedia);
        }
    }

    // =====================================================================
    // reverse direction: database entity -> Telegram message
    // =====================================================================

    public static void map(EditedMessage entity, TLRPC.Message msg, int currentAccount) {
        mapBack(entity, msg, currentAccount);
    }

    public static void mapMedia(EditedMessage entity, TLRPC.Message msg) {
        mapMediaBack(entity, msg);
    }

    private static void mapBack(AyuMessageBase entity, TLRPC.Message msg, int currentAccount) {
        try {
            msg.id = entity.messageId;
            msg.dialog_id = entity.dialogId;
            msg.date = entity.date != 0 ? entity.date : entity.entityCreateDate;
            msg.flags = entity.flags;
            msg.edit_date = entity.editDate;
            msg.views = entity.views;
            msg.grouped_id = entity.groupedId;

            long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
            msg.out = entity.fromId == selfId && entity.fromId != 0;

            msg.peer_id = peer(entity.peerId != 0 ? entity.peerId : entity.dialogId);
            if (entity.fromId != 0) {
                msg.from_id = peer(entity.fromId);
            }

            msg.message = entity.text != null ? entity.text : "";
            List<TLRPC.MessageEntity> ents = deserializeEntities(entity.textEntities);
            if (ents != null) {
                msg.entities = new ArrayList<>(ents);
            }

            if (entity.fwdFromId != 0 || entity.fwdName != null) {
                TLRPC.TL_messageFwdHeader fwd = new TLRPC.TL_messageFwdHeader();
                fwd.flags = entity.fwdFlags;
                if (entity.fwdFromId != 0) {
                    fwd.from_id = peer(entity.fwdFromId);
                }
                fwd.from_name = entity.fwdName;
                fwd.date = entity.fwdDate;
                fwd.post_author = entity.fwdPostAuthor;
                msg.fwd_from = fwd;
            }

            if (entity.replyMessageId != 0) {
                TLRPC.TL_messageReplyHeader rh = new TLRPC.TL_messageReplyHeader();
                rh.flags = entity.replyFlags;
                rh.reply_to_msg_id = entity.replyMessageId;
                rh.reply_to_top_id = entity.replyTopId;
                rh.forum_topic = entity.replyForumTopic;
                if (entity.replyPeerId != 0) {
                    rh.reply_to_peer_id = peer(entity.replyPeerId);
                }
                msg.reply_to = rh;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void mapMediaBack(AyuMessageBase entity, TLRPC.Message msg) {
        try {
            if (entity.documentType == AyuConstants.DOCUMENT_TYPE_STICKER
                    || entity.documentType == AyuConstants.DOCUMENT_TYPE_FILE) {
                if (entity.documentSerialized == null) {
                    return;
                }
                TLRPC.Document doc = deserialize(entity.documentSerialized, Target.DOCUMENT);
                if (doc == null) {
                    return;
                }
                TLRPC.TL_messageMediaDocument m = new TLRPC.TL_messageMediaDocument();
                m.flags |= 1;
                m.document = doc;
                msg.media = m;
                msg.flags |= 512;
                if (entity.mediaPath != null) {
                    msg.attachPath = entity.mediaPath;
                }
            } else if (entity.documentType == AyuConstants.DOCUMENT_TYPE_PHOTO) {
                if (entity.thumbsSerialized == null) {
                    return;
                }
                TLRPC.Photo photo = deserialize(entity.thumbsSerialized, Target.PHOTO);
                if (photo == null) {
                    return;
                }
                TLRPC.TL_messageMediaPhoto m = new TLRPC.TL_messageMediaPhoto();
                m.flags |= 1;
                m.photo = photo;
                msg.media = m;
                msg.flags |= 512;
                if (entity.mediaPath != null) {
                    msg.attachPath = entity.mediaPath;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // =====================================================================
    // shared helper used by AyuHistoryHook to rebuild a viewable message
    // =====================================================================

    public static MessageObject createMessageObject(int currentAccount, AyuMessageBase entity) {
        try {
            TLRPC.TL_message msg = new TLRPC.TL_message();
            mapBack(entity, msg, currentAccount);
            mapMediaBack(entity, msg);
            msg.date = entity.entityCreateDate != 0 ? entity.entityCreateDate : entity.date;
            MessageObject mo = new MessageObject(currentAccount, msg, true, true);
            mo.deleted = true;
            return mo;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    // =====================================================================
    // internals
    // =====================================================================

    private enum Target {DOCUMENT, PHOTO}

    private static long peerId(TLRPC.Peer peer) {
        if (peer == null) {
            return 0;
        }
        if (peer.user_id != 0) {
            return peer.user_id;
        }
        if (peer.chat_id != 0) {
            return -peer.chat_id;
        }
        if (peer.channel_id != 0) {
            return -peer.channel_id;
        }
        return 0;
    }

    private static TLRPC.Peer peer(long id) {
        if (id >= 0) {
            TLRPC.TL_peerUser p = new TLRPC.TL_peerUser();
            p.user_id = id;
            return p;
        }
        TLRPC.TL_peerChannel p = new TLRPC.TL_peerChannel();
        p.channel_id = -id;
        return p;
    }

    private static String resolveAndCopy(int account, TLRPC.Message msg, boolean copyMedia) {
        try {
            File src = FileLoader.getInstance(account).getPathToMessage(msg);
            if (src == null || !src.exists() || src.length() == 0) {
                return null;
            }
            if (!copyMedia) {
                return src.getAbsolutePath();
            }
            File dst = new File(AyuMessagesController.attachmentsPath, msg.id + "_" + src.getName());
            if (!dst.exists()) {
                copyFile(src, dst);
            }
            return dst.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1024 * 64];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        }
    }

    private static byte[] serialize(TLObject object) {
        if (object == null) {
            return null;
        }
        try {
            SerializedData data = new SerializedData(object.getObjectSize());
            object.serializeToStream(data);
            return data.toByteArray();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static byte[] serializeVector(List<? extends TLObject> list) {
        if (list == null) {
            list = new ArrayList<>();
        }
        try {
            int size = 4;
            for (TLObject o : list) {
                size += o.getObjectSize();
            }
            SerializedData data = new SerializedData(size);
            data.writeInt32(list.size());
            for (TLObject o : list) {
                o.serializeToStream(data);
            }
            return data.toByteArray();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static ArrayList<TLRPC.MessageEntity> deserializeEntities(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            SerializedData data = new SerializedData(bytes);
            int count = data.readInt32(false);
            ArrayList<TLRPC.MessageEntity> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int constructor = data.readInt32(false);
                TLRPC.MessageEntity e = TLRPC.MessageEntity.TLdeserialize(data, constructor, false);
                if (e != null) {
                    result.add(e);
                }
            }
            return result;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] bytes, Target target) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            SerializedData data = new SerializedData(bytes);
            int constructor = data.readInt32(false);
            if (target == Target.DOCUMENT) {
                return (T) TLRPC.Document.TLdeserialize(data, constructor, false);
            }
            return (T) TLRPC.Photo.TLdeserialize(data, constructor, false);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}

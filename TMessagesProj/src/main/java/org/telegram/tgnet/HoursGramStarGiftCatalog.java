package org.telegram.tgnet;

import java.util.ArrayList;

/** Reads Telegram's regular Star Gift catalog for HoursGram's local profile studio. */
public final class HoursGramStarGiftCatalog {

    private static final int VECTOR_MAGIC = 0x1cb5c415;
    private static final int GET_STAR_GIFTS = 0xc4563590;
    private static final int STAR_GIFTS = 0x2ed82995;
    private static final int STAR_GIFTS_NOT_MODIFIED = 0xa388a368;
    private static final int STAR_GIFT = 0x313a9547;
    private static final int STAR_GIFT_BACKGROUND = 0xaff56398;

    private HoursGramStarGiftCatalog() {
    }

    public static class Gift {
        public long id;
        public long stars;
        public long availabilityResale;
        public boolean isLimited;
        public boolean isSoldOut;
        public String title;
        public TLRPC.Document sticker;
    }

    public static class GetStarGifts extends TLObject {
        @Override
        public TLObject deserializeResponse(AbstractSerializedData stream, int constructor, boolean exception) {
            Response response = new Response();
            response.readParams(stream, constructor, exception);
            return response;
        }

        @Override
        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(GET_STAR_GIFTS);
            stream.writeInt32(0);
        }
    }

    public static class Response extends TLObject {
        public final ArrayList<Gift> gifts = new ArrayList<>();
        public boolean isNotModified;

        @Override
        public void readParams(AbstractSerializedData stream, boolean exception) {
            readParams(stream, stream.readInt32(exception), exception);
        }

        private void readParams(AbstractSerializedData stream, int constructor, boolean exception) {
            if (constructor == STAR_GIFTS_NOT_MODIFIED) {
                isNotModified = true;
                return;
            }
            if (constructor != STAR_GIFTS) {
                throw new RuntimeException(String.format("Unexpected Star Gifts constructor %x", constructor));
            }

            stream.readInt32(exception); // hash
            int giftCount = readVectorCount(stream, exception);
            for (int i = 0; i < giftCount; i++) {
                gifts.add(readGift(stream, exception));
            }

            int chatCount = readVectorCount(stream, exception);
            for (int i = 0; i < chatCount; i++) {
                TLRPC.Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
            }

            int userCount = readVectorCount(stream, exception);
            for (int i = 0; i < userCount; i++) {
                TLRPC.User.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }
    }

    private static Gift readGift(AbstractSerializedData stream, boolean exception) {
        int constructor = stream.readInt32(exception);
        if (constructor != STAR_GIFT) {
            throw new RuntimeException(String.format("Unexpected Star Gift constructor %x", constructor));
        }

        Gift gift = new Gift();
        int flags = stream.readInt32(exception);
        gift.isLimited = (flags & 1) != 0;
        gift.isSoldOut = (flags & 2) != 0;
        gift.id = stream.readInt64(exception);
        gift.sticker = TLRPC.Document.TLdeserialize(stream, stream.readInt32(exception), exception);
        gift.stars = stream.readInt64(exception);
        if ((flags & 1) != 0) {
            stream.readInt32(exception); // availability remains
            stream.readInt32(exception); // availability total
        }
        if ((flags & 16) != 0) {
            gift.availabilityResale = stream.readInt64(exception);
        }
        stream.readInt64(exception); // convert stars
        if ((flags & 2) != 0) {
            stream.readInt32(exception); // first sale date
            stream.readInt32(exception); // last sale date
        }
        if ((flags & 8) != 0) {
            stream.readInt64(exception); // upgrade stars
        }
        if ((flags & 16) != 0) {
            stream.readInt64(exception); // resell minimum stars
        }
        if ((flags & 32) != 0) {
            gift.title = stream.readString(exception);
        }
        if ((flags & 64) != 0) {
            TLRPC.Peer.TLdeserialize(stream, stream.readInt32(exception), exception);
        }
        if ((flags & 256) != 0) {
            stream.readInt32(exception); // per-user total
            stream.readInt32(exception); // per-user remains
        }
        if ((flags & 512) != 0) {
            stream.readInt32(exception); // locked until date
        }
        if ((flags & 2048) != 0) {
            stream.readString(exception); // auction slug
            stream.readInt32(exception); // gifts per round
            stream.readInt32(exception); // auction start date
        }
        if ((flags & 4096) != 0) {
            stream.readInt32(exception); // upgrade variants
        }
        if ((flags & 8192) != 0) {
            int backgroundConstructor = stream.readInt32(exception);
            if (backgroundConstructor != STAR_GIFT_BACKGROUND) {
                throw new RuntimeException(String.format("Unexpected Star Gift background constructor %x", backgroundConstructor));
            }
            stream.readInt32(exception); // center color
            stream.readInt32(exception); // edge color
            stream.readInt32(exception); // text color
        }
        return gift;
    }

    private static int readVectorCount(AbstractSerializedData stream, boolean exception) {
        int vectorMagic = stream.readInt32(exception);
        if (vectorMagic != VECTOR_MAGIC) {
            throw new RuntimeException(String.format("Wrong Star Gifts vector magic %x", vectorMagic));
        }
        return stream.readInt32(exception);
    }
}

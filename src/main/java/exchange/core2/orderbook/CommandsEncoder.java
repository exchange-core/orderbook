package exchange.core2.orderbook;

import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

import static exchange.core2.orderbook.IOrderBook.*;

public final class CommandsEncoder {

    public static MutableDirectBuffer placeOrder(final byte type,
                                                 final long orderId,
                                                 final long uid,
                                                 final long price,
                                                 final long reservedBidPrice,
                                                 final long size,
                                                 final OrderAction action) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(64);

        buf.putLong(PLACE_OFFSET_UID, uid);
        buf.putLong(PLACE_OFFSET_ORDER_ID, orderId);
        buf.putLong(PLACE_OFFSET_PRICE, price);
        buf.putLong(PLACE_OFFSET_RESERVED_BID_PRICE, reservedBidPrice);
        buf.putLong(PLACE_OFFSET_SIZE, size);
        buf.putLong(PLACE_OFFSET_ACTION, action.getCode());
        buf.putLong(PLACE_OFFSET_TYPE, type);

        return buf;
    }


    public static MutableDirectBuffer cancel(final long orderId,
                                             final long uid) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(16);

        buf.putLong(PLACE_OFFSET_UID, uid);
        buf.putLong(PLACE_OFFSET_ORDER_ID, orderId);

        return buf;
    }

    public static MutableDirectBuffer reduce(final long orderId,
                                             final long uid,
                                             final long size) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(24);

        buf.putLong(REDUCE_OFFSET_UID, uid);
        buf.putLong(REDUCE_OFFSET_ORDER_ID, orderId);
        buf.putLong(REDUCE_OFFSET_SIZE, size);

        return buf;
    }

    public static MutableDirectBuffer move(final long orderId,
                                           final long uid,
                                           final long price) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(24);

        buf.putLong(MOVE_OFFSET_UID, uid);
        buf.putLong(MOVE_OFFSET_ORDER_ID, orderId);
        buf.putLong(MOVE_OFFSET_PRICE, price);

        return buf;
    }

}

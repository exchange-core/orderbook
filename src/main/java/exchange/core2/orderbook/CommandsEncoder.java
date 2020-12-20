/*
 * Copyright 2020 Maksim Zheravin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
                                                 final OrderAction action,
                                                 final int userCookie) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(64);
        placeOrder(buf, 0, type, orderId, uid, price, reservedBidPrice, size, action, userCookie);
        return buf;
    }

    public static void placeOrder(final MutableDirectBuffer buf,
                                  final int offset,
                                  final byte type,
                                  final long orderId,
                                  final long uid,
                                  final long price,
                                  final long reservedBidPrice,
                                  final long size,
                                  final OrderAction action,
                                  final int userCookie) {

        buf.putLong(offset + PLACE_OFFSET_UID, uid);
        buf.putLong(offset + PLACE_OFFSET_ORDER_ID, orderId);
        buf.putLong(offset + PLACE_OFFSET_PRICE, price);
        buf.putLong(offset + PLACE_OFFSET_RESERVED_BID_PRICE, reservedBidPrice);
        buf.putLong(offset + PLACE_OFFSET_SIZE, size);
        buf.putInt(offset + PLACE_OFFSET_USER_COOKIE, userCookie);
        buf.putByte(offset + PLACE_OFFSET_ACTION, action.getCode());
        buf.putByte(offset + PLACE_OFFSET_TYPE, type);
    }


    public static MutableDirectBuffer cancel(final long orderId,
                                             final long uid) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(16);
        cancel(buf, 0, orderId, uid);
        return buf;
    }

    public static void cancel(final MutableDirectBuffer buf,
                              final int offset,
                              final long orderId,
                              final long uid) {

        buf.putLong(offset + PLACE_OFFSET_UID, uid);
        buf.putLong(offset + PLACE_OFFSET_ORDER_ID, orderId);
    }

    public static MutableDirectBuffer reduce(final long orderId,
                                             final long uid,
                                             final long size) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(24);
        reduce(buf, 0, orderId, uid, size);
        return buf;
    }

    public static void reduce(final MutableDirectBuffer buf,
                              final int offset,
                              final long orderId,
                              final long uid,
                              final long size) {

        buf.putLong(offset + REDUCE_OFFSET_UID, uid);
        buf.putLong(offset + REDUCE_OFFSET_ORDER_ID, orderId);
        buf.putLong(offset + REDUCE_OFFSET_SIZE, size);
    }

    public static MutableDirectBuffer move(final long orderId,
                                           final long uid,
                                           final long price) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(24);
        move(buf, 0, orderId, uid, price);
        return buf;
    }

    public static void move(final MutableDirectBuffer buf,
                            final int offset,
                            final long orderId,
                            final long uid,
                            final long price) {

        buf.putLong(offset + MOVE_OFFSET_UID, uid);
        buf.putLong(offset + MOVE_OFFSET_ORDER_ID, orderId);
        buf.putLong(offset + MOVE_OFFSET_PRICE, price);
    }

}

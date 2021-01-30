/*
 * Copyright 2021 Maksim Zheravin
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

package exchange.core2.orderbook.util;

import exchange.core2.orderbook.IOrderBook;
import exchange.core2.orderbook.OrderAction;
import org.agrona.BitUtil;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

import static exchange.core2.orderbook.IOrderBook.*;

public final class CommandsEncoder {

    public static void placeOrder(final BufferWriter bufferWriter,
                                  final byte type,
                                  final long orderId,
                                  final long uid,
                                  final long price,
                                  final long reservedBidPrice,
                                  final long size,
                                  final OrderAction action,
                                  final int userCookie) {

        final int bytesWritten = placeOrder(
                bufferWriter.getBuffer(),
                bufferWriter.getWriterPosition(),
                type,
                orderId,
                uid,
                price,
                reservedBidPrice,
                size,
                action,
                userCookie);

        bufferWriter.skipBytes(bytesWritten);
    }


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

    public static int placeOrder(final MutableDirectBuffer buf,
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
        return IOrderBook.PLACE_OFFSET_END;
    }

    public static void cancel(final BufferWriter bufferWriter,
                              final long orderId,
                              final long uid) {

        final int bytesWritten = cancel(
                bufferWriter.getBuffer(),
                bufferWriter.getWriterPosition(),
                orderId,
                uid);

        bufferWriter.skipBytes(bytesWritten);
    }

    public static MutableDirectBuffer cancel(final long orderId,
                                             final long uid) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(16);
        cancel(buf, 0, orderId, uid);
        return buf;
    }

    public static int cancel(final MutableDirectBuffer buf,
                             final int offset,
                             final long orderId,
                             final long uid) {

        buf.putLong(offset + CANCEL_OFFSET_UID, uid);
        buf.putLong(offset + CANCEL_OFFSET_ORDER_ID, orderId);
        return CANCEL_OFFSET_END;
    }


    public static void reduce(final BufferWriter bufferWriter,
                              final long orderId,
                              final long uid,
                              final long size) {

        final int bytesWritten = reduce(bufferWriter.getBuffer(),
                bufferWriter.getWriterPosition(),
                orderId,
                uid,
                size);

        bufferWriter.skipBytes(bytesWritten);
    }


    public static MutableDirectBuffer reduce(final long orderId,
                                             final long uid,
                                             final long size) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(24);
        reduce(buf, 0, orderId, uid, size);
        return buf;
    }

    public static int reduce(final MutableDirectBuffer buf,
                             final int offset,
                             final long orderId,
                             final long uid,
                             final long size) {

        buf.putLong(offset + REDUCE_OFFSET_UID, uid);
        buf.putLong(offset + REDUCE_OFFSET_ORDER_ID, orderId);
        buf.putLong(offset + REDUCE_OFFSET_SIZE, size);
        return REDUCE_OFFSET_END;
    }


    public static void move(final BufferWriter bufferWriter,
                            final long orderId,
                            final long uid,
                            final long price) {

        final int bytesWritten = move(
                bufferWriter.getBuffer(),
                bufferWriter.getWriterPosition(),
                orderId,
                uid,
                price);

        bufferWriter.skipBytes(bytesWritten);
    }

    public static MutableDirectBuffer move(final long orderId,
                                           final long uid,
                                           final long price) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(24);
        move(buf, 0, orderId, uid, price);
        return buf;
    }

    public static int move(final MutableDirectBuffer buf,
                           final int offset,
                           final long orderId,
                           final long uid,
                           final long price) {

        buf.putLong(offset + MOVE_OFFSET_UID, uid);
        buf.putLong(offset + MOVE_OFFSET_ORDER_ID, orderId);
        buf.putLong(offset + MOVE_OFFSET_PRICE, price);
        return MOVE_OFFSET_END;
    }


    public static void L2DataQuery(final BufferWriter bufferWriter,
                                   final int limit) {

        final int bytesWritten = L2DataQuery(
                bufferWriter.getBuffer(),
                bufferWriter.getWriterPosition(),
                limit);

        bufferWriter.skipBytes(bytesWritten);
    }

    public static MutableDirectBuffer L2DataQuery(final int limit) {

        final MutableDirectBuffer buf = new ExpandableDirectByteBuffer(4);
        L2DataQuery(buf, 0, limit);
        return buf;
    }

    public static int L2DataQuery(final MutableDirectBuffer buf,
                                  final int offset,
                                  final int limit) {

        buf.putInt(offset, limit);
        return BitUtil.SIZE_OF_INT;
    }

}

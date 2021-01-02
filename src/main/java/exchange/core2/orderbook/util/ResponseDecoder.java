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

import exchange.core2.orderbook.OrderAction;
import exchange.core2.orderbook.api.*;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static exchange.core2.orderbook.IOrderBook.*;
import static org.agrona.BitUtil.*;

public final class ResponseDecoder {

    private static final Logger log = LoggerFactory.getLogger(ResponseDecoder.class);

    public static OrderBookResponse readResult(final MutableDirectBuffer buf,
                                               final int responseMsgSize) {

        return readResult(new BufferReader(buf, responseMsgSize, 0));
    }

    public static OrderBookResponse readResult(final BufferReader buf) {

//        log.debug("Parsing response:\n{}", buf.prettyHexDump());

        final int msgSize = buf.getSize();

        final byte commandType = buf.readByte();

        if (commandType == QUERY_ORDER_BOOK) {
            return decodeL2Data(buf, msgSize);
        }

        final long uid = buf.readLong();
        final long orderId = buf.readLong();

        final int userCookie;
        if (commandType == COMMAND_PLACE_ORDER) {
            userCookie = buf.readInt();
        } else {
            userCookie = 0;
        }


        final short encodedResultCode = buf.getShort(msgSize - SIZE_OF_SHORT);

        final boolean hasReduceEvent = (encodedResultCode & RESULT_OFFSET_REDUCE_EVT_FLAG) != 0;
        final OrderAction takerOrderAction = (encodedResultCode & RESULT_OFFSET_TAKER_ACTION_BID_FLAG) != 0 ? OrderAction.BID : OrderAction.ASK;
        final boolean takerOrderCompleted = (encodedResultCode & RESULT_OFFSET_TAKE_ORDER_COMPLETED_FLAG) != 0;

        final short resultCode = (short) (encodedResultCode & RESULT_MASK);

        final Long remainingSize = takerOrderCompleted ? null : buf.getLong(msgSize - (SIZE_OF_SHORT + SIZE_OF_LONG));

        final ReduceEvent reduceEvent;
        final int reduceOffsetEndRev = SIZE_OF_SHORT + (takerOrderCompleted ? 0 : SIZE_OF_LONG);
        final int reduceOffsetStart;
        if (hasReduceEvent) {
            reduceOffsetStart = msgSize - (reduceOffsetEndRev + RESPONSE_OFFSET_REVT_END);
            reduceEvent = readReduceEvent(buf, reduceOffsetStart);
        } else {
            reduceOffsetStart = msgSize - reduceOffsetEndRev;
            reduceEvent = null;
        }

        final List<TradeEvent> tradeEvents;
        if (commandType == COMMAND_PLACE_ORDER || commandType == COMMAND_MOVE_ORDER) {
            // TODO change to sizes
            final int userCookieSize = commandType == COMMAND_PLACE_ORDER ? SIZE_OF_INT : 0;
            final int tradeEventsBlockStartOffset = SIZE_OF_BYTE + SIZE_OF_LONG + SIZE_OF_LONG + userCookieSize;

            if (reduceOffsetStart != tradeEventsBlockStartOffset) {
                final int tradeEventsBlockLength = reduceOffsetStart - tradeEventsBlockStartOffset;
                final int numberOfBlock = tradeEventsBlockLength / RESPONSE_OFFSET_TEVT_END;
                tradeEvents = new ArrayList<>(numberOfBlock);
                for (int offset = tradeEventsBlockStartOffset; offset < reduceOffsetStart; offset += RESPONSE_OFFSET_TEVT_END) {
                    tradeEvents.add(readTradeEvent(buf, offset));
                }
            } else {
                tradeEvents = Collections.emptyList();
            }
        } else {
            tradeEvents = Collections.emptyList();
        }


        switch (commandType) {
            case COMMAND_PLACE_ORDER:
                return new CommandResponsePlace(
                        resultCode,
                        uid,
                        orderId,
                        takerOrderAction,
                        takerOrderCompleted,
                        userCookie,
                        remainingSize,
                        tradeEvents,
                        reduceEvent);

            case COMMAND_CANCEL_ORDER:
                return new CommandResponseCancel(
                        resultCode,
                        uid,
                        orderId,
                        takerOrderAction,
                        takerOrderCompleted,
                        tradeEvents,
                        reduceEvent);

            case COMMAND_MOVE_ORDER:
                return new CommandResponseMove(
                        resultCode,
                        uid,
                        orderId,
                        takerOrderAction,
                        takerOrderCompleted,
                        remainingSize,
                        tradeEvents,
                        reduceEvent);

            case COMMAND_REDUCE_ORDER:
                return new CommandResponseReduce(
                        resultCode,
                        uid,
                        orderId,
                        takerOrderAction,
                        takerOrderCompleted,
                        remainingSize,
                        tradeEvents,
                        reduceEvent);

            default:
                throw new IllegalStateException("Unknown commandType=" + commandType);
        }
    }

    private static OrderBookResponse decodeL2Data(BufferReader buf, int msgSize) {

        final short encodedResultCode = buf.getShort(msgSize - RESPONSE_OFFSET_L2_RESULT);
        if (encodedResultCode == RESULT_SUCCESS) {

            final int asksNum = buf.getInt(msgSize - RESPONSE_OFFSET_L2_ASK_RECORDS);
            final int bidsNum = buf.getInt(msgSize - RESPONSE_OFFSET_L2_BID_RECORDS);

            final List<QueryResponseL2Data.L2Record> asks = readL2Records(buf, asksNum);
            final List<QueryResponseL2Data.L2Record> bids = readL2Records(buf, bidsNum);

            return new QueryResponseL2Data(encodedResultCode, asks, bids);
        } else {
            return new QueryResponseL2Data(encodedResultCode, Collections.emptyList(), Collections.emptyList());
        }
    }

    private static List<QueryResponseL2Data.L2Record> readL2Records(final BufferReader buf, final int num) {
        final List<QueryResponseL2Data.L2Record> list = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            final long price = buf.readLong();
            final long volume = buf.readLong();
            final int orders = buf.readInt();
            list.add(new QueryResponseL2Data.L2Record(price, volume, orders));
        }
        return list;
    }

    private static TradeEvent readTradeEvent(final BufferReader buf,
                                             final int offset) {

//        log.debug("READ TRADE: offset={} buf=\n{}", offset, PrintBufferUtil.hexDump(buf, 0, 128));

        final long makerOrderId = buf.getLong(offset + RESPONSE_OFFSET_TEVT_MAKER_ORDER_ID);
        final long makerUid = buf.getLong(offset + RESPONSE_OFFSET_TEVT_MAKER_UID);
        final long price = buf.getLong(offset + RESPONSE_OFFSET_TEVT_PRICE);
        final long reservedBidPrice = buf.getLong(offset + RESPONSE_OFFSET_TEVT_RESERV_BID_PRICE);
        final long tradeVolume = buf.getLong(offset + RESPONSE_OFFSET_TEVT_TRADE_VOL);
        final boolean takerCompleted = buf.getByte(offset + RESPONSE_OFFSET_TEVT_MAKER_ORDER_COMPLETED) != 0;

        return new TradeEvent(makerOrderId, makerUid, price, reservedBidPrice, tradeVolume, takerCompleted);
    }

    private static ReduceEvent readReduceEvent(final BufferReader buf, final int offset) {

        final long price = buf.getLong(offset + RESPONSE_OFFSET_REVT_PRICE);
        final long reservedBidPrice = buf.getLong(offset + RESPONSE_OFFSET_REVT_RESERV_BID_PRICE);
        final long reducedVolume = buf.getLong(offset + RESPONSE_OFFSET_REVT_REDUCED_VOL);

        return new ReduceEvent(reducedVolume, price, reservedBidPrice);
    }

}

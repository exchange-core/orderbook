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

import exchange.core2.orderbook.IResponseHandler;
import exchange.core2.orderbook.OrderAction;
import exchange.core2.orderbook.api.QueryResponseL2Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static exchange.core2.orderbook.IOrderBook.*;
import static org.agrona.BitUtil.*;


public final class ResponseFastDecoder {

    private static final Logger log = LoggerFactory.getLogger(ResponseFastDecoder.class);

    private final IResponseHandler responseHandler;

    // TODO exceptions handler ?

    public ResponseFastDecoder(final IResponseHandler responseHandler) {
        this.responseHandler = responseHandler;
    }

    public void readResult(final BufferReader buf,
                           final long time,
                           final long correlationId,
                           final int symbolId) {

//        log.debug("Parsing response:\n{}", buf.prettyHexDump());

        final int msgSize = buf.getRemainingSize();

        // TODO provide as an argument?
        final byte commandType = buf.readByte();

//        log.debug("commandType:{}", commandType);

        if (commandType < 1 || commandType > 5) {
            throw new IllegalArgumentException("unsupported by ResponseFastDecoder commandType=" + commandType);
        }

        if (commandType == QUERY_ORDER_BOOK) {
            decodeL2Data(buf, msgSize, time, correlationId, symbolId);
            return;
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

        // decoding some flags from result code bits
        final boolean hasReduceEvent = (encodedResultCode & RESULT_OFFSET_REDUCE_EVT_FLAG) != 0;
        final OrderAction takerOrderAction = (encodedResultCode & RESULT_OFFSET_TAKER_ACTION_BID_FLAG) != 0 ? OrderAction.BID : OrderAction.ASK;
        final boolean takerOrderCompleted = (encodedResultCode & RESULT_OFFSET_TAKE_ORDER_COMPLETED_FLAG) != 0;

        final short resultCode = (short) (encodedResultCode & RESULT_MASK);

        final long remainingSize = takerOrderCompleted ? UNSPECIFIED_REMAINING_SIZE_MARKER : buf.getLong(msgSize - (SIZE_OF_SHORT + SIZE_OF_LONG));

        final int reduceOffsetEndRev = SIZE_OF_SHORT + (takerOrderCompleted ? 0 : SIZE_OF_LONG);
        final int reduceOffsetStart;
        if (hasReduceEvent) {
            reduceOffsetStart = msgSize - (reduceOffsetEndRev + RESPONSE_OFFSET_REVT_END);

            final long price = buf.getLong(reduceOffsetStart + RESPONSE_OFFSET_REVT_PRICE);
            final long reservedBidPrice = buf.getLong(reduceOffsetStart + RESPONSE_OFFSET_REVT_RESERV_BID_PRICE);
            final long reducedSize = buf.getLong(reduceOffsetStart + RESPONSE_OFFSET_REVT_REDUCED_SIZE);

            responseHandler.onReduceEvent(
                    symbolId,
                    time,
                    uid,
                    orderId,
                    takerOrderAction,
                    reducedSize,
                    price,
                    reservedBidPrice);


        } else {
            reduceOffsetStart = msgSize - reduceOffsetEndRev;
        }

        if (commandType == COMMAND_PLACE_ORDER || commandType == COMMAND_MOVE_ORDER) {
            // TODO change to sizes
            final int userCookieSize = commandType == COMMAND_PLACE_ORDER ? SIZE_OF_INT : 0;
            final int tradeEventsBlockStartOffset = SIZE_OF_BYTE + SIZE_OF_LONG + SIZE_OF_LONG + userCookieSize;

            if (reduceOffsetStart != tradeEventsBlockStartOffset) {
                final int tradeEventsBlockLength = reduceOffsetStart - tradeEventsBlockStartOffset;
                if (tradeEventsBlockLength % RESPONSE_OFFSET_TEVT_END != 0) {
                    throw new IllegalStateException("Incorrect trade events block length: " + tradeEventsBlockLength);
                }

                for (int offset = tradeEventsBlockStartOffset; offset < reduceOffsetStart; offset += RESPONSE_OFFSET_TEVT_END) {

                    //        log.debug("READ TRADE: offset={} buf=\n{}", offset, PrintBufferUtil.hexDump(buf, 0, 128));

                    final long makerOrderId = buf.getLong(offset + RESPONSE_OFFSET_TEVT_MAKER_ORDER_ID);
                    final long makerUid = buf.getLong(offset + RESPONSE_OFFSET_TEVT_MAKER_UID);
                    final long price = buf.getLong(offset + RESPONSE_OFFSET_TEVT_PRICE);
                    final long reservedBidPrice = buf.getLong(offset + RESPONSE_OFFSET_TEVT_RESERV_BID_PRICE);
                    final long tradeVolume = buf.getLong(offset + RESPONSE_OFFSET_TEVT_TRADE_SIZE);
                    final boolean makerCompleted = buf.getByte(offset + RESPONSE_OFFSET_TEVT_MAKER_ORDER_COMPLETED) != 0;

                    responseHandler.onTradeEvent(
                            symbolId,
                            time,
                            uid,
                            orderId,
                            takerOrderAction,
                            makerUid,
                            makerOrderId,
                            price,
                            reservedBidPrice,
                            tradeVolume,
                            makerCompleted);
                }
            }
        }

        switch (commandType) {
            case COMMAND_PLACE_ORDER:
                responseHandler.onOrderPlaceResult(
                        resultCode,
                        time,
                        correlationId,
                        symbolId,
                        uid,
                        orderId,
                        takerOrderAction,
                        takerOrderCompleted,
                        userCookie,
                        remainingSize);
                break;

            case COMMAND_CANCEL_ORDER:
                responseHandler.onOrderCancelResult(
                        resultCode,
                        time,
                        correlationId,
                        symbolId,
                        uid,
                        orderId,
                        takerOrderAction,
                        takerOrderCompleted);
                break;

            case COMMAND_MOVE_ORDER:
                responseHandler.onOrderMoveResult(
                        resultCode,
                        time,
                        correlationId,
                        symbolId,
                        uid,
                        orderId,
                        takerOrderAction,
                        takerOrderCompleted,
                        remainingSize);
                break;

            case COMMAND_REDUCE_ORDER:
                responseHandler.onOrderReduceResult(
                        resultCode,
                        time,
                        correlationId,
                        symbolId,
                        uid,
                        orderId,
                        takerOrderAction,
                        takerOrderCompleted,
                        remainingSize);
                break;

            default:
                throw new IllegalStateException("Unknown commandType=" + commandType);
        }
    }

    private void decodeL2Data(final BufferReader buf,
                              final int msgSize,
                              final long time,
                              final long correlationId,
                              final int symbolId) {

        // reading result code and sizes from the end of the message
        final short encodedResultCode = buf.getShort(msgSize - RESPONSE_OFFSET_L2_RESULT);

        // should be specified as 0 (even for for non-successful result)
        final int asksNum = buf.getInt(msgSize - RESPONSE_OFFSET_L2_ASK_RECORDS);
        final int bidsNum = buf.getInt(msgSize - RESPONSE_OFFSET_L2_BID_RECORDS);

        // calculating block offsets
        final int asksOffset = buf.getReadPosition();
        final int bidsOffset = asksOffset + RESPONSE_OFFSET_L2_RECORD_END * asksNum;

        IResponseHandler.IL2Proxy proxy = new IResponseHandler.IL2Proxy() {
            @Override
            public boolean isEmpty() {
                // legitimate if min request size >= 1
                return asksNum == 0 && bidsNum == 0;
            }

            @Override
            public QueryResponseL2Data toQueryResponseL2Data() {
                return null; // TODO
            }

            @Override
            public void fillPricesVolumesArray(long[] prices, long[] volumes, int size, OrderAction askBid) {
                // TODO
            }

            @Override
            public void fillPricesVolumesOrdersArray(long[] prices, long[] volumes, int[] orders, int arraySize, OrderAction askBid) {
                // TODO
            }

            @Override
            public int getAskRecordsNum() {
                return asksNum;
            }


            @Override
            public long getAskPrice(final int index) {
                validateBoundsOrThrow(index, asksNum);
                return buf.getLong(asksOffset + RESPONSE_OFFSET_L2_RECORD_END * index + RESPONSE_OFFSET_L2_RECORD_PRICE);
            }

            @Override
            public long getAskVolume(final int index) {
                validateBoundsOrThrow(index, asksNum);
                return buf.getLong(asksOffset + RESPONSE_OFFSET_L2_RECORD_END * index + RESPONSE_OFFSET_L2_RECORD_VOLUME);
            }

            @Override
            public int getAskOrders(final int index) {
                validateBoundsOrThrow(index, asksNum);
                return buf.getInt(asksOffset + RESPONSE_OFFSET_L2_RECORD_END * index + RESPONSE_OFFSET_L2_RECORD_VOLUME);
            }

            @Override
            public int getBidRecordsNum() {
                return bidsNum;
            }


            @Override
            public long getBidPrice(final int index) {
                validateBoundsOrThrow(index, bidsNum);
                return buf.getLong(bidsOffset + RESPONSE_OFFSET_L2_RECORD_END * index + RESPONSE_OFFSET_L2_RECORD_PRICE);
            }

            @Override
            public long getBidVolume(final int index) {
                validateBoundsOrThrow(index, bidsNum);
                return buf.getLong(bidsOffset + RESPONSE_OFFSET_L2_RECORD_END * index + RESPONSE_OFFSET_L2_RECORD_VOLUME);
            }

            @Override
            public int getBidOrders(final int index) {
                validateBoundsOrThrow(index, bidsNum);
                return buf.getInt(bidsOffset + RESPONSE_OFFSET_L2_RECORD_END * index + RESPONSE_OFFSET_L2_RECORD_ORDERS);
            }
        };

        responseHandler.onL2DataResult(encodedResultCode, time, correlationId, symbolId, proxy);
    }

    private void validateBoundsOrThrow(final int index, final int maxIndex) {
        if (index < 0 || index >= maxIndex) {
            throw new IllegalArgumentException(String.format("index %d out of bounds [0, %d)", index, maxIndex));
        }
    }


}

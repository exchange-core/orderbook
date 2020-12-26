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

import exchange.core2.orderbook.util.BufferWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static exchange.core2.orderbook.IOrderBook.*;

public final class OrderBookEventsHelper {

    private static final Logger log = LoggerFactory.getLogger(OrderBookEventsHelper.class);

    private final BufferWriter resultsBuffer;

    private final boolean debug;

    public OrderBookEventsHelper(final BufferWriter resultsBuffer,
                                 final boolean debug) {
        this.resultsBuffer = resultsBuffer;
        this.debug = debug;
    }

    public void appendTradeEvent(final IOrder matchingOrder,
                                 final boolean makerOrderCompleted,
                                 final long tradeVolume,
                                 final long bidderHoldPrice) {

        if (debug) {
            log.debug("MATCH: orderId={} matchingOrder={} tradeVolume={} makerOrderCompleted={}",
                    matchingOrder.getOrderId(), matchingOrder, tradeVolume, makerOrderCompleted);
        }

        resultsBuffer.appendLong(matchingOrder.getOrderId());
        resultsBuffer.appendLong(matchingOrder.getUid());
        resultsBuffer.appendLong(matchingOrder.getPrice());
        resultsBuffer.appendLong(bidderHoldPrice); // matching order reserved price for released Exchange Bids funds
        resultsBuffer.appendLong(tradeVolume);
        resultsBuffer.appendByte(makerOrderCompleted ? (byte) 1 : 0);

//        log.debug("BUF after trade event: \n{}", PrintBufferUtil.hexDump(resultsBuffer, 0, 128));

        if (debug) {
            log.debug("BUF after trade event: \n{}", resultsBuffer.prettyHexDump());
        }
    }

    public void appendReduceEvent(final long price,
                                  final long bidderHoldPrice,
                                  final long reduceSize) {

        resultsBuffer.appendLong(price);
        resultsBuffer.appendLong(bidderHoldPrice); // matching order reserved price for released Exchange Bids funds
        resultsBuffer.appendLong(reduceSize);
    }

    public void appendResultCode(final short resultCode,
                                 final boolean takerOrderCompleted,
                                 final OrderAction takerAction,
                                 final boolean hasReduceEvent) {

        final short encodedResult = (short) (resultCode
                | (takerOrderCompleted ? RESULT_OFFSET_TAKE_ORDER_COMPLETED_FLAG : 0)
                | (takerAction == OrderAction.BID ? RESULT_OFFSET_TAKER_ACTION_BID_FLAG : 0)
                | (hasReduceEvent ? RESULT_OFFSET_REDUCE_EVT_FLAG : 0));

        resultsBuffer.appendShort(encodedResult);

        if (debug) {
            log.debug("encodedResult={} BUF after fillEventsHeader: \n{}", encodedResult, resultsBuffer.prettyHexDump());
        }
    }


    public void appendL2Record(final long price,
                               final long volume,
                               final int numOrders) {

        resultsBuffer.appendLong(price);
        resultsBuffer.appendLong(volume);
        resultsBuffer.appendInt(numOrders);
    }
}

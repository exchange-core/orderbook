/*
 * Copyright 2019 Maksim Zheravin
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

import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static exchange.core2.orderbook.IOrderBook.*;

public final class OrderBookEventsHelper {

    private static final Logger log = LoggerFactory.getLogger(OrderBookEventsHelper.class);

    private final MutableDirectBuffer resultsBuffer;

    public OrderBookEventsHelper(MutableDirectBuffer resultsBuffer) {
        this.resultsBuffer = resultsBuffer;
    }

    public void sendTradeEvent(final IOrder matchingOrder,
                               final boolean makerCompleted,
                               final long tradeVolume,
                               final long bidderHoldPrice) {

        final int tradeEventsNum = resultsBuffer.getInt(RESPONSE_OFFSET_HEADER_TRADES_EVT_NUM);
        resultsBuffer.putInt(RESPONSE_OFFSET_HEADER_TRADES_EVT_NUM, tradeEventsNum + 1);

        final int offset = IOrderBook.getTradeEventOffset(tradeEventsNum);

//        log.debug("MATCH: tradeEventsNum={} offset={} orderId={} matchingOrder={}", tradeEventsNum, offset, matchingOrder.getOrderId(), matchingOrder);

        resultsBuffer.putLong(offset + RESPONSE_OFFSET_TEVT_MAKER_ORDER_ID, matchingOrder.getOrderId());
        resultsBuffer.putLong(offset + RESPONSE_OFFSET_TEVT_MAKER_UID, matchingOrder.getUid());
        resultsBuffer.putLong(offset + RESPONSE_OFFSET_TEVT_PRICE, matchingOrder.getPrice());
        resultsBuffer.putLong(offset + RESPONSE_OFFSET_TEVT_RESERV_BID_PRICE, bidderHoldPrice); // matching order reserved price for released Exchange Bids funds
        resultsBuffer.putLong(offset + RESPONSE_OFFSET_TEVT_TRADE_VOL, tradeVolume);
        resultsBuffer.putByte(offset + RESPONSE_OFFSET_TEVT_MAKER_ORDER_COMPLETED, makerCompleted ? (byte) 1 : 0);

//        log.debug("BUF after trade event: \n{}", PrintBufferUtil.hexDump(resultsBuffer, 0, 128));

    }

    public void writeSingleReduceEvent(final long price,
                                       final long bidderHoldPrice,
                                       final long reduceSize) {

        resultsBuffer.putByte(RESPONSE_OFFSET_HEADER_REDUCE_EVT, (byte) 1);

        // fixed offset beacuse reduce is always a single event
        resultsBuffer.putLong(RESPONSE_OFFSET_TBLK_END + RESPONSE_OFFSET_REVT_PRICE, price);
        resultsBuffer.putLong(RESPONSE_OFFSET_TBLK_END + RESPONSE_OFFSET_REVT_RESERV_BID_PRICE, bidderHoldPrice); // matching order reserved price for released Exchange Bids funds
        resultsBuffer.putLong(RESPONSE_OFFSET_TBLK_END + RESPONSE_OFFSET_REVT_REDUCED_VOL, reduceSize);
    }

    public void appendRejectEvent(final long price,
                                  final long bidderHoldPrice,
                                  final long reduceSize) {

        final int tradeEventsNum = resultsBuffer.getInt(RESPONSE_OFFSET_HEADER_TRADES_EVT_NUM);
        resultsBuffer.putByte(RESPONSE_OFFSET_HEADER_REDUCE_EVT, (byte) 1);

        // calculate offset based on number of trades written
        final int offset = IOrderBook.getTradeEventOffset(tradeEventsNum);

        resultsBuffer.putLong(offset + RESPONSE_OFFSET_REVT_PRICE, price);
        resultsBuffer.putLong(offset + RESPONSE_OFFSET_REVT_RESERV_BID_PRICE, bidderHoldPrice); // matching order reserved price for released Exchange Bids funds
        resultsBuffer.putLong(offset + RESPONSE_OFFSET_REVT_REDUCED_VOL, reduceSize);
    }

    public void fillEventsHeader(final long takerOrderId,
                                 final long takerUid,
                                 final boolean takerOrderCompleted,
                                 final OrderAction takerAction) {

        resultsBuffer.putLong(RESPONSE_OFFSET_TBLK_TAKER_ORDER_ID, takerOrderId);
        resultsBuffer.putLong(RESPONSE_OFFSET_TBLK_TAKER_UID, takerUid);
        resultsBuffer.putByte(RESPONSE_OFFSET_TBLK_TAKER_ORDER_COMPLETED, takerOrderCompleted ? (byte) 1 : 0);
        resultsBuffer.putByte(RESPONSE_OFFSET_TBLK_TAKER_ACTION, takerAction.getCode());

//        log.debug("BUF after fillEventsHeader: \n{}", PrintBufferUtil.hexDump(resultsBuffer, 0, 128));
    }
}

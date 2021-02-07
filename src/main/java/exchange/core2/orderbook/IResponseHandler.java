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

package exchange.core2.orderbook;

import exchange.core2.orderbook.api.QueryResponseL2Data;

public interface IResponseHandler {

    // TODO think how this log allows to rebuild order book (similar to Nasdaq ITCH) for all possible cases

    void onOrderPlaceResult(short resultCode,
                            long time,
                            long correlationId,
                            int symbolId,
                            long uid,
                            long orderId,
                            OrderAction action,
                            boolean orderCompleted,
                            int userCookie,
                            long remainingSize);

    void onOrderCancelResult(short resultCode,
                             long time,
                             long correlationId,
                             int symbolId,
                             long uid,
                             long orderId,
                             OrderAction action,
                             boolean orderCompleted);

    // TODO old price and new price
    void onOrderMoveResult(short resultCode,
                           long time,
                           long correlationId,
                           int symbolId,
                           long uid,
                           long orderId,
                           OrderAction action,
                           boolean orderCompleted,
                           long remainingSize);

    void onOrderReduceResult(short resultCode,
                             long time,
                             long correlationId,
                             int symbolId,
                             long uid,
                             long orderId,
                             OrderAction action,
                             boolean orderCompleted,
                             long remainingSize);

    void onTradeEvent(int symbolId,
                      long time,
                      long takerUid,
                      long takerOrderId,
                      OrderAction takerAction,
                      long makerUid,
                      long makerOrderId,
                      long tradePrice,
                      long reservedBidPrice,
                      long tradeVolume,
                      boolean makerOrderCompleted);


    void onReduceEvent(int symbolId,
                       long time,
                       long uid,
                       long orderId,
                       OrderAction action,
                       long reducedSize,
                       long price,
                       long reservedBidPrice);


    // Technically it is a query response, but must be garbage-free optimized
    void onL2DataResult(short resultCode,
                        long time,
                        long correlationId,
                        int symbolId,
                        IL2Proxy l2dataProxy);


    interface IL2Proxy {

        /**
         * Check if orderbook is empty
         *
         * @return true if empty
         */
        boolean isEmpty();

        /**
         * Instantiates QueryResponseL2Data object
         *
         * @return QueryResponseL2Data object
         */
        QueryResponseL2Data toQueryResponseL2Data();


        void fillPricesVolumesArray(long[] prices, long[] volumes, int size, OrderAction askBid);

        void fillPricesVolumesOrdersArray(long[] prices, long[] volumes, int[] orders, int arraySize, OrderAction askBid);


        /**
         * Number of different BID price-records in the message
         *
         * @return number records
         */

        int getAskRecordsNum();

        long getAskPrice(int index);

        long getAskVolume(int index);

        int getAskOrders(int index);

        /**
         * Number of different BID price-records in the message
         *
         * @return number records
         */
        int getBidRecordsNum();

        long getBidPrice(int index);

        long getBidVolume(int index);

        int getBidOrders(int index);
    }
}

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

package exchange.core2.orderbook.api;

import exchange.core2.orderbook.OrderAction;

import java.util.List;
import java.util.Optional;

public abstract class CommandResponse extends OrderBookResponse{

    private final long uid;
    private final long orderId;
    private final OrderAction takerAction;
    private final boolean orderCompleted; // when true - the order will never appear in the responses
    private final Long remainingSize;


    private final List<TradeEvent> trades;
    private final ReduceEvent reduceEvent;

    public CommandResponse(final short resultCode,
                           final long uid,
                           final long orderId,
                           final OrderAction takerAction,
                           final boolean orderCompleted,
                           final Long remainingSize,
                           final List<TradeEvent> trades,
                           final ReduceEvent reduceEvent) {

        super(resultCode);

        this.uid = uid;
        this.orderId = orderId;
        this.takerAction = takerAction;
        this.orderCompleted = orderCompleted;
        this.remainingSize = remainingSize;
        this.trades = trades;
        this.reduceEvent = reduceEvent;
    }

    public long getUid() {
        return uid;
    }

    public long getOrderId() {
        return orderId;
    }

    public OrderAction getTakerAction() {
        return takerAction;
    }

    public boolean isOrderCompleted() {
        return orderCompleted;
    }

    public List<TradeEvent> getTrades() {
        return trades;
    }

    public Optional<ReduceEvent> getReduceEventOpt() {
        return Optional.ofNullable(reduceEvent);
    }

    public Optional<Long> getRemainingSizeOpt() {
        return Optional.ofNullable(remainingSize);
    }

}

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

import java.util.Objects;

public final class TradeEvent {

    private final long makerOrderId;
    private final long makerUid;
    private final long tradePrice;
    private final long reservedBidPrice;
    private final long tradeSize;
    private final boolean makerOrderCompleted;

    public TradeEvent(long makerOrderId,
                      long makerUid,
                      long tradePrice,
                      long reservedBidPrice,
                      long tradeSize,
                      boolean makerOrderCompleted) {

        this.makerOrderId = makerOrderId;
        this.makerUid = makerUid;
        this.tradePrice = tradePrice;
        this.reservedBidPrice = reservedBidPrice;
        this.tradeSize = tradeSize;
        this.makerOrderCompleted = makerOrderCompleted;
    }

    public long getMakerOrderId() {
        return makerOrderId;
    }

    public long getMakerUid() {
        return makerUid;
    }

    public long getTradePrice() {
        return tradePrice;
    }

    public long getReservedBidPrice() {
        return reservedBidPrice;
    }

    public long getTradeSize() {
        return tradeSize;
    }

    public boolean isMakerOrderCompleted() {
        return makerOrderCompleted;
    }

    @Override
    public String toString() {
        return "TradeEvent{" +
                "makerOrderId=" + makerOrderId +
                ", makerUid=" + makerUid +
                ", tradePrice=" + tradePrice +
                ", reservedBidPrice=" + reservedBidPrice +
                ", tradeSize=" + tradeSize +
                ", makerOrderCompleted=" + makerOrderCompleted +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeEvent that = (TradeEvent) o;
        return makerOrderId == that.makerOrderId &&
                makerUid == that.makerUid &&
                tradePrice == that.tradePrice &&
                reservedBidPrice == that.reservedBidPrice &&
                tradeSize == that.tradeSize &&
                makerOrderCompleted == that.makerOrderCompleted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(makerOrderId, makerUid, tradePrice, reservedBidPrice, tradeSize, makerOrderCompleted);
    }
}

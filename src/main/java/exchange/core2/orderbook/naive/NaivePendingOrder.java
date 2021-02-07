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

package exchange.core2.orderbook.naive;

import exchange.core2.orderbook.IOrder;
import exchange.core2.orderbook.OrderAction;

import java.util.Objects;

public final class NaivePendingOrder implements IOrder {


    public NaivePendingOrder(long orderId,
                             long price,
                             long size,
                             long filled,
                             long reserveBidPrice,
                             OrderAction action,
                             long uid,
                             long timestamp) {

        this.orderId = orderId;
        this.price = price;
        this.size = size;
        this.filled = filled;
        this.reserveBidPrice = reserveBidPrice;
        this.action = action;
        this.uid = uid;
        this.timestamp = timestamp;
    }

    private final long orderId;

    private long price;

    private long size;

    private long filled;

    // new orders - reserved price for fast moves of GTC bid orders in exchange mode
    private final long reserveBidPrice;

    // required for PLACE_ORDER only;
    private final OrderAction action;

    private final long uid;

    private final long timestamp;

    @Override
    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    @Override
    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public long getFilled() {
        return filled;
    }

    public void setFilled(long filled) {
        this.filled = filled;
    }

    public long getUnmatchedSize() {
        return size - filled;
    }


    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public OrderAction getAction() {
        return action;
    }

    @Override
    public long getOrderId() {
        return orderId;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public long getReserveBidPrice() {
        return reserveBidPrice;
    }

    @Override
    public int stateHash() {
        return Objects.hash(orderId, action, price, size, reserveBidPrice, filled, uid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NaivePendingOrder that = (NaivePendingOrder) o;
        return orderId == that.orderId &&
                price == that.price &&
                size == that.size &&
                filled == that.filled &&
                reserveBidPrice == that.reserveBidPrice &&
                uid == that.uid &&
                action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, price, size, filled, reserveBidPrice, action, uid);
    }

    @Override
    public String toString() {
        return "NaivePendingOrder{" +
                "orderId=" + orderId +
                ", price=" + price +
                ", size=" + size +
                ", filled=" + filled +
                ", reserveBidPrice=" + reserveBidPrice +
                ", action=" + action +
                ", uid=" + uid +
                ", timestamp=" + timestamp +
                '}';
    }
}

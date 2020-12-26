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

package exchange.core2.orderbook.events;

import java.util.List;
import java.util.Objects;

public class QueryResponseL2Data extends OrderBookResponse {

    private final List<L2Record> asks;
    private final List<L2Record> bids;

    public QueryResponseL2Data(final short resultCode, final List<L2Record> asks, final List<L2Record> bids) {
        super(resultCode);
        this.asks = asks;
        this.bids = bids;
    }

    public List<L2Record> getAsks() {
        return asks;
    }

    public List<L2Record> getBids() {
        return bids;
    }

    public static class L2Record {

        private final long price;
        private final long volume;
        private final int orders;

        public L2Record(final long price, final long volume, final int orders) {
            this.price = price;
            this.volume = volume;
            this.orders = orders;
        }

        public long getPrice() {
            return price;
        }

        public long getVolume() {
            return volume;
        }

        public int getOrders() {
            return orders;
        }

        @Override
        public String toString() {
            return "L2Record{" +
                    "price=" + price +
                    ", volume=" + volume +
                    ", orders=" + orders +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            L2Record l2Record = (L2Record) o;
            return price == l2Record.price &&
                    volume == l2Record.volume &&
                    orders == l2Record.orders;
        }

        @Override
        public int hashCode() {
            return Objects.hash(price, volume, orders);
        }
    }

    @Override
    public String toString() {
        return "QueryResponseL2Data{" +
                "asks=" + asks +
                ", bids=" + bids +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryResponseL2Data that = (QueryResponseL2Data) o;
        return Objects.equals(asks, that.asks) &&
                Objects.equals(bids, that.bids);
    }

    @Override
    public int hashCode() {
        return Objects.hash(asks, bids);
    }
}

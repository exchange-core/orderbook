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

import exchange.core2.orderbook.OrderAction;
import exchange.core2.orderbook.OrderBookEventsHelper;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

public final class OrdersBucketNaive {

    private final long price;

    private final LinkedHashMap<Long, NaivePendingOrder> entries;

    private final OrderBookEventsHelper eventsHelper;

    private long totalVolume;


    public OrdersBucketNaive(final long price, final OrderBookEventsHelper eventsHelper) {
        this.price = price;
        this.entries = new LinkedHashMap<>();
        this.totalVolume = 0;
        this.eventsHelper = eventsHelper;
    }


    public long getPrice() {
        return price;
    }

    public long getTotalVolume() {
        return totalVolume;
    }

    /**
     * Put a new order into bucket
     *
     * @param order - order
     */
    public void put(NaivePendingOrder order) {
        entries.put(order.getOrderId(), order);
        totalVolume += order.getUnmatchedSize();
    }

    /**
     * Remove order from the bucket
     *
     * @param orderId - order id
     * @throws IllegalStateException if order not found
     */
    public void remove(final long orderId) {
        final NaivePendingOrder order = entries.remove(orderId);
//        log.debug("removing order: {}", order);
        if (order == null) {
            throw new IllegalStateException("Removal of unknown order " + orderId);
        }

        totalVolume -= order.getUnmatchedSize();
    }

    /**
     * Collect a list of matching orders starting from eldest records
     * Completely matching orders will be removed, partially matched order kept in the bucked.
     *
     * @param volumeToCollect - volume to collect
     * @return - total matched volume, events, completed orders to remove
     */
    public long match(long volumeToCollect,
                      final long activeReservedBidPrice,
                      final LongConsumer orderRemover) {

        final Iterator<Map.Entry<Long, NaivePendingOrder>> iterator = entries.entrySet().iterator();

        long totalMatchingVolume = 0;

        // iterate through all orders
        while (iterator.hasNext() && volumeToCollect > 0) {
            final Map.Entry<Long, NaivePendingOrder> next = iterator.next();
            final NaivePendingOrder order = next.getValue();

            // calculate exact volume can fill for this order
            final long v = Math.min(volumeToCollect, order.getUnmatchedSize());
            totalMatchingVolume += v;

            order.setFilled(order.getFilled() + v);
            volumeToCollect -= v;
            totalVolume -= v;

            // remove from order book filled orders
            final boolean makerOrderCompleted = order.getUnmatchedSize() == 0;

            eventsHelper.appendTradeEvent(
                    order,
                    makerOrderCompleted,
                    v,
                    order.getAction() == OrderAction.ASK ? activeReservedBidPrice : order.getReserveBidPrice());

            if (makerOrderCompleted) {
                orderRemover.accept(order.getOrderId());
                iterator.remove();
            }
        }

        return totalMatchingVolume;
    }

    /**
     * Get number of orders in the bucket
     *
     * @return number of orders in the bucket
     */
    public int getNumOrders() {
        return entries.size();
    }

    /**
     * Reduce size of the order
     *
     * @param reduceSize - size to reduce (difference)
     */
    public void reduceSize(long reduceSize) {

        totalVolume -= reduceSize;
    }

    public void validate() {

        final long sum = entries.values().stream()
                .mapToLong(NaivePendingOrder::getUnmatchedSize)
                .sum();

        if (sum != totalVolume) {
            final String msg = String.format("totalVolume=%d calculated=%d", totalVolume, sum);
            throw new IllegalStateException(msg);
        }
    }

    /**
     * Inefficient method - for testing only
     *
     * @return new array with references to orders, preserving execution queue order
     */
    public List<NaivePendingOrder> getAllOrders() {
        return new ArrayList<>(entries.values());
    }


    /**
     * execute some action for each order (preserving execution queue order)
     *
     * @param consumer action consumer function
     */
    public void forEachOrder(Consumer<NaivePendingOrder> consumer) {
        entries.values().forEach(consumer);
    }

    public String dumpToSingleLine() {
        String orders = getAllOrders().stream()
                .map(o -> String.format("id%d_L%d_F%d", o.getOrderId(), o.getSize(), o.getFilled()))
                .collect(Collectors.joining(", "));

        return String.format("%d : vol:%d num:%d : %s", price, totalVolume, getNumOrders(), orders);
    }


    @Override
    public int hashCode() {
        return Objects.hash(
                price,
                Arrays.hashCode(entries.values().toArray(new NaivePendingOrder[0])));
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof OrdersBucketNaive)) return false;
        OrdersBucketNaive other = (OrdersBucketNaive) o;
        return price == other.getPrice()
                && getAllOrders().equals(other.getAllOrders());
    }

}

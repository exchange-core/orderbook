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

import exchange.core2.orderbook.*;
import exchange.core2.orderbook.util.BufferWriter;
import org.agrona.DirectBuffer;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class OrderBookNaiveImpl<S extends ISymbolSpecification> implements IOrderBook<S> {

    private static final Logger log = LoggerFactory.getLogger(OrderBookNaiveImpl.class);

    private final NavigableMap<Long, OrdersBucketNaive> askBuckets;
    private final NavigableMap<Long, OrdersBucketNaive> bidBuckets;

    private final S symbolSpec;

    private final LongObjectHashMap<NaivePendingOrder> idMap = new LongObjectHashMap<>();

    private final boolean logDebug;

    private final BufferWriter resultsBuffer;
    private final OrderBookEventsHelper eventsHelper;


    public OrderBookNaiveImpl(final S symbolSpec,
                              final boolean logDebug,
                              final BufferWriter resultsBuffer) {

        this.symbolSpec = symbolSpec;
        this.askBuckets = new TreeMap<>();
        this.bidBuckets = new TreeMap<>(Collections.reverseOrder());
        this.logDebug = logDebug;
        this.resultsBuffer = resultsBuffer;
        this.eventsHelper = new OrderBookEventsHelper(resultsBuffer, logDebug);
    }


    @Override
    public void newOrder(final DirectBuffer buffer,
                         final int offset,
                         final long timestamp) {

        final byte orderType = buffer.getByte(offset + PLACE_OFFSET_TYPE);
        final long uid = buffer.getLong(offset + PLACE_OFFSET_UID);
        final long newOrderId = buffer.getLong(offset + PLACE_OFFSET_ORDER_ID);
        final int userCookie = buffer.getInt(offset + PLACE_OFFSET_USER_COOKIE);
        final OrderAction action = OrderAction.of(buffer.getByte(offset + PLACE_OFFSET_ACTION));
        if (logDebug) log.debug("orderType={} userCookie={}", orderType, userCookie);

        resultsBuffer.appendByte(IOrderBook.COMMAND_PLACE_ORDER);
        resultsBuffer.appendLong(uid);
        resultsBuffer.appendLong(newOrderId);
        resultsBuffer.appendInt(userCookie);

        final long size = buffer.getLong(offset + PLACE_OFFSET_SIZE);
        if (size <= 0) {
            if (logDebug) log.debug("RESULT_INCORRECT_ORDER_SIZE");
            eventsHelper.appendResultCode(IOrderBook.RESULT_INCORRECT_ORDER_SIZE, true, action, false);
            return;
        }

        switch (orderType) {
            case ORDER_TYPE_GTC:
                newOrderPlaceGtc(buffer, offset, size, newOrderId, uid, action, timestamp);
                return;
            case ORDER_TYPE_IOC:
                newOrderMatchIoc(buffer, offset, size, action);
                return;
            case ORDER_TYPE_FOK_BUDGET:
                newOrderMatchFokBudget(buffer, offset, size, action);
                return;
            // TODO IOC_BUDGET and FOK support
            default:
                if (logDebug) log.debug("RESULT_UNSUPPORTED_ORDER_TYPE");
                eventsHelper.appendResultCode(IOrderBook.RESULT_UNSUPPORTED_ORDER_TYPE, true, action, false);
        }
    }


    /**
     * Place GTC order
     *
     * @param buffer
     * @param offset
     * @param size
     * @param newOrderId
     * @param uid
     * @param action
     * @param timestamp
     */
    private void newOrderPlaceGtc(final DirectBuffer buffer,
                                  final int offset,
                                  final long size,
                                  final long newOrderId,
                                  final long uid,
                                  final OrderAction action,
                                  final long timestamp) {

        final long price = buffer.getLong(offset + PLACE_OFFSET_PRICE);
        final long reserveBidPrice = buffer.getLong(offset + PLACE_OFFSET_RESERVED_BID_PRICE);

        if (logDebug) {
            log.debug("action={} price={} size={} reserveBidPrice={} newOrderId={} uid={}", action, price, size, reserveBidPrice, newOrderId, uid);
        }

        // check if order is marketable (if there are opposite matching orders)
        final SortedMap<Long, OrdersBucketNaive> subTree = subtreeForMatching(action, price);
        final long filledSize = tryMatchInstantly(size, reserveBidPrice, subTree, 0);


        final boolean completed = (filledSize == size);

        if (completed) {
            if (logDebug) log.debug("completed");
            // order was matched completely - nothing to place - can just return
            eventsHelper.appendResultCode(RESULT_SUCCESS, true, action, false);
            return;
        }

        final long nonMatchedSize = size - filledSize;

        if (idMap.containsKey(newOrderId)) {
            // duplicate order id - can match, but can not place - reject it
            eventsHelper.appendReduceEvent(price, reserveBidPrice, nonMatchedSize);
            eventsHelper.appendResultCode(RESULT_SUCCESS, true, action, true);

            log.warn("reject duplicate order id: {}", newOrderId);
            return;
        }

        // normally placing regular GTC limit order
        final NaivePendingOrder orderRecord = new NaivePendingOrder(
                newOrderId,
                price,
                size,
                filledSize,
                reserveBidPrice,
                action,
                uid,
                timestamp);

        if (logDebug) log.debug("placing into order book: {}", orderRecord);

        getBucketsByAction(action)
                .computeIfAbsent(price, p -> new OrdersBucketNaive(p, eventsHelper))
                .put(orderRecord);

        idMap.put(newOrderId, orderRecord);

        resultsBuffer.appendLong(nonMatchedSize);

        eventsHelper.appendResultCode(RESULT_SUCCESS, false, action, false);

        if (logDebug) log.debug("placed maker order: {}", orderRecord);
    }

    /**
     * Match IoC order
     *
     * @param buffer
     * @param offset
     * @param size
     * @param action
     */
    private void newOrderMatchIoc(final DirectBuffer buffer,
                                  final int offset,
                                  final long size,
                                  final OrderAction action) {

        final long price = buffer.getLong(offset + PLACE_OFFSET_PRICE);
        final long reserveBidPrice = buffer.getLong(offset + PLACE_OFFSET_RESERVED_BID_PRICE);

        if (logDebug) log.debug("action={} price={} size={} reserveBidPrice={}", action, price, size, reserveBidPrice);

        final SortedMap<Long, OrdersBucketNaive> subtree = subtreeForMatching(action, price);
        final long filledSize = tryMatchInstantly(size, reserveBidPrice, subtree, 0);

        final long rejectedSize = size - filledSize;

        if (logDebug) log.debug("rejected size: {}", rejectedSize);
        if (rejectedSize != 0) {
            // the order was not matched completely - send reject for not-completed IoC order
            eventsHelper.appendReduceEvent(price, reserveBidPrice, rejectedSize);
        }

        eventsHelper.appendResultCode(RESULT_SUCCESS, true, action, rejectedSize != 0);
    }

    /**
     * Match FoK order (budget cap)
     *
     * @param buffer
     * @param offset
     * @param size
     * @param action
     */
    private void newOrderMatchFokBudget(final DirectBuffer buffer,
                                        final int offset,
                                        final long size,
                                        final OrderAction action) {

        final SortedMap<Long, OrdersBucketNaive> fullSubtree = (action == OrderAction.ASK) ? bidBuckets : askBuckets;

        final Optional<Long> budget = checkBudgetToFill(size, fullSubtree);

        final long price = buffer.getLong(offset + PLACE_OFFSET_PRICE);
        final long reserveBidPrice = buffer.getLong(offset + PLACE_OFFSET_RESERVED_BID_PRICE);

        if (logDebug) log.debug("Budget calc: {} requested: {}", budget, price);

        final boolean canMatch = budget.isPresent() && isBudgetLimitSatisfied(action, budget.get(), price);

        if (canMatch) {
            // completely match the order
            final long filled = tryMatchInstantly(size, reserveBidPrice, fullSubtree, 0);

            if (filled != size) {
                throw new IllegalStateException("complete match is expected");
            }

        } else {
            // send reduce event if can not fill
            eventsHelper.appendReduceEvent(price, reserveBidPrice, size);
        }

        eventsHelper.appendResultCode(RESULT_SUCCESS, true, action, !canMatch);
    }

    private boolean isBudgetLimitSatisfied(final OrderAction orderAction, final long calculated, final long limit) {
        return calculated == limit || (orderAction == OrderAction.BID ^ calculated > limit);
    }

    /**
     * Check if budget can be filled
     *
     * @param size
     * @param matchingBuckets
     * @return
     */
    private Optional<Long> checkBudgetToFill(
            long size,
            final SortedMap<Long, OrdersBucketNaive> matchingBuckets) {

        long budget = 0;

        for (final OrdersBucketNaive bucket : matchingBuckets.values()) {

            final long availableSize = bucket.getTotalVolume();
            final long price = bucket.getPrice();

            if (size > availableSize) {
                size -= availableSize;
                budget += availableSize * price;
                if (logDebug) log.debug("add    {} * {} -> {}", price, availableSize, budget);
            } else {
                final long result = budget + size * price;
                if (logDebug) log.debug("return {} * {} -> {}", price, size, result);
                return Optional.of(result);
            }
        }

        if (logDebug) log.debug("not enough liquidity to fill size={}", size);

        return Optional.empty();
    }

    private SortedMap<Long, OrdersBucketNaive> subtreeForMatching(final OrderAction action, final long price) {
        return (action == OrderAction.ASK ? bidBuckets : askBuckets)
                .headMap(price, true);
    }

    /**
     * Match the order instantly to specified sorted buckets map
     * Fully matching orders are removed from orderId index
     * Should any trades occur - they sent to tradesConsumer
     *
     * @param matchingBuckets - sorted buckets map
     * @param filled          - current 'filled' value for the order
     * @return new filled size
     */
    private long tryMatchInstantly(final long takerSize,
                                   final long reserveBidPriceTaker,
                                   final SortedMap<Long, OrdersBucketNaive> matchingBuckets,
                                   long filled) {

        if (logDebug) log.debug("matchInstantly: takerSize={} filled={}", takerSize, filled);

        if (matchingBuckets.size() == 0) {
            return filled;
        }

        final Iterator<OrdersBucketNaive> iterator = matchingBuckets.values().iterator();

        while (iterator.hasNext()) {

            final OrdersBucketNaive bucket = iterator.next();
            final long sizeLeft = takerSize - filled;

            if (logDebug) log.debug("trying to match sizeLeft={} at price {}", sizeLeft, bucket.getPrice());

            filled += bucket.match(
                    sizeLeft,
                    reserveBidPriceTaker,
                    idMap::remove);

            // remove empty bucket
            if (bucket.getTotalVolume() == 0) {
                iterator.remove();
            }

            if (filled == takerSize) {
                // takerSize matched completely
                break;
            }
        }

        return filled;
    }


    @Override
    public void cancelOrder(DirectBuffer buffer, int offset) {

        final long orderId = buffer.getLong(offset + CANCEL_OFFSET_ORDER_ID);
        final long cmdUid = buffer.getLong(offset + CANCEL_OFFSET_UID);

        resultsBuffer.appendByte(IOrderBook.COMMAND_CANCEL_ORDER);
        resultsBuffer.appendLong(cmdUid);
        resultsBuffer.appendLong(orderId);

        final NaivePendingOrder order = idMap.get(orderId);
        if (order == null || order.getUid() != cmdUid) {
            // order already matched and removed from order book previously
            eventsHelper.appendResultCode(
                    RESULT_UNKNOWN_ORDER_ID,
                    true,
                    OrderAction.ASK, // arbitrary action, should be ignored
                    false);
            return;
        }

        // now can remove it
        idMap.remove(orderId);

        final NavigableMap<Long, OrdersBucketNaive> buckets = getBucketsByAction(order.getAction());
        final long price = order.getPrice();
        final OrdersBucketNaive ordersBucket = buckets.get(price);

        // remove order and whole bucket if its empty
        ordersBucket.remove(orderId);
        if (ordersBucket.getTotalVolume() == 0) {
            buckets.remove(price);
        }

        // put reduce event
        eventsHelper.appendReduceEvent(
                order.getPrice(),
                order.getReserveBidPrice(),
                order.getUnmatchedSize());

        // fill events header
        eventsHelper.appendResultCode(
                RESULT_SUCCESS,
                true,
                order.getAction(),
                true);
    }

    @Override
    public void reduceOrder(final DirectBuffer buffer, final int offset) {

        final long orderId = buffer.getLong(offset + REDUCE_OFFSET_ORDER_ID);
        final long requestedReduceSize = buffer.getLong(offset + REDUCE_OFFSET_SIZE);
        final long cmdUid = buffer.getLong(offset + REDUCE_OFFSET_UID);

        resultsBuffer.appendByte(IOrderBook.COMMAND_REDUCE_ORDER);
        resultsBuffer.appendLong(cmdUid);
        resultsBuffer.appendLong(orderId);

        final NaivePendingOrder order = idMap.get(orderId);
        if (order == null || order.getUid() != cmdUid) {
            // not found or previously matched, moved or cancelled
            eventsHelper.appendResultCode(
                    RESULT_UNKNOWN_ORDER_ID,
                    true,
                    OrderAction.ASK, // arbitrary action, should be ignored
                    false);
            return;
        }

        if (requestedReduceSize <= 0) {
            eventsHelper.appendResultCode(
                    RESULT_INCORRECT_REDUCE_SIZE,
                    false,
                    OrderAction.ASK, // arbitrary action, should be ignored
                    false);
            return;
        }

        // always > 0 (otherwise order automatically removed)
        final long remainingSize = order.getUnmatchedSize();

        // always > 0
        final long actualReduceBy = Math.min(remainingSize, requestedReduceSize);

        final NavigableMap<Long, OrdersBucketNaive> buckets = getBucketsByAction(order.getAction());
        final OrdersBucketNaive ordersBucket = buckets.get(order.getPrice());

        // send reduce event
        eventsHelper.appendReduceEvent(
                order.getPrice(),
                order.getReserveBidPrice(),
                actualReduceBy);

        final boolean canRemove = (actualReduceBy == remainingSize);

        if (canRemove) {

            // now can remove order
            idMap.remove(orderId);

            // canRemove order and whole bucket if it is empty
            ordersBucket.remove(orderId);
            if (ordersBucket.getTotalVolume() == 0) {
                buckets.remove(order.getPrice());
            }

        } else {

            order.setSize(order.getSize() - actualReduceBy);
            ordersBucket.reduceSize(actualReduceBy);
            resultsBuffer.appendLong(order.getUnmatchedSize()); // remaining unmatched size
        }

        // fill events header
        eventsHelper.appendResultCode(
                RESULT_SUCCESS,
                canRemove,
                order.getAction(),
                true);

    }

    @Override
    public void moveOrder(final DirectBuffer buffer, final int offset) {

        final long orderId = buffer.getLong(offset + MOVE_OFFSET_ORDER_ID);
        final long newPrice = buffer.getLong(offset + MOVE_OFFSET_PRICE);
        final long cmdUid = buffer.getLong(offset + MOVE_OFFSET_UID);

        resultsBuffer.appendByte(IOrderBook.COMMAND_MOVE_ORDER);
        resultsBuffer.appendLong(cmdUid);
        resultsBuffer.appendLong(orderId);

        final NaivePendingOrder order = idMap.get(orderId);
        if (order == null || order.getUid() != cmdUid) {
            // already matched, moved or cancelled
            eventsHelper.appendResultCode(
                    RESULT_UNKNOWN_ORDER_ID,
                    true,
                    OrderAction.ASK, // arbitrary action, should be ignored
                    false);
            return;
        }

        // reserved price risk check for exchange bids
        if (order.getAction() == OrderAction.BID && symbolSpec.isExchangeType() && newPrice > order.getReserveBidPrice()) {
            resultsBuffer.appendLong(order.getUnmatchedSize());
            eventsHelper.appendResultCode(
                    RESULT_MOVE_FAILED_PRICE_OVER_RISK_LIMIT,
                    false,
                    order.getAction(),
                    false);
            return;
        }

        final long price = order.getPrice();
        final NavigableMap<Long, OrdersBucketNaive> buckets = getBucketsByAction(order.getAction());
        final OrdersBucketNaive bucket = buckets.get(price);

        // take order out of the original bucket and clean bucket if its empty
        bucket.remove(orderId);

        if (bucket.getTotalVolume() == 0) {
            buckets.remove(price);
        }

        order.setPrice(newPrice);

        // try match with new price
        final long filled = tryMatchInstantly(
                order.getSize(),
                order.getReserveBidPrice(),
                subtreeForMatching(order.getAction(), newPrice),
                order.getFilled());

        final boolean takerCompleted = (filled == order.getSize());

        if (takerCompleted) {
            // order was fully matched (100% marketable) - removing from order book
            idMap.remove(orderId);

        } else {
            order.setFilled(filled);

            // if not filled completely - put it into corresponding bucket
            buckets.computeIfAbsent(newPrice, p -> new OrdersBucketNaive(p, eventsHelper))
                    .put(order);

            resultsBuffer.appendLong(order.getSize() - filled); // unmatched size
        }

        eventsHelper.appendResultCode(RESULT_SUCCESS, takerCompleted, order.getAction(), false);
    }

    /**
     * Get bucket by order action
     *
     * @param action - action
     * @return bucket - navigable map
     */
    private NavigableMap<Long, OrdersBucketNaive> getBucketsByAction(OrderAction action) {
        return action == OrderAction.ASK ? askBuckets : bidBuckets;
    }

    @Override
    public void sendL2Snapshot(final DirectBuffer buffer, final int offset) {
        final int sizeOffer = buffer.getInt(offset);
        final int maxSize = sizeOffer > 0 ? sizeOffer : Integer.MAX_VALUE;

        resultsBuffer.appendByte(IOrderBook.QUERY_ORDER_BOOK);
        if (sizeOffer <= 0) {
            resultsBuffer.appendShort(RESULT_INCORRECT_L2_SIZE_LIMIT);
            return;
        }

        int asks = 0;
        for (final OrdersBucketNaive bucket : askBuckets.values()) {
            eventsHelper.appendL2Record(bucket.getPrice(), bucket.getTotalVolume(), bucket.getNumOrders());
            if (++asks == maxSize) {
                break;
            }
        }

        int bids = 0;
        for (final OrdersBucketNaive bucket : bidBuckets.values()) {
            eventsHelper.appendL2Record(bucket.getPrice(), bucket.getTotalVolume(), bucket.getNumOrders());
            if (++bids == maxSize) {
                break;
            }
        }

        resultsBuffer.appendInt(asks);
        resultsBuffer.appendInt(bids);
        resultsBuffer.appendShort(RESULT_SUCCESS);

        //log.debug("L2 DATA: {}", resultsBuffer.prettyHexDump());
    }

    /**
     * Get order from internal map
     *
     * @param orderId - order Id
     * @return order from map
     */
    @Override
    public IOrder getOrderById(long orderId) {
        return idMap.get(orderId);
    }

    @Override
    public void fillAsks(final int size, L2MarketData data) {
        if (size == 0) {
            data.askSize = 0;
            return;
        }

        int i = 0;
        for (OrdersBucketNaive bucket : askBuckets.values()) {
            data.askPrices[i] = bucket.getPrice();
            data.askVolumes[i] = bucket.getTotalVolume();
            data.askOrders[i] = bucket.getNumOrders();
            if (++i == size) {
                break;
            }
        }
        data.askSize = i;
    }

    @Override
    public void fillBids(final int size, L2MarketData data) {
        if (size == 0) {
            data.bidSize = 0;
            return;
        }

        int i = 0;
        for (OrdersBucketNaive bucket : bidBuckets.values()) {
            data.bidPrices[i] = bucket.getPrice();
            data.bidVolumes[i] = bucket.getTotalVolume();
            data.bidOrders[i] = bucket.getNumOrders();
            if (++i == size) {
                break;
            }
        }
        data.bidSize = i;
    }

    @Override
    public int getTotalAskBuckets(final int limit) {
        return Math.min(limit, askBuckets.size());
    }

    @Override
    public int getTotalBidBuckets(final int limit) {
        return Math.min(limit, bidBuckets.size());
    }

    @Override
    public void verifyInternalState() {
        askBuckets.values().forEach(OrdersBucketNaive::validate);
        bidBuckets.values().forEach(OrdersBucketNaive::validate);
    }

    @Override
    public List<IOrder> findUserOrders(final long uid) {
        final List<IOrder> list = new ArrayList<>();
        final Consumer<OrdersBucketNaive> bucketConsumer =
                bucket -> bucket.forEachOrder(
                        order -> {
                            if (order.getUid() == uid) {
                                list.add(order);
                            }
                        });
        askBuckets.values().forEach(bucketConsumer);
        bidBuckets.values().forEach(bucketConsumer);
        return list;
    }

    @Override
    public S getSymbolSpec() {
        return symbolSpec;
    }

    @Override
    public Stream<IOrder> askOrdersStream(final boolean sorted) {
        return askBuckets.values().stream().flatMap(bucket -> bucket.getAllOrders().stream());
    }

    @Override
    public Stream<IOrder> bidOrdersStream(final boolean sorted) {
        return bidBuckets.values().stream().flatMap(bucket -> bucket.getAllOrders().stream());
    }
}

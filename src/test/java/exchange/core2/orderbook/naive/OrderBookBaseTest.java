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
import exchange.core2.orderbook.api.*;
import exchange.core2.orderbook.util.BufferWriter;
import exchange.core2.orderbook.util.CommandsEncoder;
import exchange.core2.orderbook.util.ResponseDecoder;
import exchange.core2.tests.util.L2MarketDataHelper;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableLong;
import org.hamcrest.core.IsNot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

import static exchange.core2.orderbook.IOrderBook.*;
import static exchange.core2.orderbook.OrderAction.ASK;
import static exchange.core2.orderbook.OrderAction.BID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;


/**
 * TODO tests where IOC order is not fully matched because of limit price (similar to GTC tests)
 * TODO tests where GTC order has duplicate id - rejection event should be sent
 * TODO add tests for exchange mode (moves)
 * TODO test reserve price validation for BID orders in exchange mode
 */


@RunWith(MockitoJUnitRunner.class)
public abstract class OrderBookBaseTest<S extends ISymbolSpecification> {

    private static final Logger log = LoggerFactory.getLogger(OrderBookBaseTest.class);

    protected IOrderBook<S> orderBook;

    private L2MarketDataHelper expectedState;

    protected MutableDirectBuffer responseBuffer = new ExpandableDirectByteBuffer(1024);
//    protected MutableDirectBuffer responseBuffer = new ExpandableArrayBuffer(256);

    protected BufferWriter bufferWriter = new BufferWriter(responseBuffer, 0);

    protected List<IOrder> gtcOrders = new ArrayList<>();


//    protected CommandsEncoder commandsEncoder = new CommandsEncoder(commandsBuffer);

    protected abstract IOrderBook<S> createNewOrderBook(BufferWriter bufferWriter);


    static final long INITIAL_PRICE = 81600L;
    static final long MAX_PRICE = 400000L;

    static final long UID_1 = 8320000192882333412L;
    static final long UID_2 = 8320000192882333413L;

    protected abstract S getCoreSymbolSpec();


    @Before
    public void before() {

        orderBook = createNewOrderBook(bufferWriter);
        orderBook.verifyInternalState();

        placeOrder(ORDER_TYPE_GTC, -1L, UID_2, INITIAL_PRICE, 0L, 13L, ASK);
        cancel(-1L, UID_2);

        gtcOrders.add(new NaivePendingOrder(1L, 81600L, 100L, 0, 0L, ASK, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(2L, 81599L, 50L, 0, 0L, ASK, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(3L, 81599L, 25L, 0, 0L, ASK, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(8L, 201000L, 28L, 0, 0L, ASK, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(9L, 201000L, 32L, 0, 0L, ASK, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(10L, 200954L, 10L, 0, 0L, ASK, UID_1, 1L));

        gtcOrders.add(new NaivePendingOrder(4L, 81593L, 40L, 0, 82001L, BID, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(5L, 81590L, 20L, 0, 82004L, BID, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(6L, 81590L, 1L, 0, 82020L, BID, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(7L, 81200L, 20L, 0, 82044L, BID, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(11L, 10000L, 12L, 0, 12000L, BID, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(12L, 10000L, 1L, 0, 12000L, BID, UID_1, 1L));
        gtcOrders.add(new NaivePendingOrder(13L, 9136L, 2L, 0, 12000L, BID, UID_1, 1L));

        gtcOrders.forEach(this::placeOrderGTC);

        expectedState = new L2MarketDataHelper(
                new L2MarketData(
                        new long[]{81599, 81600, 200954, 201000},
                        new long[]{75, 100, 10, 60},
                        new long[]{2, 1, 1, 2},
                        new long[]{81593, 81590, 81200, 10000, 9136},
                        new long[]{40, 21, 20, 13, 2},
                        new long[]{1, 2, 1, 2, 1}
                ));

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(25);
        assertThat(expectedState.build(), is(snapshot));
    }


    /**
     * In the end of each test remove all orders by sending market orders wit proper size.
     * Check order book is empty.
     */
    @After
    public void after() {
        clearOrderBook();
    }

    protected void clearOrderBook() {
        orderBook.verifyInternalState();
        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(Integer.MAX_VALUE);

        // match all asks
        long askSum = Arrays.stream(snapshot.askVolumes).sum();
        if (askSum > 0) {
            placeOrder(ORDER_TYPE_IOC, 100000000000L, -1, MAX_PRICE, MAX_PRICE, askSum, BID);

//        log.debug("{}", orderBook.getL2MarketDataSnapshot(Integer.MAX_VALUE).dumpOrderBook());

            orderBook.verifyInternalState();
        }

        // match all bids
        long bidSum = Arrays.stream(snapshot.bidVolumes).sum();
        if (bidSum > 0) {
            placeOrder(ORDER_TYPE_IOC, 100000000001L, -2, 1, 0, bidSum, ASK);
        }
//        log.debug("{}", orderBook.getL2MarketDataSnapshot(Integer.MAX_VALUE).dumpOrderBook());

        assertThat(orderBook.getL2MarketDataSnapshot(Integer.MAX_VALUE).askSize, is(0));
        assertThat(orderBook.getL2MarketDataSnapshot(Integer.MAX_VALUE).bidSize, is(0));

        orderBook.verifyInternalState();
    }


    @Test
    public void shouldInitializeWithoutErrors() {

    }

    // ------------------------ NO TRADES -----------------------

    @Test
    public void shouldReceiveL2Data() {
        // verify full data
        QueryResponseL2Data response = queryL2Data();
        expectedState.checkL2Data(response);

        // request BBO
        QueryResponseL2Data response1 = queryL2Data(1, RESULT_SUCCESS);
        assertThat(response1.getAsks(), is(response.getAsks().subList(0, 1)));
        assertThat(response1.getBids(), is(response.getBids().subList(0, 1)));
    }

    @Test
    public void shouldReturnErrorForIncorrectL2DataQuery() {
        queryL2Data(0, RESULT_INCORRECT_L2_SIZE_LIMIT);
        queryL2Data(-1, RESULT_INCORRECT_L2_SIZE_LIMIT);
        queryL2Data(Integer.MIN_VALUE, RESULT_INCORRECT_L2_SIZE_LIMIT);
    }

    /**
     * Just place few GTC orders
     */
    @Test
    public void shouldPlaceGtcOrder() {

        placeOrder(ORDER_TYPE_GTC, 93L, UID_1, 81598L, 0, 1L, ASK,
                verifyRemainingSize(1L),
                verifyOrderNotCompleted());

        //log.debug("{}", dumpOrderBook(snapshot));
    }


    /**
     * Just place few GTC orders
     */
    @Test
    public void shouldAddGtcOrders() {

        placeOrder(ORDER_TYPE_GTC, 93L, UID_1, 81598L, 0, 1L, ASK,
                verifyRemainingSize(1L),
                verifyOrderNotCompleted());
        expectedState.insertAsk(0, 81598L, 1L);

        placeOrder(ORDER_TYPE_GTC, 94L, UID_1, 81594, MAX_PRICE, 9_000_000_000L, BID,
                verifyRemainingSize(9_000_000_000L),
                verifyOrderNotCompleted());
        expectedState.insertBid(0, 81594L, 9_000_000_000L);

        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
        orderBook.verifyInternalState();

        placeOrder(ORDER_TYPE_GTC, 95L, UID_1, 130000L, 0L, 13_000_000_000L, ASK,
                verifyRemainingSize(13_000_000_000L),
                verifyOrderNotCompleted());
        expectedState.insertAsk(3, 130000L, 13_000_000_000L);

        placeOrder(ORDER_TYPE_GTC, 96L, UID_1, 1000L, MAX_PRICE, 4L, BID,
                verifyRemainingSize(4L),
                verifyOrderNotCompleted());
        expectedState.insertBid(6, 1000L, 4L);

        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
        orderBook.verifyInternalState();

        //log.debug("{}", dumpOrderBook(snapshot));
    }

    /**
     * Ignore order with duplicate orderId
     */
    @Test
    public void shouldIgnoredDuplicateOrder() {

        CommandResponse res = placeOrder(ORDER_TYPE_GTC, 1L, UID_1, 81600L, 0L, 100L, ASK,
                verifyEmptyRemainingSize(),
                verifyOrderCompleted(),
                verifyNoTradeEvents());

        // just confirm reduce event exists, rest of verifications are done automatically
        assertTrue(res.getReduceEventOpt().isPresent());
    }

    /**
     * Reject order with duplicate orderId
     */
    @Test
    public void shouldRejectIncorrectOrderSize() {

        placeOrder(ORDER_TYPE_GTC, 1L, UID_1, 86600L, 86600L, 0L, BID,
                RESULT_INCORRECT_ORDER_SIZE,
                verifyEmptyRemainingSize(),
                verifyOrderCompleted(),
                verifyNoTradeEvents());
    }

    @Test
    public void shouldRejectUnsupportedType() {
        placeOrder(ORDER_TYPE_IOC_BUDGET, 1L, UID_1, 86600L, 86600L, 182739L, BID,
                RESULT_UNSUPPORTED_ORDER_TYPE,
                verifyEmptyRemainingSize(),
                verifyOrderCompleted(),
                verifyNoTradeEvents());

        placeOrder(ORDER_TYPE_FOK, 1L, UID_1, 6600L, 0L, 48823L, ASK,
                RESULT_UNSUPPORTED_ORDER_TYPE,
                verifyEmptyRemainingSize(),
                verifyOrderCompleted(),
                verifyNoTradeEvents());
    }


    /**
     * Cancel existing order
     */
    @Test
    public void shouldCancelBidOrder() {

        // cancel bid order
        CommandResponse res = cancel(5L, UID_1);

        expectedState.setBidVolume(1, 1L).decrementBidOrdersNum(1);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));

        verifySingleReduceEvent(res, UID_1, 5L, BID, 81590L, 82004L, 20L, true);
    }


    @Test
    public void shouldCancelAskOrder() {
        // cancel ask order
        CommandResponse res = cancel(2L, UID_1);

        expectedState.setAskVolume(0, 25L).decrementAskOrdersNum(0);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));

        verifySingleReduceEvent(res, UID_1, 2L, ASK, 81599L, 0L, 50L, true);
    }

    @Test
    public void shouldReduceBidOrder() {

        // reduce bid order
        CommandResponse res = reduce(5L, UID_1, 3L);

        expectedState.decrementBidVolume(1, 3L);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));

        verifySingleReduceEvent(res, UID_1, 5L, BID, 81590L, 82004L, 3L, false);
    }


    @Test
    public void shouldReduceAskOrder() {
        // reduce ask order - will effectively remove order
        CommandResponse res = reduce(1L, UID_1, 300L);

        expectedState.removeAsk(1);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));

        verifySingleReduceEvent(res, UID_1, 1L, ASK, 81600L, 0L, 100L, true);
    }

    @Test
    public void shouldReduceOrderByMaxSize() {
        // reduce ask order by max possible value - will effectively remove order
        CommandResponse res = reduce(1L, UID_1, Long.MAX_VALUE);

        expectedState.removeAsk(1);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));

        verifySingleReduceEvent(res, UID_1, 1L, ASK, 81600L, 0L, 100L, true);
    }

    @Test
    public void shouldNotReduceByNegativeOrZero() {

        // zero
        CommandResponse res = reduce(4, UID_1, 0L, RESULT_INCORRECT_REDUCE_SIZE);
        verifyNoEvents(res);

        // negative
        res = reduce(8, UID_1, -1L, RESULT_INCORRECT_REDUCE_SIZE);
        verifyNoEvents(res);

        // big negative
        res = reduce(8, UID_1, Long.MIN_VALUE, RESULT_INCORRECT_REDUCE_SIZE);
        verifyNoEvents(res);

        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }


    /**
     * When cancelling an order, order book implementation should also remove a bucket if no orders left for specified price
     */
    @Test
    public void shouldCancelOrderAndEmptyBucket() {
        CommandResponse res = cancel(2L, UID_1);

        verifySingleReduceEvent(res, UID_1, 2L, ASK, 81599L, 0L, 50L, true);

        //log.debug("{}", orderBook.getL2MarketDataSnapshot(10).dumpOrderBook());

        CommandResponse res2 = cancel(3L, UID_1);

        verifySingleReduceEvent(res2, UID_1, 3L, ASK, 81599L, 0L, 25L, true);

        expectedState.removeAsk(0);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }

    @Test
    public void shouldReturnErrorWhenCancelUnknownOrder() {

        CommandResponse res = cancel(5291L, UID_1, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }

    @Test
    public void shouldReturnErrorWhenCancelOtherUserOrder() {

        CommandResponse res = cancel(3L, UID_2, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }

    @Test
    public void shouldReturnErrorWhenMoveOtherUserOrder() {

        CommandResponse res = move(2L, UID_2, 100L, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }


    @Test
    public void shouldReturnErrorWhenMoveUnknownOrder() {

        CommandResponse res = move(2433L, UID_1, 300L, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }

    @Test
    public void shouldReturnErrorWhenMoveOverBidPriceLimit() {

        // bid price hold = 82044L
        CommandResponse res = move(7L, UID_1, 82045L, RESULT_MOVE_FAILED_PRICE_OVER_RISK_LIMIT);
        verifyNoEvents(res);

        // not completed if such errors is received
        assertFalse(res.isOrderCompleted());
        assertThat(res.getTakerAction(), is(BID));

        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }

    @Test
    public void shouldReturnErrorWhenReducingUnknownOrder() {

        CommandResponse res = reduce(329813L, UID_1, 1L, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }


    @Test
    public void shouldReturnErrorWhenReducingOtherUserOrder() {

        CommandResponse res = reduce(8L, UID_2, 3L, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }

    @Test
    public void shouldMoveOrderIntoExistingBucket() {
        CommandResponse res = move(7L, UID_1, 81590L);
        verifyNoEvents(res);

        // moved
        L2MarketData expected = expectedState.setBidVolume(1, 41L).incrementBidOrdersNum(1).removeBid(2).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));
    }


    @Test
    public void shouldMoveOrderNewBucket() {
        CommandResponse res = move(7L, UID_1, 81594L);
        verifyNoEvents(res);

        // moved
        L2MarketData expected = expectedState.removeBid(2).insertBid(0, 81594L, 20L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));
    }

    // ------------------------ MATCHING TESTS -----------------------

    @Test
    public void shouldMatchIocOrderPartialBBO() {

        // size=10
        CommandResponse res = placeOrder(ORDER_TYPE_IOC, 123L, UID_2, 1L, 0L, 10L, ASK);

        // best bid matched
        L2MarketData expected = expectedState.setBidVolume(0, 30L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, ASK, true,
                new TradeEvent(4L, UID_1, 81593L, 82001L, 10L, false));

        assertNotNull(orderBook.getOrderById(4L));
    }


    @Test
    public void shouldMatchIocOrderFullBBO() {

        // size=40
        CommandResponse res = placeOrder(ORDER_TYPE_IOC, 123L, UID_2, 1L, 0L, 40L, ASK);

        // best bid matched
        L2MarketData expected = expectedState.removeBid(0).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, ASK, true,
                new TradeEvent(4L, UID_1, 81593L, 82001L, 40L, true));

        // check order is removed from map
        assertNull(orderBook.getOrderById(4L));
    }

    @Test
    public void shouldMatchIocOrderWithTwoLimitOrdersPartial() {

        // size=41
        CommandResponse res = placeOrder(ORDER_TYPE_IOC, 123L, UID_2, 1L, 0L, 41L, ASK);

        // best bid matched
        L2MarketData expected = expectedState.removeBid(0).setBidVolume(0, 20L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, ASK, true,
                new TradeEvent(4L, UID_1, 81593L, 82001L, 40L, true),
                new TradeEvent(5L, UID_1, 81590L, 82004L, 1L, false));

        // check orders are removed from map
        assertNull(orderBook.getOrderById(4L));
        assertNotNull(orderBook.getOrderById(5L));
    }

    @Test
    public void shouldMatchIocOrderMultipleOrders() {

        // size=175
        CommandResponse res = placeOrder(ORDER_TYPE_IOC, 123L, UID_2, MAX_PRICE, MAX_PRICE, 175L, BID);

        // asks matched
        L2MarketData expected = expectedState.removeAsk(0).removeAsk(0).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, BID, true,
                new TradeEvent(2L, UID_1, 81599L, MAX_PRICE, 50L, true),
                new TradeEvent(3L, UID_1, 81599L, MAX_PRICE, 25L, true),
                new TradeEvent(1L, UID_1, 81600L, MAX_PRICE, 100L, true));

        // check orders are removed from map
        assertNull(orderBook.getOrderById(1L));
        assertNull(orderBook.getOrderById(2L));
        assertNull(orderBook.getOrderById(3L));
    }


    @Test
    public void shouldMatchIocOrderWithRejection() {

        // size=270
        CommandResponse res = placeOrder(ORDER_TYPE_IOC, 123L, UID_2, MAX_PRICE, MAX_PRICE + 1, 270L, BID);

        // all asks matched
        L2MarketData expected = expectedState.removeAllAsks().build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        // 6 trades generated, rejection with size=25 left unmatched
        verifyTradeEvents(
                res, UID_2, 123L, BID, true,
                new ReduceEvent(25L, MAX_PRICE, MAX_PRICE + 1),
                new TradeEvent(2L, UID_1, 81599L, MAX_PRICE + 1, 50L, true),
                new TradeEvent(3L, UID_1, 81599L, MAX_PRICE + 1, 25L, true),
                new TradeEvent(1L, UID_1, 81600L, MAX_PRICE + 1, 100L, true),
                new TradeEvent(10L, UID_1, 200954L, MAX_PRICE + 1, 10L, true),
                new TradeEvent(8L, UID_1, 201000L, MAX_PRICE + 1, 28L, true),
                new TradeEvent(9L, UID_1, 201000L, MAX_PRICE + 1, 32L, true));
    }

    // ---------------------- FOK BUDGET ORDERS ---------------------------

    @Test
    public void shouldRejectFokBidOrderOutOfBudget() {

        long size = 180L;
        long buyBudget = expectedState.aggregateBuyBudget(size) - 1;
        assertThat(buyBudget, is(81599L * 75L + 81600L * 100L + 200954L * 5L - 1));

        CommandResponse res = placeOrder(ORDER_TYPE_FOK_BUDGET, 123L, UID_2, buyBudget, buyBudget, size, BID);

        L2MarketData expected = expectedState.build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        // no trades generated, rejection with full size unmatched
        verifyTradeEvents(
                res, UID_2, 123L, BID, true,
                new ReduceEvent(size, buyBudget, buyBudget));
    }

    @Test
    public void shouldMatchFokBidOrderExactBudget() {

        long size = 180L;
        long buyBudget = expectedState.aggregateBuyBudget(size);
        assertThat(buyBudget, is(81599L * 75L + 81600L * 100L + 200954L * 5L));

        CommandResponse res = placeOrder(ORDER_TYPE_FOK_BUDGET, 123L, UID_2, buyBudget, buyBudget, size, BID);

        L2MarketData expected = expectedState.removeAsk(0).removeAsk(0).setAskVolume(0, 5L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, BID, true,
                new TradeEvent(2L, UID_1, 81599L, buyBudget, 50L, true),
                new TradeEvent(3L, UID_1, 81599L, buyBudget, 25L, true),
                new TradeEvent(1L, UID_1, 81600L, buyBudget, 100L, true),
                new TradeEvent(10L, UID_1, 200954L, buyBudget, 5L, false));

    }

    @Test
    public void shouldMatchFokBidOrderExtraBudget() {

        long size = 176L;
        long buyBudget = expectedState.aggregateBuyBudget(size) + 1;
        assertThat(buyBudget, is(81599L * 75L + 81600L * 100L + 200954L + 1L));

        CommandResponse res = placeOrder(ORDER_TYPE_FOK_BUDGET, 123L, UID_2, buyBudget, buyBudget, size, BID);

        L2MarketData expected = expectedState.removeAsk(0).removeAsk(0).setAskVolume(0, 9L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, BID, true,
                new TradeEvent(2L, UID_1, 81599L, buyBudget, 50L, true),
                new TradeEvent(3L, UID_1, 81599L, buyBudget, 25L, true),
                new TradeEvent(1L, UID_1, 81600L, buyBudget, 100L, true),
                new TradeEvent(10L, UID_1, 200954L, buyBudget, 1L, false));
    }


    @Test
    public void shouldRejectFokBidOrderOutOfLiquidity() {

        long size = 1000000000000000L;
        long buyBudget = 1000000000000000L;

        CommandResponse res = placeOrder(ORDER_TYPE_FOK_BUDGET, 123L, UID_2, buyBudget, buyBudget, size, BID);

        L2MarketData expected = expectedState.build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        // no trades generated, rejection with full size unmatched
        verifyTradeEvents(
                res, UID_2, 123L, BID, true,
                new ReduceEvent(size, buyBudget, buyBudget));
    }

    @Test
    public void shouldRejectFokAskOrderBelowExpectation() {

        long size = 60L;
        long sellExpectation = expectedState.aggregateSellExpectation(size) + 1;
        assertThat(sellExpectation, is(81593L * 40L + 81590L * 20L + 1));

        CommandResponse res = placeOrder(ORDER_TYPE_FOK_BUDGET, 123L, UID_2, sellExpectation, 0L, size, ASK);

        L2MarketData expected = expectedState.build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        // no trades generated, rejection with full size unmatched
        verifyTradeEvents(
                res, UID_2, 123L, ASK, true,
                new ReduceEvent(size, sellExpectation, 0L));
    }

    @Test
    public void shouldMatchFokAskOrderExactExpectation() {

        long size = 60L;
        long sellExpectation = expectedState.aggregateSellExpectation(size);
        assertThat(sellExpectation, is(81593L * 40L + 81590L * 20L));

        CommandResponse res = placeOrder(ORDER_TYPE_FOK_BUDGET, 123L, UID_2, sellExpectation, 0L, size, ASK);

        L2MarketData expected = expectedState.removeBid(0).setBidVolume(0, 1).decrementBidOrdersNum(0).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, ASK, true,
                new TradeEvent(4L, UID_1, 81593L, 82001L, 40L, true),
                new TradeEvent(5L, UID_1, 81590L, 82004L, 20L, true));
    }

    @Test
    public void shouldMatchFokAskOrderExtraBudget() {

        long size = 61L;
        long sellExpectation = expectedState.aggregateSellExpectation(size) - 1;
        assertThat(sellExpectation, is(81593L * 40L + 81590L * 21L - 1));

        CommandResponse res = placeOrder(ORDER_TYPE_FOK_BUDGET, 123L, UID_2, sellExpectation, 0L, size, ASK);

        L2MarketData expected = expectedState.removeBid(0).removeBid(0).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, ASK, true,
                new TradeEvent(4L, UID_1, 81593L, 82001L, 40L, true),
                new TradeEvent(5L, UID_1, 81590L, 82004L, 20L, true),
                new TradeEvent(6L, UID_1, 81590L, 82020L, 1L, true));
    }

    @Test
    public void shouldRejectFokAskOrderOutOfLiquidity() {

        long size = 10000000000L;
        long sellExpectation = 1L;

        CommandResponse res = placeOrder(ORDER_TYPE_FOK_BUDGET, 123L, UID_2, sellExpectation, 0L, size, ASK);

        L2MarketData expected = expectedState.build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        // no trades generated, rejection with full size unmatched
        verifyTradeEvents(
                res, UID_2, 123L, ASK, true,
                new ReduceEvent(size, sellExpectation, 0L));
    }

    // MARKETABLE GTC ORDERS

    @Test
    public void shouldFullyMatchMarketableGtcOrder() {

        // size=1
        CommandResponse res = placeOrder(ORDER_TYPE_GTC, 123L, UID_2, 81599L, MAX_PRICE, 1L, BID);

        // best ask partially matched
        L2MarketData expected = expectedState.setAskVolume(0, 74L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, BID, true,
                new TradeEvent(2L, UID_1, 81599L, MAX_PRICE, 1L, false));
    }

    @Test
    public void shouldPartiallyMatchMarketableGtcOrderAndPlace() {

        // size=77
        CommandResponse res = placeOrder(ORDER_TYPE_GTC, 123L, UID_2, 81599L, MAX_PRICE, 77L, BID);

        // best asks fully matched, limit bid order placed
        L2MarketData expected = expectedState.removeAsk(0).insertBid(0, 81599L, 2L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, BID, false,
                new TradeEvent(2L, UID_1, 81599L, MAX_PRICE, 50L, true),
                new TradeEvent(3L, UID_1, 81599L, MAX_PRICE, 25L, true));
    }

    //
    @Test
    public void shouldFullyMatchMarketableGtcOrder2Prices() {

        // size=77
        CommandResponse res = placeOrder(ORDER_TYPE_GTC, 123L, UID_2, 81600L, MAX_PRICE, 77L, BID);

        // best asks fully matched, limit bid order placed
        L2MarketData expected = expectedState.removeAsk(0).setAskVolume(0, 98L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                res, UID_2, 123L, BID, true,
                new TradeEvent(2L, UID_1, 81599L, MAX_PRICE, 50L, true),
                new TradeEvent(3L, UID_1, 81599L, MAX_PRICE, 25L, true),
                new TradeEvent(1L, UID_1, 81600L, MAX_PRICE, 2L, false));
    }


    @Test
    public void shouldFullyMatchMarketableGtcOrderWithAllLiquidity() {

        // size=1000
        CommandResponse res = placeOrder(ORDER_TYPE_GTC, 123L, UID_2, 220000L, MAX_PRICE + 1, 1000L, BID);

        // best asks fully matched, limit bid order placed
        L2MarketData expected = expectedState.removeAllAsks().insertBid(0, 220000L, 755L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        // trades only, rejection not generated for limit order
        verifyTradeEvents(
                res, UID_2, 123L, BID, false,
                new TradeEvent(2L, UID_1, 81599L, MAX_PRICE + 1, 50L, true),
                new TradeEvent(3L, UID_1, 81599L, MAX_PRICE + 1, 25L, true),
                new TradeEvent(1L, UID_1, 81600L, MAX_PRICE + 1, 100L, true),
                new TradeEvent(10L, UID_1, 200954L, MAX_PRICE + 1, 10L, true),
                new TradeEvent(8L, UID_1, 201000L, MAX_PRICE + 1, 28L, true),
                new TradeEvent(9L, UID_1, 201000L, MAX_PRICE + 1, 32L, true));
    }

    // Move GTC order to marketable price
    @Test
    public void shouldMoveOrderFullyMatchAsMarketable() {

        // add new order and check it is there
        CommandResponse resPlace = placeOrder(ORDER_TYPE_GTC, 83L, UID_2, 81200L, MAX_PRICE, 20L, BID);
        verifyNoEvents(resPlace);

        L2MarketData expected = expectedState.setBidVolume(2, 40L).incrementBidOrdersNum(2).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        // move to marketable price area
        CommandResponse resMove = move(83L, UID_2, 81602L);

        // moved
        expected = expectedState.setBidVolume(2, 20L).decrementBidOrdersNum(2).setAskVolume(0, 55L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                resMove, UID_2, 83L, BID, true,
                new TradeEvent(2L, UID_1, 81599L, MAX_PRICE, 20L, false));
    }


    @Test
    public void shouldMoveOrderFullyMatchAsMarketable2Prices() {

        CommandResponse resPlace = placeOrder(ORDER_TYPE_GTC, 83L, UID_2, 81594L, MAX_PRICE, 100L, BID);
        verifyNoEvents(resPlace);

        // move to marketable zone
        CommandResponse resMove = move(83L, UID_2, 81600L);

        // moved
        L2MarketData expected = expectedState.removeAsk(0).setAskVolume(0, 75L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                resMove, UID_2, 83L, BID, true,
                new TradeEvent(2L, UID_1, 81599L, MAX_PRICE, 50L, true),
                new TradeEvent(3L, UID_1, 81599L, MAX_PRICE, 25L, true),
                new TradeEvent(1L, UID_1, 81600L, MAX_PRICE, 25L, false));
    }


    @Test
    public void shouldMoveOrderMatchesAllLiquidity() {

        CommandResponse resPlace = placeOrder(ORDER_TYPE_GTC, 83L, UID_2, 81594L, MAX_PRICE, 246L, BID);
        verifyNoEvents(resPlace);

        // move to marketable zone
        CommandResponse resMove = move(83L, UID_2, 201000L);

        // moved
        L2MarketData expected = expectedState.removeAllAsks().insertBid(0, 201000L, 1L).build();
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expected));

        verifyTradeEvents(
                resMove, UID_2, 83L, BID, false,
                new TradeEvent(2L, UID_1, 81599L, MAX_PRICE, 50L, true),
                new TradeEvent(3L, UID_1, 81599L, MAX_PRICE, 25L, true),
                new TradeEvent(1L, UID_1, 81600L, MAX_PRICE, 100L, true),
                new TradeEvent(10L, UID_1, 200954L, MAX_PRICE, 10L, true),
                new TradeEvent(8L, UID_1, 201000L, MAX_PRICE, 28L, true),
                new TradeEvent(9L, UID_1, 201000L, MAX_PRICE, 32L, true));
    }

    @Test
    public void shouldFindUserOrders() {
        // same orders as placed initially (ignore ordering)
        assertThat(new HashSet<>(orderBook.findUserOrders(UID_1)), is(new HashSet<>(gtcOrders)));

        // no orders for UID2
        assertTrue(orderBook.findUserOrders(UID_2).isEmpty());
    }


//    @Test
//    public void multipleCommandsKeepInternalStateTest() {
//
//        int tranNum = 25000;
//
//        final IOrderBook localOrderBook = createNewOrderBook();
//        localOrderBook.validateInternalState();
//
//        TestOrdersGenerator.GenResult genResult = TestOrdersGenerator.generateCommands(
//                tranNum,
//                200,
//                6,
//                TestOrdersGenerator.UID_PLAIN_MAPPER,
//                0,
//                false,
//                false,
//                TestOrdersGenerator.createAsyncProgressLogger(tranNum),
//                348290254);
//
//        genResult.getCommands().forEach(cmd -> {
//            cmd.orderId += 100; // TODO set start id
//            //log.debug("{}",  cmd);
//            CommandResultCode commandResultCode = IOrderBook.processCommand(localOrderBook, cmd);
//            assertThat(commandResultCode, is(SUCCESS));
//            localOrderBook.validateInternalState();
//        });
//
//    }

    // ------------------------------- UTILITY METHODS --------------------------

    private final void placeOrderGTC(final IOrder order) {
        placeOrder(ORDER_TYPE_GTC, order.getOrderId(), order.getUid(), order.getPrice(), order.getReserveBidPrice(), order.getSize(), order.getAction());
    }

    @SafeVarargs
    private final CommandResponsePlace placeOrder(final byte type,
                                                  final long orderId,
                                                  final long uid,
                                                  final long price,
                                                  final long reservedBidPrice,
                                                  final long size,
                                                  final OrderAction action,
                                                  final Consumer<CommandResponse>... responseValidators) {

        return placeOrder(type, orderId, uid, price, reservedBidPrice, size, action, RESULT_SUCCESS, responseValidators);
    }

    @SafeVarargs
    private final CommandResponsePlace placeOrder(final byte type,
                                                  final long orderId,
                                                  final long uid,
                                                  final long price,
                                                  final long reservedBidPrice,
                                                  final long size,
                                                  final OrderAction action,
                                                  final short expectedResultCode,
                                                  final Consumer<CommandResponse>... responseValidators) {

        bufferWriter.reset();
        final int userCookie = Objects.hash(uid, orderId, price, size);
        final MutableDirectBuffer buffer = CommandsEncoder.placeOrder(type, orderId, uid, price, reservedBidPrice, size, action, userCookie);
        orderBook.newOrder(buffer, 0, 12345678L);

        final CommandResponsePlace response = (CommandResponsePlace) ResponseDecoder.readResult(responseBuffer, bufferWriter.getWriterPosition());

        assertThat(response.getResultCode(), is(expectedResultCode));

        assertThat(response.getTakerAction(), is(action));
        assertThat(response.getOrderId(), is(orderId));
        assertThat(response.getUid(), is(uid));
        assertThat(response.getUserCookie(), is(userCookie));

        Arrays.stream(responseValidators).forEach(v -> v.accept(response));

        final MutableLong totalVolumeInEvents = new MutableLong();

        final Optional<ReduceEvent> reduceEventOpt = response.getReduceEventOpt();
        final List<TradeEvent> trades = response.getTrades();

        reduceEventOpt.ifPresent(reduceEvent -> {
            assertThat(reduceEvent.getPrice(), is(price));
            assertThat(reduceEvent.getReservedBidPrice(), is(action == BID ? reservedBidPrice : 0L));
            assertTrue(reduceEvent.getReducedVolume() > 0);
            totalVolumeInEvents.addAndGet(reduceEvent.getReducedVolume());
        });

        trades.forEach(trade -> {
            assertThat(trade.getMakerOrderId(), IsNot.not(0L));
            assertThat(trade.getMakerUid(), IsNot.not(0L));
            assertTrue(trade.getReservedBidPrice() > 0);
            assertTrue(trade.getTradePrice() > 0);
            assertTrue(trade.getTradeVolume() > 0);
            totalVolumeInEvents.addAndGet(trade.getTradeVolume());
        });

        if (response.isSuccessful()) {

            if (type != ORDER_TYPE_GTC) {

                // check total trades+reduce size in events
                assertThat(totalVolumeInEvents.get(), is(size));

                // sending  IoC or FoK order triggers either reduce or/and trades
                assertTrue(reduceEventOpt.isPresent() || trades.size() != 0);
            }

        } else {

            // for failed orders - no events expected (! risk processor should apply same validations)
            assertFalse(reduceEventOpt.isPresent());
            assertTrue(trades.isEmpty());
        }

        orderBook.verifyInternalState();
        return response;
    }

    // cancel
    @SafeVarargs
    private final CommandResponseCancel cancel(final long orderId,
                                               final long uid,
                                               final Consumer<CommandResponse>... responseValidators) {

        return cancel(orderId, uid, RESULT_SUCCESS);
    }

    @SafeVarargs
    private final CommandResponseCancel cancel(final long orderId,
                                               final long uid,
                                               final short expectedResultCode,
                                               final Consumer<CommandResponse>... responseValidators) {

        bufferWriter.reset();
        orderBook.cancelOrder(CommandsEncoder.cancel(orderId, uid), 0);
        CommandResponseCancel response = (CommandResponseCancel) readResultAndVerifyInternalState(expectedResultCode);

        // no trades
        assertTrue(response.getTrades().isEmpty());
        assertTrue(response.isOrderCompleted());

        // reduce event when SUCCESS result
        assertThat(response.getReduceEventOpt().isPresent(), is(response.isSuccessful()));

        assertThat(response.getOrderId(), is(orderId));
        assertThat(response.getUid(), is(uid));

        if (!response.isSuccessful()) {
            // ASK for any error
            assertThat(response.getTakerAction(), is(ASK));
        }

        return response;
    }

    // reduce

    protected CommandResponseReduce reduce(final long orderId,
                                           final long uid,
                                           final long size) {

        return reduce(orderId, uid, size, RESULT_SUCCESS);

    }

    protected CommandResponseReduce reduce(final long orderId,
                                           final long uid,
                                           final long size,
                                           final short expectedResultCode) {

        bufferWriter.reset();
        orderBook.reduceOrder(CommandsEncoder.reduce(orderId, uid, size), 0);
        return (CommandResponseReduce) readResultAndVerifyInternalState(expectedResultCode);
    }

    // move

    protected CommandResponseMove move(final long orderId,
                                       final long uid,
                                       final long price) {

        return move(orderId, uid, price, RESULT_SUCCESS);
    }

    protected CommandResponseMove move(final long orderId,
                                       final long uid,
                                       final long price,
                                       final short expectedResultCode) {

        bufferWriter.reset();
        orderBook.moveOrder(CommandsEncoder.move(orderId, uid, price), 0);
        return (CommandResponseMove) readResultAndVerifyInternalState(expectedResultCode);
    }

    protected QueryResponseL2Data queryL2Data() {
        return queryL2Data(Integer.MAX_VALUE, RESULT_SUCCESS);
    }

    protected QueryResponseL2Data queryL2Data(final int recordsLimit,
                                              final short expectedResultCode) {

        bufferWriter.reset();
        orderBook.sendL2Snapshot(CommandsEncoder.L2DataQuery(recordsLimit), 0);
        return (QueryResponseL2Data) readResultAndVerifyInternalState(expectedResultCode);
    }


    private OrderBookResponse readResultAndVerifyInternalState(short expectedResultCode) {
        final OrderBookResponse response = ResponseDecoder.readResult(responseBuffer, bufferWriter.getWriterPosition());
        assertThat(response.getResultCode(), is(expectedResultCode));

        orderBook.verifyInternalState();
        return response;
    }

    private void verifyNoEvents(final CommandResponse res) {

        assertFalse(res.getReduceEventOpt().isPresent());
        assertTrue(res.getTrades().isEmpty());
    }

    private void verifySingleReduceEvent(final CommandResponse res,
                                         final long uid,
                                         final long orderId,
                                         final OrderAction action,
                                         final long price,
                                         final long reservedBidPrice,
                                         final long reducedVolume,
                                         final boolean completed) {

        assertThat(res.getUid(), is(uid));
        assertThat(res.getOrderId(), is(orderId));
        assertThat(res.isOrderCompleted(), is(completed));
        assertThat(res.getTakerAction(), is(action));

        assertTrue(res.getTrades().isEmpty());

        assertTrue(res.getReduceEventOpt().isPresent());
        final ReduceEvent reduceEvent = res.getReduceEventOpt().get();

        assertThat(reduceEvent.getPrice(), is(price));
        assertThat(reduceEvent.getReservedBidPrice(), is(reservedBidPrice));
        assertThat(reduceEvent.getReducedVolume(), is(reducedVolume));
    }

    private void verifyTradeEvents(final CommandResponse res,
                                   final long uid,
                                   final long orderId,
                                   final OrderAction action,
                                   final boolean takerOrderCompleted,
                                   final TradeEvent... tradeEvents) {

        verifyTradeEvents(res, uid, orderId, action, takerOrderCompleted, null, tradeEvents);
    }

    private void verifyTradeEvents(final CommandResponse res,
                                   final long uid,
                                   final long orderId,
                                   final OrderAction action,
                                   final boolean takerOrderCompleted,
                                   final ReduceEvent reduceEvent,
                                   final TradeEvent... tradeEvents) {


        assertThat(res.getUid(), is(uid));
        assertThat(res.getOrderId(), is(orderId));
        assertThat(res.getTakerAction(), is(action));
        assertThat(res.isOrderCompleted(), is(takerOrderCompleted));

        final List<TradeEvent> tradeEventsList = Arrays.asList(tradeEvents);
        assertThat(res.getTrades(), is((tradeEventsList)));

        final Optional<ReduceEvent> reduceEventOpt = res.getReduceEventOpt();
        assertThat(reduceEventOpt, is(Optional.ofNullable(reduceEvent)));
    }

    protected Consumer<CommandResponse> verifyRemainingSize(final long expected) {
        return resp -> assertThat(resp.getRemainingSizeOpt(), is(Optional.of(expected)));
    }

    protected Consumer<CommandResponse> verifyEmptyRemainingSize() {
        return resp -> assertThat(resp.getRemainingSizeOpt(), is(Optional.empty()));
    }


    protected Consumer<CommandResponse> verifyOrderCompleted() {
        return resp -> assertTrue(resp.isOrderCompleted());
    }

    protected Consumer<CommandResponse> verifyOrderNotCompleted() {
        return resp -> assertFalse(resp.isOrderCompleted());
    }

    protected Consumer<CommandResponse> verifyNoReduceEvent() {
        return resp -> assertFalse(resp.getReduceEventOpt().isPresent());
    }

    protected Consumer<CommandResponse> verifyNoTradeEvents() {
        return resp -> assertTrue(resp.getTrades().isEmpty());
    }

    protected Consumer<CommandResponse> verifyTradeEventsNum(final int expectedNumber) {
        return resp -> assertThat(resp.getTrades().size(), is(expectedNumber));
    }

    protected Consumer<CommandResponse> verifyTakerAction(final OrderAction expectedAction) {
        return resp -> assertThat(resp.getTakerAction(), is(expectedAction));
    }
}
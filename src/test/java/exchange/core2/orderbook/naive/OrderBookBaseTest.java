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
package exchange.core2.orderbook.naive;

import exchange.core2.orderbook.*;
import exchange.core2.orderbook.events.CommandProcessingResponse;
import exchange.core2.orderbook.events.ReduceEvent;
import exchange.core2.orderbook.events.TradeEvent;
import exchange.core2.orderbook.events.TradeEventsBlock;
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

import java.util.Arrays;
import java.util.Optional;

import static exchange.core2.orderbook.IOrderBook.*;
import static exchange.core2.orderbook.OrderAction.ASK;
import static exchange.core2.orderbook.OrderAction.BID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


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

    protected MutableDirectBuffer responseBuffer = new ExpandableDirectByteBuffer(256);
//    protected MutableDirectBuffer responseBuffer = new ExpandableArrayBuffer(256);

//    protected CommandsEncoder commandsEncoder = new CommandsEncoder(commandsBuffer);

    protected abstract IOrderBook<S> createNewOrderBook(MutableDirectBuffer resultsBuffer);


    static final long INITIAL_PRICE = 81600L;
    static final long MAX_PRICE = 400000L;

    static final long UID_1 = 8320000192882333412L;
    static final long UID_2 = 8320000192882333413L;

    protected abstract S getCoreSymbolSpec();


    @Before
    public void before() {
        orderBook = createNewOrderBook(responseBuffer);
        orderBook.verifyInternalState();

        placeOrder(ORDER_TYPE_GTC, -1L, UID_2, INITIAL_PRICE, 0L, 13L, ASK);
        cancel(-1L, UID_2);

        placeOrder(ORDER_TYPE_GTC, 1L, UID_1, 81600L, 0L, 100L, ASK);
        placeOrder(ORDER_TYPE_GTC, 2L, UID_1, 81599L, 0L, 50L, ASK);
        placeOrder(ORDER_TYPE_GTC, 3L, UID_1, 81599L, 0L, 25L, ASK);
        placeOrder(ORDER_TYPE_GTC, 8L, UID_1, 201000L, 0L, 28L, ASK);
        placeOrder(ORDER_TYPE_GTC, 9L, UID_1, 201000L, 0L, 32L, ASK);
        placeOrder(ORDER_TYPE_GTC, 10L, UID_1, 200954L, 0L, 10L, ASK);

        placeOrder(ORDER_TYPE_GTC, 4L, UID_1, 81593L, 82000L, 40L, BID);
        placeOrder(ORDER_TYPE_GTC, 5L, UID_1, 81590L, 82000L, 20L, BID);
        placeOrder(ORDER_TYPE_GTC, 6L, UID_1, 81590L, 82000L, 1L, BID);
        placeOrder(ORDER_TYPE_GTC, 7L, UID_1, 81200L, 82000L, 20L, BID);
        placeOrder(ORDER_TYPE_GTC, 11L, UID_1, 10000L, 12000L, 12L, BID);
        placeOrder(ORDER_TYPE_GTC, 12L, UID_1, 10000L, 12000L, 1L, BID);
        placeOrder(ORDER_TYPE_GTC, 13L, UID_1, 9136L, 12000L, 2L, BID);

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

    /**
     * Just place few GTC orders
     */
    @Test
    public void shouldAddGtcOrders() {

        placeOrder(ORDER_TYPE_GTC, 93L, UID_1, 81598L, 0, 1L, ASK);
        expectedState.insertAsk(0, 81598L, 1L);

        placeOrder(ORDER_TYPE_GTC, 94L, UID_1, 81594, MAX_PRICE, 9_000_000_000L, BID);
        expectedState.insertBid(0, 81594L, 9_000_000_000L);

        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
        orderBook.verifyInternalState();

        placeOrder(ORDER_TYPE_GTC, 95L, UID_1, 130000L, 0L, 13_000_000_000L, ASK);
        expectedState.insertAsk(3, 130000L, 13_000_000_000L);

        placeOrder(ORDER_TYPE_GTC, 96L, UID_1, 1000L, MAX_PRICE, 4L, BID);
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

        CommandProcessingResponse res = placeOrder(ORDER_TYPE_GTC, 1L, UID_1, 81600L, 0L, 100L, ASK);

        // just confirm reduce event exists, rest of verifications are done automatically
        Optional<TradeEventsBlock> tradeEventsBlockOpt = res.getTradeEventsBlock();
        assertTrue(tradeEventsBlockOpt.isPresent());
        TradeEventsBlock tradeEventsBlock = tradeEventsBlockOpt.get();

        assertTrue(tradeEventsBlock.getReduceEvent().isPresent());
        assertThat(tradeEventsBlock.getTrades().length, is(0));
    }


    /**
     * Remove existing order
     */
    @Test
    public void shouldRemoveBidOrder() {

        // remove bid order
        CommandProcessingResponse res = cancel(5L, UID_1);

        expectedState.setBidVolume(1, 1).decrementBidOrdersNum(1);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));

        verifySingleReduceEvent(res, UID_1, 5L, BID, 81590L, 82000L, 20L, true);
    }


    @Test
    public void shouldRemoveAskOrder() {
        // remove ask order
        CommandProcessingResponse res = cancel(2L, UID_1);

        expectedState.setAskVolume(0, 25).decrementAskOrdersNum(0);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));

        verifySingleReduceEvent(res, UID_1, 2L, ASK, 81599L, 0L, 50L, true);
    }

    @Test
    public void shouldReduceBidOrder() {

        // reduce bid order
        CommandProcessingResponse res = reduce(5L, UID_1, 3L);

        expectedState.decrementBidVolume(1, 3L);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));

        verifySingleReduceEvent(res, UID_1, 5L, BID, 81590L, 82000L, 3L, false);
    }


    @Test
    public void shouldReduceAskOrder() {
        // reduce ask order - will effectively remove order
        CommandProcessingResponse res = reduce(1L, UID_1, 300L);

        expectedState.removeAsk(1);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));

        verifySingleReduceEvent(res, UID_1, 1L, ASK, 81600L, 0L, 100L, true);
    }

    @Test
    public void shouldReduceOrderByMaxSize() {
        // reduce ask order by max possible value - will effectively remove order
        CommandProcessingResponse res = reduce(1L, UID_1, Long.MAX_VALUE);

        expectedState.removeAsk(1);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));

        verifySingleReduceEvent(res, UID_1, 1L, ASK, 81600L, 0L, 100L, true);
    }

    @Test
    public void shouldIgnoreReducingByNegativeSize() {

        // zero
        CommandProcessingResponse res = reduce(4, UID_1, 0L);
        verifyNoEvents(res);

        // negative
        res = reduce(8, UID_1, -1L);
        verifyNoEvents(res);

        // big negative
        res = reduce(8, UID_1, Long.MIN_VALUE);
        verifyNoEvents(res);

        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }


    /**
     * When cancelling an order, order book implementation should also remove a bucket if no orders left for specified price
     */
    @Test
    public void shouldRemoveOrderAndEmptyBucket() {
        CommandProcessingResponse res = cancel(2L, UID_1);

        verifySingleReduceEvent(res, UID_1, 2L, ASK, 81599L, 0L, 50L, true);

        //log.debug("{}", orderBook.getL2MarketDataSnapshot(10).dumpOrderBook());

        CommandProcessingResponse res2 = cancel(3L, UID_1);

        verifySingleReduceEvent(res2, UID_1, 3L, ASK, 81599L, 0L, 25L, true);

        expectedState.removeAsk(0);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }

    @Test
    public void shouldReturnErrorWhenCancelUnknownOrder() {

        CommandProcessingResponse res = cancel(5291L, UID_1, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }

    @Test
    public void shouldReturnErrorWhenCancelOtherUserOrder() {

        CommandProcessingResponse res = cancel(3L, UID_2, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }

    @Test
    public void shouldReturnErrorWhenMoveOtherUserOrder() {

        CommandProcessingResponse res = move(2L, UID_2, 100L, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }


    @Test
    public void shouldReturnErrorWhenMoveUnknownOrder() {

        CommandProcessingResponse res = move(2433, UID_1, 300, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }

    @Test
    public void shouldReturnErrorWhenReducingUnknownOrder() {

        CommandProcessingResponse res = reduce(3, UID_2, 1, RESULT_UNKNOWN_ORDER_ID);
        verifyNoEvents(res);
        assertThat(orderBook.getL2MarketDataSnapshot(), is(expectedState.build()));
    }


    //
//    @Test
//    public void shouldReturnErrorWhenReducingOtherUserOrder() {
//
//        OrderCommand cmd = OrderCommand.reduce(8, UID_2, 3);
//        processAndValidate(cmd, RESULT_UNKNOWN_ORDER_ID);
//        assertNull(cmd.matcherEvent);
//
//        assertEquals(expectedState.build(), orderBook.getL2MarketDataSnapshot());
//    }
//
//    @Test
//    public void shouldMoveOrderExistingBucket() {
//        OrderCommand cmd = OrderCommand.update(7, UID_1, 81590);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//
//        // moved
//        L2MarketData expected = expectedState.setBidVolume(1, 41).incrementBidOrdersNum(1).removeBid(2).build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(0));
//    }
//
//    @Test
//    public void shouldMoveOrderNewBucket() {
//        OrderCommand cmd = OrderCommand.update(7, UID_1, 81594);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//
//        // moved
//        L2MarketData expected = expectedState.removeBid(2).insertBid(0, 81594, 20).build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(0));
//    }
//
//    // ------------------------ MATCHING TESTS -----------------------
//
//    @Test
//    public void shouldMatchIocOrderPartialBBO() {
//
//        // size=10
//        OrderCommand cmd = CommandsEncoder.placeOrder(IOC, 123, UID_2, 1, 0, 10, ASK);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        // best bid matched
//        L2MarketData expected = expectedState.setBidVolume(0, 30).build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(1));
//        checkEventTrade(events.get(0), 4L, 81593, 10L);
//    }
//
//
//    @Test
//    public void shouldMatchIocOrderFullBBO() {
//
//        // size=40
//        OrderCommand cmd = CommandsEncoder.placeOrder(IOC, 123, UID_2, 1, 0, 40, ASK);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        // best bid matched
//        L2MarketData expected = expectedState.removeBid(0).build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(1));
//        checkEventTrade(events.get(0), 4L, 81593, 40L);
//    }
//
//    @Test
//    public void shouldMatchIocOrderWithTwoLimitOrdersPartial() {
//
//        // size=41
//        OrderCommand cmd = CommandsEncoder.placeOrder(IOC, 123, UID_2, 1, 0, 41, ASK);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        // bids matched
//        L2MarketData expected = expectedState.removeBid(0).setBidVolume(0, 20).build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(2));
//        checkEventTrade(events.get(0), 4L, 81593, 40L);
//        checkEventTrade(events.get(1), 5L, 81590, 1L);
//
//        // check orders are removed from map
//        assertNull(orderBook.getOrderById(4L));
//        assertNotNull(orderBook.getOrderById(5L));
//    }
//
//
//    @Test
//    public void shouldMatchIocOrderFullLiquidity() {
//
//        // size=175
//        OrderCommand cmd = CommandsEncoder.placeOrder(IOC, 123, UID_2, MAX_PRICE, MAX_PRICE, 175, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        // all asks matched
//        L2MarketData expected = expectedState.removeAsk(0).removeAsk(0).build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(3));
//        checkEventTrade(events.get(0), 2L, 81599L, 50L);
//        checkEventTrade(events.get(1), 3L, 81599L, 25L);
//        checkEventTrade(events.get(2), 1L, 81600L, 100L);
//
//        // check orders are removed from map
//        assertNull(orderBook.getOrderById(1L));
//        assertNull(orderBook.getOrderById(2L));
//        assertNull(orderBook.getOrderById(3L));
//    }
//
//    @Test
//    public void shouldMatchIocOrderWithRejection() {
//
//        // size=270
//        OrderCommand cmd = CommandsEncoder.placeOrder(IOC, 123, UID_2, MAX_PRICE, MAX_PRICE + 1, 270, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        // all asks matched
//        L2MarketData expected = expectedState.removeAllAsks().build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(7));
//
//        // 6 trades generated, first comes rejection with size=25 left unmatched
//        checkEventRejection(events.get(0), 25L, 400000L, MAX_PRICE + 1);
//    }
//
//    // ---------------------- FOK BUDGET ORDERS ---------------------------
//
//    @Test
//    public void shouldRejectFokBidOrderOutOfBudget() {
//
//        long size = 180L;
//        long buyBudget = expectedState.aggregateBuyBudget(size) - 1;
//        assertThat(buyBudget, is(81599L * 75L + 81600L * 100L + 200954L * 5L - 1));
//
//        OrderCommand cmd = CommandsEncoder.placeOrder(FOK_BUDGET, 123L, UID_2, buyBudget, buyBudget, size, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        assertEquals(expectedState.build(), snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(1));
//
//        // no trades generated, rejection with full size unmatched
//        checkEventRejection(events.get(0), size, buyBudget, buyBudget);
//    }
//
//    @Test
//    public void shouldMatchFokBidOrderExactBudget() {
//
//        long size = 180L;
//        long buyBudget = expectedState.aggregateBuyBudget(size);
//        assertThat(buyBudget, is(81599L * 75L + 81600L * 100L + 200954L * 5L));
//
//        OrderCommand cmd = CommandsEncoder.placeOrder(FOK_BUDGET, 123L, UID_2, buyBudget, buyBudget, size, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        assertEquals(expectedState.removeAsk(0).removeAsk(0).setAskVolume(0, 5).build(), snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(4));
//        checkEventTrade(events.get(0), 2L, 81599, 50L);
//        checkEventTrade(events.get(1), 3L, 81599, 25L);
//        checkEventTrade(events.get(2), 1L, 81600L, 100L);
//        checkEventTrade(events.get(3), 10L, 200954L, 5L);
//    }
//
//    @Test
//    public void shouldMatchFokBidOrderExtraBudget() {
//
//        long size = 176L;
//        long buyBudget = expectedState.aggregateBuyBudget(size) + 1;
//        assertThat(buyBudget, is(81599L * 75L + 81600L * 100L + 200954L + 1L));
//
//        OrderCommand cmd = CommandsEncoder.placeOrder(FOK_BUDGET, 123L, UID_2, buyBudget, buyBudget, size, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        assertEquals(expectedState.removeAsk(0).removeAsk(0).setAskVolume(0, 9).build(), snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(4));
//        checkEventTrade(events.get(0), 2L, 81599, 50L);
//        checkEventTrade(events.get(1), 3L, 81599, 25L);
//        checkEventTrade(events.get(2), 1L, 81600L, 100L);
//        checkEventTrade(events.get(3), 10L, 200954L, 1L);
//    }
//
//    @Test
//    public void shouldRejectFokAskOrderBelowExpectation() {
//
//        long size = 60L;
//        long sellExpectation = expectedState.aggregateSellExpectation(size) + 1;
//        assertThat(sellExpectation, is(81593L * 40L + 81590L * 20L + 1));
//
//        OrderCommand cmd = CommandsEncoder.placeOrder(FOK_BUDGET, 123L, UID_2, sellExpectation, sellExpectation, size, ASK);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        assertEquals(expectedState.build(), snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(1));
//        // no trades generated, rejection with full size unmatched
//        checkEventRejection(events.get(0), size, sellExpectation, sellExpectation);
//    }
//
//    @Test
//    public void shouldMatchFokAskOrderExactExpectation() {
//
//        long size = 60L;
//        long sellExpectation = expectedState.aggregateSellExpectation(size);
//        assertThat(sellExpectation, is(81593L * 40L + 81590L * 20L));
//
//        OrderCommand cmd = CommandsEncoder.placeOrder(FOK_BUDGET, 123L, UID_2, sellExpectation, sellExpectation, size, ASK);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        assertEquals(expectedState.removeBid(0).setBidVolume(0, 1).decrementBidOrdersNum(0).build(), snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(2));
//        checkEventTrade(events.get(0), 4L, 81593L, 40L);
//        checkEventTrade(events.get(1), 5L, 81590L, 20L);
//    }
//
//    @Test
//    public void shouldMatchFokAskOrderExtraBudget() {
//
//        long size = 61L;
//        long sellExpectation = expectedState.aggregateSellExpectation(size) - 1;
//        assertThat(sellExpectation, is(81593L * 40L + 81590L * 21L - 1));
//
//        OrderCommand cmd = CommandsEncoder.placeOrder(FOK_BUDGET, 123L, UID_2, sellExpectation, sellExpectation, size, ASK);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        assertEquals(expectedState.removeBid(0).removeBid(0).build(), snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(3));
//        checkEventTrade(events.get(0), 4L, 81593L, 40L);
//        checkEventTrade(events.get(1), 5L, 81590L, 20L);
//        checkEventTrade(events.get(2), 6L, 81590L, 1L);
//    }
//
//
//    // MARKETABLE GTC ORDERS
//
//    @Test
//    public void shouldFullyMatchMarketableGtcOrder() {
//
//        // size=1
//        OrderCommand cmd = CommandsEncoder.placeOrder(ORDER_TYPE_GTC, 123, UID_2, 81599, MAX_PRICE, 1, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        // best ask partially matched
//        L2MarketData expected = expectedState.setAskVolume(0, 74).build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(1));
//        checkEventTrade(events.get(0), 2L, 81599, 1L);
//    }
//
//
//    @Test
//    public void shouldPartiallyMatchMarketableGtcOrderAndPlace() {
//
//        // size=77
//        OrderCommand cmd = CommandsEncoder.placeOrder(ORDER_TYPE_GTC, 123, UID_2, 81599, MAX_PRICE, 77, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        // best asks fully matched, limit bid order placed
//        L2MarketData expected = expectedState.removeAsk(0).insertBid(0, 81599, 2).build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(2));
//
//        checkEventTrade(events.get(0), 2L, 81599, 50L);
//        checkEventTrade(events.get(1), 3L, 81599, 25L);
//    }
//
//    @Test
//    public void shouldFullyMatchMarketableGtcOrder2Prices() {
//
//        // size=77
//        OrderCommand cmd = CommandsEncoder.placeOrder(ORDER_TYPE_GTC, 123, UID_2, 81600, MAX_PRICE, 77, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        // best asks fully matched, limit bid order placed
//        L2MarketData expected = expectedState.removeAsk(0).setAskVolume(0, 98).build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(3));
//
//        checkEventTrade(events.get(0), 2L, 81599, 50L);
//        checkEventTrade(events.get(1), 3L, 81599, 25L);
//        checkEventTrade(events.get(2), 1L, 81600, 2L);
//    }
//
//
//    @Test
//    public void shouldFullyMatchMarketableGtcOrderWithAllLiquidity() {
//
//        // size=1000
//        OrderCommand cmd = CommandsEncoder.placeOrder(ORDER_TYPE_GTC, 123, UID_2, 220000, MAX_PRICE, 1000, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        // best asks fully matched, limit bid order placed
//        L2MarketData expected = expectedState.removeAllAsks().insertBid(0, 220000, 755).build();
//        assertEquals(expected, snapshot);
//
//        // trades only, rejection not generated for limit order
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(6));
//
//        checkEventTrade(events.get(0), 2L, 81599, 50L);
//        checkEventTrade(events.get(1), 3L, 81599, 25L);
//        checkEventTrade(events.get(2), 1L, 81600, 100L);
//        checkEventTrade(events.get(3), 10L, 200954, 10L);
//        checkEventTrade(events.get(4), 8L, 201000, 28L);
//        checkEventTrade(events.get(5), 9L, 201000, 32L);
//    }
//
//
//    // Move GTC order to marketable price
//    // TODO add into far area
//    @Test
//    public void shouldMoveOrderFullyMatchAsMarketable() {
//
//        // add new order and check it is there
//        OrderCommand cmd = CommandsEncoder.placeOrder(ORDER_TYPE_GTC, 83, UID_2, 81200, MAX_PRICE, 20, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(0));
//
//        L2MarketData expected = expectedState.setBidVolume(2, 40).incrementBidOrdersNum(2).build();
//        assertEquals(expected, orderBook.getL2MarketDataSnapshot(10));
//
//        // move to marketable price area
//        cmd = OrderCommand.update(83, UID_2, 81602);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        // moved
//        expected = expectedState.setBidVolume(2, 20).decrementBidOrdersNum(2).setAskVolume(0, 55).build();
//        assertEquals(expected, orderBook.getL2MarketDataSnapshot(10));
//
//        events = cmd.extractEvents();
//        assertThat(events.size(), is(1));
//        checkEventTrade(events.get(0), 2L, 81599, 20L);
//    }
//
//
//    @Test
//    public void shouldMoveOrderFullyMatchAsMarketable2Prices() {
//
//        OrderCommand cmd = CommandsEncoder.placeOrder(ORDER_TYPE_GTC, 83, UID_2, 81594, MAX_PRICE, 100, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(0));
//
//        // move to marketable zone
//        cmd = OrderCommand.update(83, UID_2, 81600);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//
//        // moved
//        L2MarketData expected = expectedState.removeAsk(0).setAskVolume(0, 75).build();
//        assertEquals(expected, snapshot);
//
//        events = cmd.extractEvents();
//        assertThat(events.size(), is(3));
//        checkEventTrade(events.get(0), 2L, 81599, 50L);
//        checkEventTrade(events.get(1), 3L, 81599, 25L);
//        checkEventTrade(events.get(2), 1L, 81600, 25L);
//
//    }
//
//    @Test
//    public void shouldMoveOrderMatchesAllLiquidity() {
//
//        OrderCommand cmd = CommandsEncoder.placeOrder(ORDER_TYPE_GTC, 83, UID_2, 81594, MAX_PRICE, 246, BID);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        // move to marketable zone
//        cmd = OrderCommand.update(83, UID_2, 201000);
//        processAndValidate(cmd, RESULT_SUCCESS);
//
//        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//
//        // moved
//        L2MarketData expected = expectedState.removeAllAsks().insertBid(0, 201000, 1).build();
//        assertEquals(expected, snapshot);
//
//        List<MatcherTradeEvent> events = cmd.extractEvents();
//        assertThat(events.size(), is(6));
//        checkEventTrade(events.get(0), 2L, 81599, 50L);
//        checkEventTrade(events.get(1), 3L, 81599, 25L);
//        checkEventTrade(events.get(2), 1L, 81600, 100L);
//        checkEventTrade(events.get(3), 10L, 200954, 10L);
//        checkEventTrade(events.get(4), 8L, 201000, 28L);
//        checkEventTrade(events.get(5), 9L, 201000, 32L);
//    }
//
//
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
//
    // ------------------------------- UTILITY METHODS --------------------------
    protected CommandProcessingResponse placeOrder(final byte type,
                                                   final long orderId,
                                                   final long uid,
                                                   final long price,
                                                   final long reservedBidPrice,
                                                   final long size,
                                                   final OrderAction action) {

        orderBook.newOrder(CommandsEncoder.placeOrder(type, orderId, uid, price, reservedBidPrice, size, action), 0);

        final CommandProcessingResponse response = ResponseDecoder.readResult(responseBuffer, 0);

        assertThat(response.getResultCode(), is(RESULT_SUCCESS));

        final Optional<TradeEventsBlock> tradeEventsBlockOpt = response.getTradeEventsBlock();

        if (type != ORDER_TYPE_GTC) {
            // trades block is mandatory for non GTC orders
            assertTrue(tradeEventsBlockOpt.isPresent());
        }

        if (tradeEventsBlockOpt.isPresent()) {

            final TradeEventsBlock tradeEventsBlock = tradeEventsBlockOpt.get();

            assertThat(tradeEventsBlock.getTakerAction(), is(action));
            assertThat(tradeEventsBlock.getTakerOrderId(), is(orderId));
            assertThat(tradeEventsBlock.getTakerUid(), is(uid));

            final MutableLong totalVolumeInEvents = new MutableLong();

            final Optional<ReduceEvent> reduceEventOpt = tradeEventsBlock.getReduceEvent();
            final TradeEvent[] trades = tradeEventsBlock.getTrades();
            assertTrue(reduceEventOpt.isPresent() || trades.length != 0);

            reduceEventOpt.ifPresent(reduceEvent -> {
                assertThat(reduceEvent.getPrice(), is(price));
                assertThat(reduceEvent.getReservedBidPrice(), is(action == BID ? reservedBidPrice : 0L));
                assertTrue(reduceEvent.getReducedVolume() > 0);
                totalVolumeInEvents.addAndGet(reduceEvent.getReducedVolume());
            });

            Arrays.stream(trades).forEach(trade -> {
                assertThat(trade.getMakerOrderId(), IsNot.not(0L));
                assertThat(trade.getMakerUid(), IsNot.not(0L));
                assertTrue(trade.getReservedBidPrice() > 0);
                assertTrue(trade.getTradePrice() > 0);
                assertTrue(trade.getTradeVolume() > 0);
                totalVolumeInEvents.addAndGet(trade.getTradeVolume());
            });

            if (type != ORDER_TYPE_GTC) {
                assertThat(totalVolumeInEvents.get(), is(size));
            }
        }

        orderBook.verifyInternalState();
        return response;
    }

    // cancel

    protected CommandProcessingResponse cancel(final long orderId,
                                               final long uid) {

        return cancel(orderId, uid, RESULT_SUCCESS);
    }

    protected CommandProcessingResponse cancel(final long orderId,
                                               final long uid,
                                               final short expectedResultCode) {

        orderBook.cancelOrder(CommandsEncoder.cancel(orderId, uid), 0);
        return readResultAndVerifyInternalState(expectedResultCode);
    }

    // reduce

    protected CommandProcessingResponse reduce(final long orderId,
                                               final long uid,
                                               final long size) {
        return reduce(orderId, uid, size, RESULT_SUCCESS);

    }

    protected CommandProcessingResponse reduce(final long orderId,
                                               final long uid,
                                               final long size,
                                               final short expectedResultCode) {
        orderBook.reduceOrder(CommandsEncoder.reduce(orderId, uid, size), 0);
        return readResultAndVerifyInternalState(expectedResultCode);
    }

    // move

    protected CommandProcessingResponse move(final long orderId,
                                             final long uid,
                                             final long price) {

        return move(orderId, uid, price, RESULT_SUCCESS);
    }

    protected CommandProcessingResponse move(final long orderId,
                                             final long uid,
                                             final long price,
                                             final short expectedResultCode) {

        orderBook.moveOrder(CommandsEncoder.move(orderId, uid, price), 0);
        return readResultAndVerifyInternalState(expectedResultCode);
    }

    private CommandProcessingResponse readResultAndVerifyInternalState(short expectedResultCode) {
        final CommandProcessingResponse response = ResponseDecoder.readResult(responseBuffer, 0);
        assertThat(response.getResultCode(), is(expectedResultCode));

        orderBook.verifyInternalState();
        return response;
    }

    private void verifyNoEvents(final CommandProcessingResponse res) {
        assertFalse(res.getTradeEventsBlock().isPresent());
    }

    private void verifySingleReduceEvent(final CommandProcessingResponse res,
                                         final long uid,
                                         final long orderId,
                                         final OrderAction action,
                                         final long price,
                                         final long reservedBidPrice,
                                         final long reducedVolume,
                                         final boolean completed) {

        Optional<TradeEventsBlock> tradeEventsBlockOpt = res.getTradeEventsBlock();
        assertTrue(tradeEventsBlockOpt.isPresent());
        TradeEventsBlock tradeEventsBlock = tradeEventsBlockOpt.get();


        assertThat(tradeEventsBlock.getTakerUid(), is(uid));
        assertThat(tradeEventsBlock.getTakerOrderId(), is(orderId));
        assertThat(tradeEventsBlock.isTakerOrderCompleted(), is(completed));
        assertThat(tradeEventsBlock.getTakerAction(), is(action));

        assertThat(tradeEventsBlock.getTrades().length, is(0));

        Optional<ReduceEvent> reduceEventOpt = tradeEventsBlock.getReduceEvent();
        assertTrue(reduceEventOpt.isPresent());
        ReduceEvent reduceEvent = reduceEventOpt.get();

        assertThat(reduceEvent.getPrice(), is(price));
        assertThat(reduceEvent.getReservedBidPrice(), is(reservedBidPrice));
        assertThat(reduceEvent.getReducedVolume(), is(reducedVolume));
    }


//    public void checkEventTrade(MatcherTradeEvent event, long matchedId, long price, long size) {
//        assertThat(event.eventType, is(MatcherEventType.TRADE));
//        assertThat(event.matchedOrderId, is(matchedId));
//        assertThat(event.price, is(price));
//        assertThat(event.size, is(size));
//        // TODO add more checks for MatcherTradeEvent
//    }
//
//    public void checkEventRejection(MatcherTradeEvent event, long size, long price, Long bidderHoldPrice) {
//        assertThat(event.eventType, is(MatcherEventType.REJECT));
//        assertThat(event.size, is(size));
//        assertThat(event.price, is(price));
//        assertTrue(event.activeOrderCompleted);
//        if (bidderHoldPrice != null) {
//            assertThat(event.bidderHoldPrice, is(bidderHoldPrice));
//        }
//    }
//
//    public void checkEventReduce(MatcherTradeEvent event, long reduceSize, long price, boolean completed, Long bidderHoldPrice) {
//        assertThat(event.eventType, is(MatcherEventType.REDUCE));
//        assertThat(event.size, is(reduceSize));
//        assertThat(event.price, is(price));
//        assertThat(event.activeOrderCompleted, is(completed));
//        assertNull(event.nextEvent);
//        if (bidderHoldPrice != null) {
//            assertThat(event.bidderHoldPrice, is(bidderHoldPrice));
//        }
//    }

}
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
import exchange.core2.orderbook.OrderBookEventsHelper;
import exchange.core2.orderbook.util.BufferWriter;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.MutableLong;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.function.LongConsumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OrdersBucketNaiveTest {

    private final static long PRICE = 132000L;

    private final static long BIDDER_HOLD_PRICE = 132800L;
    private final static long BIDDER_HOLD_PRICE_1 = 132801L;

    private OrdersBucketNaive bucket;

    @Mock
    private OrderBookEventsHelper eventsHelper;

    @Mock
    private LongConsumer removeCallback;

    @Captor
    ArgumentCaptor<NaivePendingOrder> orderCaptor;
    @Captor
    ArgumentCaptor<Long> sizeCaptor;
    @Captor
    ArgumentCaptor<Boolean> completedCaptor;
    @Captor
    ArgumentCaptor<Long> orderIdCaptor;

    @Before
    public void before() {
        bucket = new OrdersBucketNaive(PRICE, eventsHelper, removeCallback);
        bucket.validate();
    }


    @Test
    public void shouldReturnPrice() {

        assertThat(bucket.getPrice(), is(PRICE));
    }


    @Test
    public void shouldMaintainTotalVolume() {

        assertThat(bucket.getTotalVolume(), is(0L));

        // add orders
        addNewOrder(1L, 1L);
        addNewOrder(2L, 2L);
        NaivePendingOrder order3 = addNewOrder(3L, 10L, 3L);
        assertThat(bucket.getTotalVolume(), is(10L));
        bucket.validate();

        // cancel
        bucket.remove(2L);
        assertThat(bucket.getTotalVolume(), is(8L));
        bucket.validate();

        // reduce
        order3.setFilled(order3.getFilled() + 1);
        bucket.reduceSize(1L);
        assertThat(bucket.getTotalVolume(), is(7L));
        bucket.validate();


        // match all
        match(19L);
        assertThat(bucket.getTotalVolume(), is(0L));
        bucket.validate();
    }

    @Test
    public void shouldMaintainTotalOrdersNum() {

        assertThat(bucket.getNumOrders(), is(0));

        // add orders
        addNewOrder(1L, 1L);
        addNewOrder(2L, 2L);
        addNewOrder(3L, 10L, 3L);
        assertThat(bucket.getNumOrders(), is(3));
        bucket.validate();

        // cancel
        bucket.remove(1L);
        assertThat(bucket.getNumOrders(), is(2));
        bucket.validate();

        // match all
        match(55L);
        assertThat(bucket.getNumOrders(), is(0));
        bucket.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void validationNotRemoveUnknownOrder() {

        bucket.remove(312837912873L);
    }


    @Test(expected = IllegalStateException.class)
    public void validationShouldDetectVolumeInconsistency() {

        addNewOrder(1L, 3L);
        NaivePendingOrder order2 = addNewOrder(2L, 201L);
        order2.setFilled(order2.getFilled() + 1);
        bucket.validate();
    }

    @Test
    public void shouldMatchEmptyBucket() {

        long matched = match(1L);

        assertThat(matched, is(0L));

        assertThat(bucket.getTotalVolume(), is(0L));
        assertThat(bucket.getNumOrders(), is(0));

        verify(eventsHelper, never()).appendTradeEvent(any(IOrder.class), anyBoolean(), anyLong(), anyLong());
        verify(eventsHelper, never()).appendReduceEvent(anyLong(), anyLong(), anyLong());

        verify(removeCallback, never()).accept(anyLong());
    }

    @Test
    public void shouldMatchSingleOrderPartially() {
        // add orders
        NaivePendingOrder order1 = addNewOrder(1L, 4L);
        addNewOrder(2L, 10L, 3L);
        addNewOrder(3L, 1L);

        long matched = match(3L);

        assertThat(matched, is(3L));

        assertThat(bucket.getTotalVolume(), is(9L));
        assertThat(bucket.getNumOrders(), is(3));

        verify(eventsHelper, times(1)).appendTradeEvent(eq(order1), eq(false), eq(3L), eq(BIDDER_HOLD_PRICE));
        verify(eventsHelper, never()).appendReduceEvent(anyLong(), anyLong(), anyLong());

        verify(removeCallback, never()).accept(anyLong());
    }

    @Test
    public void shouldMatchSingleOrderCompletely() {
        // add orders
        NaivePendingOrder order1 = addNewOrder(1L, 4L);
        addNewOrder(2L, 10L, 3L);
        addNewOrder(3L, 1L);

        long matched = match(4L);

        assertThat(matched, is(4L));

        assertThat(bucket.getTotalVolume(), is(8L));
        assertThat(bucket.getNumOrders(), is(2));

        verify(eventsHelper, times(1)).appendTradeEvent(eq(order1), eq(true), eq(4L), eq(BIDDER_HOLD_PRICE));
        verify(eventsHelper, never()).appendReduceEvent(anyLong(), anyLong(), anyLong());

        verify(removeCallback, times(1)).accept(eq(1L));
    }

    @Test
    public void shouldMatchTwoOrders() {
        // add orders
        NaivePendingOrder order1 = addNewOrder(1L, 4L);
        NaivePendingOrder order2 = addNewOrder(2L, 10L, 3L);
        addNewOrder(3L, 1L);


        long matched = match(5L);

        assertThat(matched, is(5L));

        assertThat(bucket.getTotalVolume(), is(7L));
        assertThat(bucket.getNumOrders(), is(2));

        verify(eventsHelper, times(2)).appendTradeEvent(orderCaptor.capture(), completedCaptor.capture(), sizeCaptor.capture(), eq(BIDDER_HOLD_PRICE));

        assertThat(orderCaptor.getAllValues().get(0), is(order1));
        assertThat(completedCaptor.getAllValues().get(0), is(true));
        assertThat(sizeCaptor.getAllValues().get(0), is(4L));

        assertThat(orderCaptor.getAllValues().get(1), is(order2));
        assertThat(completedCaptor.getAllValues().get(1), is(false));
        assertThat(sizeCaptor.getAllValues().get(1), is(1L));

        verify(eventsHelper, never()).appendReduceEvent(anyLong(), anyLong(), anyLong());

        verify(removeCallback, times(1)).accept(eq(1L));
    }

    @Test
    public void shouldMatchAllOrders() {
        // add orders
        NaivePendingOrder order1 = addNewOrder(1L, 4L);
        NaivePendingOrder order2 = addNewOrder(2L, 10L, 3L);
        NaivePendingOrder order3 = addNewOrder(3L, 1L);

        long matched = match(35L);

        assertThat(matched, is(12L));

        assertThat(bucket.getTotalVolume(), is(0L));
        assertThat(bucket.getNumOrders(), is(0));

        verify(eventsHelper, times(3)).appendTradeEvent(orderCaptor.capture(), completedCaptor.capture(), sizeCaptor.capture(), eq(BIDDER_HOLD_PRICE));

        assertThat(orderCaptor.getAllValues().get(0), is(order1));
        assertThat(completedCaptor.getAllValues().get(0), is(true));
        assertThat(sizeCaptor.getAllValues().get(0), is(4L));

        assertThat(orderCaptor.getAllValues().get(1), is(order2));
        assertThat(completedCaptor.getAllValues().get(1), is(true));
        assertThat(sizeCaptor.getAllValues().get(1), is(7L));

        assertThat(orderCaptor.getAllValues().get(2), is(order3));
        assertThat(completedCaptor.getAllValues().get(2), is(true));
        assertThat(sizeCaptor.getAllValues().get(2), is(1L));

        verify(eventsHelper, never()).appendReduceEvent(anyLong(), anyLong(), anyLong());

        verify(removeCallback, times(3)).accept(orderIdCaptor.capture());

        assertThat(orderIdCaptor.getAllValues().get(0), is(order1.getOrderId()));
        assertThat(orderIdCaptor.getAllValues().get(1), is(order2.getOrderId()));
        assertThat(orderIdCaptor.getAllValues().get(2), is(order3.getOrderId()));
    }

    @Test
    public void shouldAffectExecutionOrder() {
        // add orders
        NaivePendingOrder order1 = addNewOrder(1L, 4L);
        addNewOrder(2L, 10L, 3L);
        NaivePendingOrder order3 = addNewOrder(3L, 1L);

        // remove and re-insert order2
        bucket.remove(2);
        NaivePendingOrder order2 = addNewOrder(2L, 10L, 3L);

        long matched = match(12L);

        assertThat(matched, is(12L));

        assertThat(bucket.getTotalVolume(), is(0L));
        assertThat(bucket.getNumOrders(), is(0));

        verify(eventsHelper, times(3)).appendTradeEvent(orderCaptor.capture(), completedCaptor.capture(), sizeCaptor.capture(), eq(BIDDER_HOLD_PRICE));

        assertThat(orderCaptor.getAllValues().get(0), is(order1));
        assertThat(completedCaptor.getAllValues().get(0), is(true));
        assertThat(sizeCaptor.getAllValues().get(0), is(4L));

        assertThat(orderCaptor.getAllValues().get(1), is(order3));
        assertThat(completedCaptor.getAllValues().get(1), is(true));
        assertThat(sizeCaptor.getAllValues().get(1), is(1L));

        assertThat(orderCaptor.getAllValues().get(2), is(order2));
        assertThat(completedCaptor.getAllValues().get(2), is(true));
        assertThat(sizeCaptor.getAllValues().get(2), is(7L));

        verify(eventsHelper, never()).appendReduceEvent(anyLong(), anyLong(), anyLong());

        verify(removeCallback, times(3)).accept(orderIdCaptor.capture());

        assertThat(orderIdCaptor.getAllValues().get(0), is(order1.getOrderId()));
        assertThat(orderIdCaptor.getAllValues().get(1), is(order3.getOrderId()));
        assertThat(orderIdCaptor.getAllValues().get(2), is(order2.getOrderId()));
    }

    @Test
    public void shouldUseBidderHoldPrice() {
        // add orders
        NaivePendingOrder order1 = createOrder(1L, 4L, 0L, OrderAction.ASK);
        bucket.put(order1);

        bucket.match(1L, BIDDER_HOLD_PRICE_1);

        // bidder is taker (because maker order is ASK order), therefore BIDDER_HOLD_PRICE_1 should be provided
        verify(eventsHelper, times(1)).appendTradeEvent(eq(order1), eq(false), eq(1L), eq(BIDDER_HOLD_PRICE_1));
    }

    @Test
    public void shouldReturnOrdersList() {

        List<NaivePendingOrder> allOrders = bucket.getAllOrders();
        assertTrue(allOrders.isEmpty());

        // add orders
        addNewOrder(1L, 1L);
        addNewOrder(2L, 2L);
        addNewOrder(3L, 10L, 3L);
        allOrders = bucket.getAllOrders();
        assertThat(allOrders.size(), is(3));
        bucket.validate();
    }

    @Test
    public void shouldApplyForEachOrders() {

        MutableLong c = new MutableLong();
        bucket.forEachOrder(ord -> c.increment());
        assertThat(c.value, is(0L));


        // add orders
        addNewOrder(1L, 4L, 1L); // 3
        addNewOrder(2L, 2L); // 2
        addNewOrder(3L, 10L, 5L); // 5
        bucket.forEachOrder(ord -> c.addAndGet(ord.getUnmatchedSize()));

        assertThat(c.value, is(10L));
        bucket.validate();
    }

    @Test
    public void equalAndHashCode() {


        OrdersBucketNaive bucketA = new OrdersBucketNaive(123L, new OrderBookEventsHelper(new BufferWriter(new ExpandableArrayBuffer(), 0), false), removeCallback);
        bucketA.put(createOrder(1, 1, 0));
        bucketA.put(createOrder(3, 2, 1));
        bucketA.put(createOrder(4, 3, 2));

        OrdersBucketNaive bucketB = new OrdersBucketNaive(123L, new OrderBookEventsHelper(new BufferWriter(new ExpandableArrayBuffer(), 0), false), removeCallback);
        bucketB.put(createOrder(1, 1, 0));
        bucketB.put(createOrder(3, 2, 1));

        assertNotEquals(bucketA, bucketB);

        bucketB.put(createOrder(4, 3, 2));

        assertEquals(bucketA, bucketB);
        assertEquals(bucketA.hashCode(), bucketB.hashCode());
    }

    @Test
    public void dumpToSingleLine() {
        addNewOrder(1, 1, 0);
        addNewOrder(3, 2, 1);
        addNewOrder(4, 3, 2);

        String str = bucket.dumpToSingleLine();
        assertThat(str, is("132000 : vol:3 num:3 : id1_L1_F0, id3_L2_F1, id4_L3_F2"));
    }


    // -------------------------- utility methods ----------------------------------

    private NaivePendingOrder addNewOrder(long orderId, long size) {
        return addNewOrder(orderId, size, 0L);
    }

    private NaivePendingOrder addNewOrder(long orderId, long size, long filled) {
        NaivePendingOrder order = createOrder(orderId, size, filled, OrderAction.BID);
        bucket.put(order);
        return order;
    }

    private NaivePendingOrder createOrder(long orderId, long size, long filled) {
        return createOrder(orderId, size, filled, OrderAction.BID);
    }

    private NaivePendingOrder createOrder(long orderId, long size, long filled, OrderAction action) {
        return new NaivePendingOrder(orderId, PRICE, size, filled, BIDDER_HOLD_PRICE, action, 9289382L, 129839138288773L);
    }


    private long match(long volumeToCollect) {
        return bucket.match(volumeToCollect, 0L);
    }

}
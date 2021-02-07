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

package exchange.core2.orderbook.util;

import exchange.core2.orderbook.OrderAction;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.Before;
import org.junit.Test;

import static exchange.core2.orderbook.IOrderBook.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class CommandsEncoderTest {

    private MutableDirectBuffer buffer;


    private final static int OFFSET = 192;

    private final static long ORDER_ID = 871232876636821638L;
    private final static byte TYPE = (byte) 5;
    private final static long UID = 2965192688263679992L;
    private final static long PRICE = 102882916362869637L;
    private final static long RESERVED_BID_PRICE = 138286182637623999L;
    private final static long SIZE = 928726351726536753L;
    private final static int USER_COOKIE = 2069128732;

    @Before
    public void before() {
        buffer = new ExpandableArrayBuffer();
    }

    @Test
    public void shouldEncodePlaceOrder() {

        CommandsEncoder.placeOrder(
                buffer,
                OFFSET,
                TYPE,
                ORDER_ID,
                UID,
                PRICE,
                RESERVED_BID_PRICE,
                SIZE,
                OrderAction.BID,
                USER_COOKIE);

        assertThat(buffer.getLong(OFFSET + PLACE_OFFSET_UID), is(UID));
        assertThat(buffer.getLong(OFFSET + PLACE_OFFSET_ORDER_ID), is(ORDER_ID));
        assertThat(buffer.getLong(OFFSET + PLACE_OFFSET_PRICE), is(PRICE));
        assertThat(buffer.getLong(OFFSET + PLACE_OFFSET_RESERVED_BID_PRICE), is(RESERVED_BID_PRICE));
        assertThat(buffer.getLong(OFFSET + PLACE_OFFSET_SIZE), is(SIZE));
        assertThat(buffer.getInt(OFFSET + PLACE_OFFSET_USER_COOKIE), is(USER_COOKIE));
        assertThat(buffer.getByte(OFFSET + PLACE_OFFSET_ACTION), is(OrderAction.BID.getCode()));
        assertThat(buffer.getByte(OFFSET + PLACE_OFFSET_TYPE), is(TYPE));
    }

    @Test
    public void shouldEncodePlaceOrderNewBuffer() {

        MutableDirectBuffer buf = CommandsEncoder.placeOrder(
                TYPE,
                ORDER_ID,
                UID,
                PRICE,
                RESERVED_BID_PRICE,
                SIZE,
                OrderAction.BID,
                USER_COOKIE);

        assertThat(buf.getLong(PLACE_OFFSET_UID), is(UID));
        assertThat(buf.getLong(PLACE_OFFSET_ORDER_ID), is(ORDER_ID));
        assertThat(buf.getLong(PLACE_OFFSET_PRICE), is(PRICE));
        assertThat(buf.getLong(PLACE_OFFSET_RESERVED_BID_PRICE), is(RESERVED_BID_PRICE));
        assertThat(buf.getLong(PLACE_OFFSET_SIZE), is(SIZE));
        assertThat(buf.getInt(PLACE_OFFSET_USER_COOKIE), is(USER_COOKIE));
        assertThat(buf.getByte(PLACE_OFFSET_ACTION), is(OrderAction.BID.getCode()));
        assertThat(buf.getByte(PLACE_OFFSET_TYPE), is(TYPE));
    }


    @Test
    public void shouldEncodeCancel() {

        CommandsEncoder.cancel(buffer, OFFSET, ORDER_ID, UID);

        assertThat(buffer.getLong(OFFSET + CANCEL_OFFSET_UID), is(UID));
        assertThat(buffer.getLong(OFFSET + CANCEL_OFFSET_ORDER_ID), is(ORDER_ID));
    }

    @Test
    public void shouldEncodeCancelNewBuffer() {

        MutableDirectBuffer buf = CommandsEncoder.cancel(ORDER_ID, UID);

        assertThat(buf.getLong(CANCEL_OFFSET_UID), is(UID));
        assertThat(buf.getLong(CANCEL_OFFSET_ORDER_ID), is(ORDER_ID));
    }


    @Test
    public void shouldEncodeReduce() {

        CommandsEncoder.reduce(buffer, OFFSET, ORDER_ID, UID, SIZE);

        assertThat(buffer.getLong(OFFSET + REDUCE_OFFSET_UID), is(UID));
        assertThat(buffer.getLong(OFFSET + REDUCE_OFFSET_ORDER_ID), is(ORDER_ID));
        assertThat(buffer.getLong(OFFSET + REDUCE_OFFSET_SIZE), is(SIZE));
    }

    @Test
    public void shouldEncodeReduceNewBuffer() {

        MutableDirectBuffer buf = CommandsEncoder.reduce(ORDER_ID, UID, SIZE);

        assertThat(buf.getLong(REDUCE_OFFSET_UID), is(UID));
        assertThat(buf.getLong(REDUCE_OFFSET_ORDER_ID), is(ORDER_ID));
        assertThat(buf.getLong(REDUCE_OFFSET_SIZE), is(SIZE));
    }


    @Test
    public void shouldEncodeMove() {

        CommandsEncoder.move(buffer, OFFSET, ORDER_ID, UID, PRICE);

        assertThat(buffer.getLong(OFFSET + MOVE_OFFSET_UID), is(UID));
        assertThat(buffer.getLong(OFFSET + MOVE_OFFSET_ORDER_ID), is(ORDER_ID));
        assertThat(buffer.getLong(OFFSET + MOVE_OFFSET_PRICE), is(PRICE));
    }

    @Test
    public void shouldEncodeMoveNewBuffer() {

        MutableDirectBuffer buf = CommandsEncoder.move(ORDER_ID, UID, PRICE);

        assertThat(buf.getLong(MOVE_OFFSET_UID), is(UID));
        assertThat(buf.getLong(MOVE_OFFSET_ORDER_ID), is(ORDER_ID));
        assertThat(buf.getLong(MOVE_OFFSET_PRICE), is(PRICE));
    }

    @Test
    public void shouldEncodeL2Query() {

        CommandsEncoder.L2DataQuery(buffer, OFFSET, 1000000000);

        assertThat(buffer.getInt(OFFSET), is(1000000000));
    }

    @Test
    public void shouldEncodeL2QueryNewBuffer() {

        MutableDirectBuffer buf = CommandsEncoder.L2DataQuery(1000000000);

        assertThat(buf.getInt(0), is(1000000000));
    }

}
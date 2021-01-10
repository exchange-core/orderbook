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

package exchange.core2.orderbook;

import exchange.core2.orderbook.util.BufferWriter;
import org.agrona.DirectBuffer;

import java.util.List;
import java.util.stream.Stream;

/**
 * Utility class representing not-found order books
 *
 * @param <T> ISymbolSpecification implementation
 */
public final class VoidOrderBookImpl<T extends ISymbolSpecification> implements IOrderBook<T> {

    private final BufferWriter resultsBuffer;
    private final OrderBookEventsHelper eventsHelper;


    public VoidOrderBookImpl(BufferWriter resultsBuffer) {
        this.resultsBuffer = resultsBuffer;
        this.eventsHelper = new OrderBookEventsHelper(resultsBuffer, false);
    }

    @Override
    public void newOrder(DirectBuffer buffer, int offset, long timestamp) {

        final long uid = buffer.getLong(offset + PLACE_OFFSET_UID);
        final long newOrderId = buffer.getLong(offset + PLACE_OFFSET_ORDER_ID);
        final int userCookie = buffer.getInt(offset + PLACE_OFFSET_USER_COOKIE);
        final OrderAction action = OrderAction.of(buffer.getByte(offset + PLACE_OFFSET_ACTION));

        resultsBuffer.appendByte(IOrderBook.COMMAND_PLACE_ORDER);
        resultsBuffer.appendLong(uid);
        resultsBuffer.appendLong(newOrderId);
        resultsBuffer.appendInt(userCookie);
        eventsHelper.appendResultCode(IOrderBook.RESULT_UNKNOWN_SYMBOL, true, action, false);

        throw new IllegalStateException();
    }

    @Override
    public void cancelOrder(DirectBuffer buffer, int offset) {
        prepareCommandErrorResponse(buffer, offset, IOrderBook.COMMAND_CANCEL_ORDER);
    }

    @Override
    public void reduceOrder(DirectBuffer buffer, int offset) {
        prepareCommandErrorResponse(buffer, offset, IOrderBook.COMMAND_REDUCE_ORDER);
    }

    @Override
    public void moveOrder(DirectBuffer buffer, int offset) {
        prepareCommandErrorResponse(buffer, offset, IOrderBook.COMMAND_MOVE_ORDER);
    }


    private void prepareCommandErrorResponse(DirectBuffer buffer, int offset, byte commandCode) {
        final long orderId = buffer.getLong(offset + CANCEL_OFFSET_ORDER_ID);
        final long cmdUid = buffer.getLong(offset + CANCEL_OFFSET_UID);

        resultsBuffer.appendByte(commandCode);
        resultsBuffer.appendLong(cmdUid);
        resultsBuffer.appendLong(orderId);

        eventsHelper.appendResultCode(
                RESULT_UNKNOWN_SYMBOL,
                true,
                OrderAction.ASK, // arbitrary action, should be ignored
                false);
    }


    @Override
    public void sendL2Snapshot(DirectBuffer buffer, int offset) {
        resultsBuffer.appendByte(IOrderBook.QUERY_ORDER_BOOK);
        resultsBuffer.appendShort(RESULT_UNKNOWN_SYMBOL);
    }

    @Override
    public IOrder getOrderById(long orderId) {
        throw new IllegalStateException();
    }

    @Override
    public void verifyInternalState() {
        throw new IllegalStateException();

    }

    @Override
    public List<IOrder> findUserOrders(long uid) {
        throw new IllegalStateException();
    }

    @Override
    public T getSymbolSpec() {
        throw new IllegalStateException();
    }

    @Override
    public Stream<? extends IOrder> askOrdersStream(boolean sorted) {
        throw new IllegalStateException();
    }

    @Override
    public Stream<? extends IOrder> bidOrdersStream(boolean sorted) {
        throw new IllegalStateException();
    }

}

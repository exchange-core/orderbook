package exchange.core2.orderbook;

import exchange.core2.orderbook.events.ReduceEvent;
import exchange.core2.orderbook.events.TradeEvent;
import exchange.core2.orderbook.events.TradeEventsBlock;
import org.agrona.MutableDirectBuffer;

import static exchange.core2.orderbook.IOrderBook.*;

public final class ResponseDecoder {

    public static TradeEventsBlock readEventsBlock(final MutableDirectBuffer buf,
                                                   final int offset) {

        final long takerOrderId = buf.getLong(offset + RESPONSE_OFFSET_TBLK_TAKER_ORDER_ID);
        final long takerUid = buf.getLong(offset + RESPONSE_OFFSET_TBLK_TAKER_UID);
        final boolean takerOrderCompleted = buf.getByte(offset + RESPONSE_OFFSET_TBLK_TAKER_ORDER_COMPLETED) != 0;
        final OrderAction takerAction = OrderAction.of(buf.getByte(offset + RESPONSE_OFFSET_TBLK_TAKER_ACTION));
        final int tradeEventsNum = buf.getInt(offset + RESPONSE_OFFSET_TBLK_TRADES_EVT_NUM);
        final boolean hasReduceEvent = buf.getByte(offset + RESPONSE_OFFSET_TBLK_REDUCE_EVT) != 0;

        // build trade events
        final TradeEvent[] tradeEvents = new TradeEvent[tradeEventsNum];
        for (int i = 0; i < tradeEventsNum; i++) {
            tradeEvents[i] = readTradeEvent(buf, RESPONSE_OFFSET_TBLK_END + i * RESPONSE_OFFSET_TEVT_END);
        }

        // build reduce event
        final ReduceEvent reduceEvent = hasReduceEvent
                ? readReduceEvent(buf, RESPONSE_OFFSET_TBLK_END + tradeEventsNum * RESPONSE_OFFSET_TEVT_END)
                : null;

        return new TradeEventsBlock(takerOrderId, takerUid, takerAction, takerOrderCompleted, tradeEvents, reduceEvent);
    }

    private static TradeEvent readTradeEvent(final MutableDirectBuffer buf,
                                             final int offset) {

        final long makerOrderId = buf.getLong(offset + RESPONSE_OFFSET_TEVT_MAKER_ORDER_ID);
        final long makerUid = buf.getLong(offset + RESPONSE_OFFSET_TEVT_MAKER_UID);
        final long price = buf.getLong(offset + RESPONSE_OFFSET_TEVT_PRICE);
        final long reservedBidPrice = buf.getLong(offset + RESPONSE_OFFSET_TEVT_RESERV_BID_PRICE);
        final long tradeVolume = buf.getLong(offset + RESPONSE_OFFSET_TEVT_TRADE_VOL);
        final boolean takerCompleted = buf.getByte(offset + RESPONSE_OFFSET_TEVT_MAKER_ORDER_COMPLETED) != 0;

        return new TradeEvent(makerOrderId, makerUid, price, reservedBidPrice, tradeVolume, takerCompleted);

    }

    private static ReduceEvent readReduceEvent(final MutableDirectBuffer buf,
                                               final int offset) {
        final long price = buf.getLong(offset + RESPONSE_OFFSET_REVT_PRICE);
        final long reservedBidPrice = buf.getLong(offset + RESPONSE_OFFSET_REVT_RESERV_BID_PRICE);
        final long reducedVolume = buf.getLong(offset + RESPONSE_OFFSET_REVT_REDUCED_VOL);

        return new ReduceEvent(reducedVolume, price, reservedBidPrice);
    }

}

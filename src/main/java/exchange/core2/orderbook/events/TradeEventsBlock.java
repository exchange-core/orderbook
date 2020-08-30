package exchange.core2.orderbook.events;

import exchange.core2.orderbook.OrderAction;

import java.util.Arrays;
import java.util.Optional;

public class TradeEventsBlock {

    private final long takerOrderId;
    private final long takerUid;
    private final boolean takerOrderCompleted;
    private final OrderAction takerAction;
    private final TradeEvent[] trades;
    private final ReduceEvent reduceEvent;

    public TradeEventsBlock(long takerOrderId,
                            long takerUid,
                            OrderAction takerAction,
                            boolean takerOrderCompleted,
                            TradeEvent[] trades,
                            ReduceEvent reduceEvent) {

        this.takerOrderId = takerOrderId;
        this.takerUid = takerUid;
        this.takerAction = takerAction;
        this.takerOrderCompleted = takerOrderCompleted;
        this.trades = trades;
        this.reduceEvent = reduceEvent;
    }

    public long getTakerOrderId() {
        return takerOrderId;
    }

    public long getTakerUid() {
        return takerUid;
    }

    public OrderAction getTakerAction() {
        return takerAction;
    }

    public boolean isTakerOrderCompleted() {
        return takerOrderCompleted;
    }

    public TradeEvent[] getTrades() {
        return trades;
    }

    public Optional<ReduceEvent> getReduceEvent() {
        return Optional.ofNullable(reduceEvent);
    }

    @Override
    public String toString() {
        return "TradeEventsBlock{" +
                "takerOrderId=" + takerOrderId +
                ", takerUid=" + takerUid +
                ", takerOrderCompleted=" + takerOrderCompleted +
                ", takerAction=" + takerAction +
                ", trades=" + Arrays.toString(trades) +
                ", reduceEvent=" + reduceEvent +
                '}';
    }
}

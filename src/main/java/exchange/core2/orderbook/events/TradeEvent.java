package exchange.core2.orderbook.events;

import java.util.Objects;

public final class TradeEvent {

    private final long makerOrderId;
    private final long makerUid;
    private final long tradePrice;
    private final long reservedBidPrice;
    private final long tradeVolume;
    private final boolean makerOrderCompleted;

    public TradeEvent(long makerOrderId,
                      long makerUid,
                      long tradePrice,
                      long reservedBidPrice,
                      long tradeVolume,
                      boolean makerOrderCompleted) {

        this.makerOrderId = makerOrderId;
        this.makerUid = makerUid;
        this.tradePrice = tradePrice;
        this.reservedBidPrice = reservedBidPrice;
        this.tradeVolume = tradeVolume;
        this.makerOrderCompleted = makerOrderCompleted;
    }

    public long getMakerOrderId() {
        return makerOrderId;
    }

    public long getMakerUid() {
        return makerUid;
    }

    public long getTradePrice() {
        return tradePrice;
    }

    public long getReservedBidPrice() {
        return reservedBidPrice;
    }

    public long getTradeVolume() {
        return tradeVolume;
    }

    public boolean isMakerOrderCompleted() {
        return makerOrderCompleted;
    }

    @Override
    public String toString() {
        return "TradeEvent{" +
                "makerOrderId=" + makerOrderId +
                ", makerUid=" + makerUid +
                ", tradePrice=" + tradePrice +
                ", reservedBidPrice=" + reservedBidPrice +
                ", tradeVolume=" + tradeVolume +
                ", makerOrderCompleted=" + makerOrderCompleted +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeEvent that = (TradeEvent) o;
        return makerOrderId == that.makerOrderId &&
                makerUid == that.makerUid &&
                tradePrice == that.tradePrice &&
                reservedBidPrice == that.reservedBidPrice &&
                tradeVolume == that.tradeVolume &&
                makerOrderCompleted == that.makerOrderCompleted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(makerOrderId, makerUid, tradePrice, reservedBidPrice, tradeVolume, makerOrderCompleted);
    }
}

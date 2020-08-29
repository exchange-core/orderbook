package exchange.core2.orderbook.events;

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
}

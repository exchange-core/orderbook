package exchange.core2.orderbook.events;

public class ReduceEvent {

    private final long reducedVolume;
    private final long price;
    private final long reservedBidPrice;

    public ReduceEvent(long reducedVolume,
                       long price,
                       long reservedBidPrice) {

        this.reducedVolume = reducedVolume;
        this.price = price;
        this.reservedBidPrice = reservedBidPrice;
    }

    public long getReducedVolume() {
        return reducedVolume;
    }

    public long getPrice() {
        return price;
    }

    public long getReservedBidPrice() {
        return reservedBidPrice;
    }

    @Override
    public String toString() {
        return "ReduceEvent{" +
                "reducedVolume=" + reducedVolume +
                ", price=" + price +
                ", reservedBidPrice=" + reservedBidPrice +
                '}';
    }
}

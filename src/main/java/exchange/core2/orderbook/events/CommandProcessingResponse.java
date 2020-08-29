package exchange.core2.orderbook.events;

public class CommandProcessingResponse {

    private final int resultCode;
    private final TradeEventsBlock tradeEventsBlock;

    public CommandProcessingResponse(int resultCode, TradeEventsBlock tradeEventsBlock) {
        this.resultCode = resultCode;
        this.tradeEventsBlock = tradeEventsBlock;
    }

    public int getResultCode() {
        return resultCode;
    }

    public TradeEventsBlock getTradeEventsBlock() {
        return tradeEventsBlock;
    }
}

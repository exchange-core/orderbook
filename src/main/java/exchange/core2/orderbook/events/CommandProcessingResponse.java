package exchange.core2.orderbook.events;

import java.util.Optional;

public class CommandProcessingResponse {

    private final short resultCode;
    private final TradeEventsBlock tradeEventsBlock;

    public CommandProcessingResponse(short resultCode, TradeEventsBlock tradeEventsBlock) {
        this.resultCode = resultCode;
        this.tradeEventsBlock = tradeEventsBlock;
    }

    public short getResultCode() {
        return resultCode;
    }

    public Optional<TradeEventsBlock> getTradeEventsBlock() {
        return Optional.ofNullable(tradeEventsBlock);
    }

    @Override
    public String toString() {
        return "CommandProcessingResponse{" +
                "resultCode=" + resultCode +
                ", tradeEventsBlock=" + tradeEventsBlock +
                '}';
    }
}

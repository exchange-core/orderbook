package exchange.core2.orderbook.naive;

import exchange.core2.orderbook.IOrderBook;
import exchange.core2.orderbook.ISymbolSpecification;
import org.agrona.MutableDirectBuffer;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OrderBookNaiveImplTest extends OrderBookBaseTest {

    @Mock
    ISymbolSpecification spec;

    @Override
    protected IOrderBook createNewOrderBook(final MutableDirectBuffer resultsBuffer) {

        return new OrderBookNaiveImpl<>(getCoreSymbolSpec(), true, resultsBuffer);
    }

    @Override
    protected ISymbolSpecification getCoreSymbolSpec() {

        when(spec.isExchangeType()).thenReturn(true);

        return spec;
    }
}
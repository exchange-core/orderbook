/*
 * Copyright 2020 Maksim Zheravin
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

package exchange.core2.orderbook.naive;

import exchange.core2.orderbook.IOrderBook;
import exchange.core2.orderbook.ISymbolSpecification;
import exchange.core2.orderbook.util.BufferWriter;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OrderBookNaiveImplTest extends OrderBookBaseTest {

    @Mock
    ISymbolSpecification spec;

    @Override
    protected IOrderBook createNewOrderBook(final BufferWriter bufferWriter) {

        return new OrderBookNaiveImpl<>(getCoreSymbolSpec(), false, bufferWriter);
    }

    @Override
    protected ISymbolSpecification getCoreSymbolSpec() {

        when(spec.isExchangeType()).thenReturn(true);

        return spec;
    }
}
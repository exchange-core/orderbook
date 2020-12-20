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

package exchange.core2.orderbook.events;

import exchange.core2.orderbook.OrderAction;

import java.util.List;
import java.util.Optional;

public final class CommandResponseMove extends CommandResponse {

    private final Long remainingSize;

    public CommandResponseMove(final short resultCode,
                               final long uid,
                               final long orderId,
                               final OrderAction takerAction,
                               final boolean orderCompleted,
                               final Long remainingSize,
                               final List<TradeEvent> trades,
                               final ReduceEvent reduceEvent) {

        super(resultCode, uid, orderId, takerAction, orderCompleted, trades, reduceEvent);

        this.remainingSize = remainingSize;
    }

    public Optional<Long> getRemainingSizeOpt() {
        return Optional.ofNullable(remainingSize);
    }
}

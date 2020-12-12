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

package exchange.core2.orderbook.util;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;

public class BufferReader {

    private final DirectBuffer buffer;
    private final int initialPosition;
    private int readPosition;

    // TODO limit reader position on creation

    public BufferReader(final DirectBuffer buffer, final int initialPosition) {

        this.buffer = buffer;
        this.initialPosition = initialPosition;
        this.readPosition = initialPosition;
    }

    public DirectBuffer getBuffer() {
        return buffer;
    }

    public int getInitialPosition() {
        return initialPosition;
    }

    public int getReadPosition() {
        return readPosition;
    }

    public byte readByte() {
        final byte b = buffer.getByte(readPosition);
        readPosition += BitUtil.SIZE_OF_BYTE;
        return b;
    }

    public short readShort() {
        final short s = buffer.getShort(readPosition);
        readPosition += BitUtil.SIZE_OF_SHORT;
        return s;
    }

    public int readInt() {
        final int i = buffer.getInt(readPosition);
        readPosition += BitUtil.SIZE_OF_INT;
        return i;
    }

    public long readLong() {
        final long w = buffer.getLong(readPosition);
        readPosition += BitUtil.SIZE_OF_LONG;
        return w;
    }

}

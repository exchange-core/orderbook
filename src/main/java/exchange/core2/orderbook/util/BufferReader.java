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
import org.agrona.PrintBufferUtil;

public class BufferReader {

    private final DirectBuffer buffer;

    // initial position (start of message)
    private final int initialPosition;

    // message size
    private final int size;

    // position pointer for sequential reading
    private int readPosition;

    // TODO limit reader position on creation

    public BufferReader(final DirectBuffer buffer, final int size, final int initialPosition) {

        this.buffer = buffer;
        this.initialPosition = initialPosition;
        this.size = size;

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

    public int getSize() {
        return size;
    }

    public int getRemainingSize() {
        return size - (readPosition - initialPosition);
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

    public byte getByte(final int offset) {
        final byte b = buffer.getByte(initialPosition + offset);
        return b;
    }

    public short getShort(final int offset) {
        final short s = buffer.getShort(initialPosition + offset);
        return s;
    }

    public int getInt(final int offset) {
        final int i = buffer.getInt(initialPosition + offset);
        return i;
    }

    public long getLong(final int offset) {
        final long w = buffer.getLong(initialPosition + offset);
        return w;
    }

    public String prettyHexDump() {
        return PrintBufferUtil.prettyHexDump(buffer, initialPosition, size);
    }
}

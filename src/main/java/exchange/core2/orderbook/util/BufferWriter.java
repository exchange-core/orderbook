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
import org.agrona.MutableDirectBuffer;
import org.agrona.PrintBufferUtil;

public class BufferWriter {

    private final MutableDirectBuffer buffer;
    private final int initialPosition;
    private int writerPosition;

    public BufferWriter(final MutableDirectBuffer buffer, final int initialPosition) {

        this.buffer = buffer;
        this.initialPosition = initialPosition;
        this.writerPosition = initialPosition;
    }

    public MutableDirectBuffer getBuffer() {
        return buffer;
    }

    public int getInitialPosition() {
        return initialPosition;
    }

    public int getWriterPosition() {
        return writerPosition;
    }

    public void skipBytes(int bytesToSkip) {
        writerPosition += bytesToSkip;
    }

    public void reset() {
        writerPosition = initialPosition;
    }

    public void appendByte(final byte b) {
        buffer.putByte(writerPosition, b);
        writerPosition += BitUtil.SIZE_OF_BYTE;
    }

    public void appendShort(final short s) {
        buffer.putShort(writerPosition, s);
        writerPosition += BitUtil.SIZE_OF_SHORT;
    }

    public void appendInt(final int i) {
        buffer.putInt(writerPosition, i);
        writerPosition += BitUtil.SIZE_OF_INT;
    }

    public void appendLong(final long w) {
        buffer.putLong(writerPosition, w);
        writerPosition += BitUtil.SIZE_OF_LONG;
    }

    public void overwriteByte(final int offset, final byte b) {
        buffer.putByte(offset, b);
    }

    public void overwriteShort(final int offset, final short s) {
        buffer.putShort(offset, s);
    }

    public void overwriteInt(final int offset, final int i) {
        buffer.putInt(offset, i);
    }

    public void overwriteLong(final int offset, final long w) {
        buffer.putLong(offset, w);
    }

    public void appendBytesFromReader(final BufferReader reader, final int length) {
        reader.getBuffer().getBytes(reader.getReadPosition(), buffer, writerPosition, length);
        writerPosition += length;
        reader.skipBytes(length);
    }

    public byte[] getBytes() {
        final int length = writerPosition - initialPosition;
        final byte[] array = new byte[length];
        buffer.getBytes(initialPosition, array);
        return array;
    }

    /**
     * Zero copy conversion to reader.
     * Writer can not be used anymore
     *
     * @return buffer reader
     */
    public BufferReader toReader() {
        final int size = writerPosition - initialPosition;
        return new BufferReader(buffer, size, initialPosition);
    }

    public String prettyHexDump() {
        return PrintBufferUtil.prettyHexDump(buffer, initialPosition, writerPosition);
    }

}

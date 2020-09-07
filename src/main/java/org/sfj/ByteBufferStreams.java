/*
 * Copyright 2020 C. Schanck
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
package org.sfj;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.function.BiFunction;

/**
 * So, this is a useful pair of classes for presenting {@link ByteBuffer} objects
 * as {@link java.io.InputStream}/{@link OutputStream} objects. The class extends
 * the input and output stream classes, as well as {@link DataInput}/{@link DataOutput}.
 * <p>For the input class, you can change the underlying buffer when needed, and access
 * the current working buffer.
 * <p>For the output class, if the underlying ByteBuffer runs out of room, a callback
 * is made to allow the user to provide a new buffer with enough room.
 * <p>These classes also include primitives for directly writing/reading ByteBuffers
 * into the stream.
 * <p>These classes are <b>NOT</b> in anyway thread safe, not a single synchronized
 * access anywhere; since the underlying ByteBuffer is not thread safe, seemed pointless.
 * If you need concurrency, wrap these classes.
 */
public interface ByteBufferStreams {

  /**
   * InputStream/DataInput over ByteBuffer. Also adds a primitive for directly
   * writing ByteBuffers.
   */
  class Input extends java.io.InputStream implements DataInput {
    private ByteBuffer source;

    /**
     * Constructor.
     * @param source initial buffer. null allowed.
     */
    public Input(ByteBuffer source) {
      super();
      this.source = source;
    }

    /**
     * Set the underlying buffer.
     * @param source new buffer
     */
    public void setBuffer(ByteBuffer source) {
      this.source = source;
    }

    private void checkRoom(int n) throws EOFException {
      if (source == null || source.remaining() < n) {
        throw new EOFException();
      }
    }

    @Override
    public int read(byte[] b) throws IOException {
      return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int m = Math.max(0, Math.min(len, source.remaining()));
      readFully(b, off, m);
      return m;
    }

    @Override
    public long skip(long n) {
      if (n < Integer.MAX_VALUE) {
        int m = Math.max(0, Math.min((int) n, source.remaining()));
        source.position(source.position() + m);
        return m;
      }
      return 0;
    }

    @Override
    public int available() {
      return source == null ? 0 : source.remaining();
    }

    @Override
    public void close() {
      // noop
    }

    @Override
    public void mark(int readlimit) {
      // noop
    }

    @Override
    public void reset() {
      // noop
    }

    @Override
    public boolean markSupported() {
      return false;
    }

    @Override
    public int read() throws IOException {
      return readUnsignedByte();
    }

    @Override
    public void readFully(byte[] b) throws IOException {
      checkRoom(b.length);
      source.get(b);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
      checkRoom(len);
      source.get(b, off, len);
    }

    @Override
    public int skipBytes(int n) {
      n = Math.min(source.remaining(), n);
      source.position(source.position() + n);
      return n;
    }

    @Override
    public boolean readBoolean() throws IOException {
      checkRoom(1);
      return (source.get() != 0);
    }

    @Override
    public byte readByte() throws IOException {
      checkRoom(1);
      return source.get();
    }

    @Override
    public int readUnsignedByte() throws IOException {
      checkRoom(1);
      return ((int) source.get()) & 0xff;
    }

    @Override
    public short readShort() throws IOException {
      checkRoom(2);
      int ch1 = source.get() & 0xff;
      int ch2 = source.get() & 0xff;
      return (short) ((ch1 << 8) + ch2);
    }

    @Override
    public int readUnsignedShort() throws IOException {
      checkRoom(2);
      int ch1 = source.get() & 0xff;
      int ch2 = source.get() & 0xff;
      return ((ch1 << 8) + ch2) & 0xffff;
    }

    @Override
    public char readChar() throws IOException {
      checkRoom(2);
      int ch1 = source.get() & 0xff;
      int ch2 = source.get() & 0xff;
      return (char) ((ch1 << 8) + ch2);
    }

    @Override
    public int readInt() throws IOException {
      checkRoom(4);
      int ch1 = source.get() & 0xff;
      int ch2 = source.get() & 0xff;
      int ch3 = source.get() & 0xff;
      int ch4 = source.get() & 0xff;
      return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4));
    }

    @Override
    public long readLong() throws IOException {
      checkRoom(8);
      int ch1 = source.get() & 0xff;
      int ch2 = source.get() & 0xff;
      int ch3 = source.get() & 0xff;
      int ch4 = source.get() & 0xff;
      int ch5 = source.get() & 0xff;
      int ch6 = source.get() & 0xff;
      int ch7 = source.get() & 0xff;
      int ch8 = source.get() & 0xff;
      return (((long) ch1 << 56) +
              ((long) ch2 << 48) +
              ((long) ch3 << 40) +
              ((long) ch4 << 32) +
              ((long) ch5 << 24) +
              (ch6 << 16) +
              (ch7 << 8) +
              (ch8));
    }

    @Override
    public float readFloat() throws IOException {
      return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() throws IOException {
      return Double.longBitsToDouble(readLong());
    }

    @Override
    @Deprecated
    public String readLine() {
      throw new UnsupportedOperationException("readLine not supported");
    }

    @Override
    public String readUTF() throws IOException {
      try {
        return DataInputStream.readUTF(this);
      } catch (BufferOverflowException | BufferUnderflowException | IndexOutOfBoundsException e) {
        throw new IOException(e);
      }
    }

    /**
     * Additional ByteBuffer primitive read.
     * @param dest destination byte buffer
     * @throws IOException on error
     */
    public void read(ByteBuffer dest) throws IOException {
      checkRoom(dest.remaining());
      dest.put(source);
    }
  }

  /**
   * Output stream class over ByteBuffer.
   */
  class Output extends OutputStream implements DataOutput {
    private ByteBuffer dest;
    private final BiFunction<Output, Integer, ByteBuffer> onFull;

    public Output(ByteBuffer dest) {
      this(dest, null);
    }

    /**
     * Create a ByteBuffer output stream. You provide a buffer.
     * @param dest initial buffer (null allowed)
     * @param onFull callback to invoke on full. The callback will be called
     * with the {@link Output} object and the number of bytes of room needed,
     * and must return a buffer capable of ingesting that many bytes. The prior buffer
     * will simply be tossed away, so if you are going to grow the buffer in place,
     * you need to copy data, or you can take the buffer and send it on it's way.
     */
    public Output(ByteBuffer dest, BiFunction<Output, Integer, ByteBuffer> onFull) {
      this.dest = dest;
      this.onFull = onFull;
    }

    @Override
    public void flush() {
      // noop;
    }

    @Override
    public void close() {
      // noop
    }

    /**
     * Get the current working buffer for this stream.
     * @return buffer
     */
    public ByteBuffer getBuffer() {
      return dest;
    }

    /**
     * Set the buffer for this stream. Replaces existing buffer.
     * @param dest new buffer
     */
    public void setBuffer(ByteBuffer dest) {
      this.dest = dest;
    }

    private void checkRoom(int n) throws IOException {
      if (dest != null && dest.remaining() < n) {
        if (onFull == null) {
          throw new IOException("Overflow1; needed: " + n);
        }
        ByteBuffer p = onFull.apply(this, n);
        if (p == null || p.remaining() < n) {
          throw new IOException("Overflow2; needed: " + n);
        }
        dest = p;
      }
    }

    @Override
    public void write(int b) throws IOException {
      checkRoom(1);
      dest.put((byte) (b & 0xff));
    }

    @Override
    public void write(byte[] b) throws IOException {
      checkRoom(b.length);
      dest.put(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      checkRoom(len);
      dest.put(b, off, len);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
      checkRoom(1);
      dest.put((byte) (v ? 1 : 0));
    }

    @Override
    public void writeByte(int v) throws IOException {
      checkRoom(1);
      dest.put((byte) v);
    }

    @Override
    public void writeShort(int v) throws IOException {
      checkRoom(2);
      dest.put((byte) ((v >>> 8) & 0xff));
      dest.put((byte) ((v) & 0xff));
    }

    @Override
    public void writeChar(int v) throws IOException {
      checkRoom(2);
      dest.put((byte) ((v >>> 8) & 0xff));
      dest.put((byte) ((v) & 0xff));
    }

    @Override
    public void writeInt(int v) throws IOException {
      checkRoom(4);
      dest.put((byte) ((v >>> 24) & 0xff));
      dest.put((byte) ((v >>> 16) & 0xff));
      dest.put((byte) ((v >>> 8) & 0xff));
      dest.put((byte) ((v) & 0xff));
    }

    @Override
    public void writeLong(long v) throws IOException {
      checkRoom(8);
      dest.put((byte) ((v >>> 56) & 0xff));
      dest.put((byte) ((v >>> 48) & 0xff));
      dest.put((byte) ((v >>> 40) & 0xff));
      dest.put((byte) ((v >>> 32) & 0xff));
      dest.put((byte) ((v >>> 24) & 0xff));
      dest.put((byte) ((v >>> 16) & 0xff));
      dest.put((byte) ((v >>> 8) & 0xff));
      dest.put((byte) ((v) & 0xff));
    }

    @Override
    public void writeFloat(float v) throws IOException {
      writeInt(Float.floatToIntBits(v));
    }

    @Override
    public void writeDouble(double v) throws IOException {
      writeLong(Double.doubleToLongBits(v));
    }

    @Override
    public void writeBytes(String s) throws IOException {
      int len = s.length();
      checkRoom(len);
      for (int i = 0; i < len; i++) {
        dest.put((byte) s.charAt(i));
      }
    }

    @Override
    public void writeChars(String s) throws IOException {
      int len = s.length();
      for (int i = 0; i < len; i++) {
        writeChar(s.charAt(i));
      }
    }

    @Override
    public void writeUTF(String s) throws IOException {
      int stringLen = s.length();
      int bytesLen = 0;
      int ch;

      // calculate length
      for (int i = 0; i < stringLen; i++) {
        ch = s.charAt(i);
        if ((ch >= 0x0001) && (ch <= 0x007f)) {
          bytesLen++;
        } else if (ch > 0x07ff) {
          bytesLen += 3;
        } else {
          bytesLen += 2;
        }
      }

      if (bytesLen > 65535) {
        throw new UTFDataFormatException("string too long: " + bytesLen);
      }

      // fits?
      checkRoom(bytesLen + 2);

      // write length as an unsigned short
      dest.put((byte) ((bytesLen >>> 8) & 0xff));
      dest.put((byte) ((bytesLen) & 0xff));

      // first write the ascii ones fast as can be. lots of times this will be it.
      int i;
      for (i = 0; i < stringLen; i++) {
        ch = s.charAt(i);
        if (!((ch >= 0x0001) && (ch <= 0x007f))) {
          break;
        }
        dest.put((byte) ch);
      }

      // from now on, handle utf chars
      for (; i < stringLen; i++) {
        ch = s.charAt(i);
        if ((ch >= 0x0001) && (ch <= 0x007f)) {
          dest.put((byte) ch);
        } else if (ch > 0x07ff) {
          dest.put((byte) (0xe0 | ((ch >> 12) & 0x0f)));
          dest.put((byte) (0x80 | ((ch >> 6) & 0x3f)));
          dest.put((byte) (0x80 | ((ch) & 0x3f)));
        } else {
          dest.put((byte) (0xc0 | ((ch >> 6) & 0x1f)));
          dest.put((byte) (0x80 | ((ch) & 0x3f)));
        }
      }
    }

    /**
     * Primitive to write a ByteBuffer directly into the stream.
     * @param in buffer to write.
     * @throws IOException on error
     */
    public void write(ByteBuffer in) throws IOException {
      checkRoom(in.remaining());
      dest.put(in);
    }
  }
}

package org.sfj;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ByteBufferStreamsTest {

  // in no way is this actually enough. but hey.... it's a start
  @Test
  public void testBasic() throws IOException {
    ByteBufferStreams.Output os = new ByteBufferStreams.Output(ByteBuffer.allocate(1024));
    os.write(10);
    os.writeBoolean(true);
    os.writeByte(1);
    os.writeChar('v');
    os.writeFloat(1.1f);
    os.writeDouble(2.2d);
    os.writeChars("Hello");
    os.writeInt(100);
    os.writeShort(10);
    os.writeLong(Integer.MAX_VALUE + 10L);
    os.write(new byte[] { 10, 4, -11 });
    os.writeUTF("Bool");
    ByteBuffer p = os.getBuffer();
    p.flip();
    ByteBufferStreams.Input istream = new ByteBufferStreams.Input(p);
    assertThat(istream.read(), is(10));
    assertThat(istream.readBoolean(), is(true));
    assertThat(istream.readByte(), is((byte) 1));
    assertThat(istream.readChar(), is('v'));
    assertThat(istream.readFloat(), is(1.1f));
    assertThat(istream.readDouble(), is(2.2d));
    assertThat(istream.readChar(), is('H'));
    assertThat(istream.readChar(), is('e'));
    assertThat(istream.readChar(), is('l'));
    assertThat(istream.readChar(), is('l'));
    assertThat(istream.readChar(), is('o'));
    assertThat(istream.readInt(), is(100));
    assertThat(istream.readShort(), is((short) 10));
    assertThat(istream.readLong(), is(Integer.MAX_VALUE + 10L));
    byte[] b = new byte[3];
    istream.readFully(b);
    assertThat(b[0], is((byte) 10));
    assertThat(b[1], is((byte) 4));
    assertThat(b[2], is((byte) -11));
    assertThat(istream.readUTF(), is("Bool"));

    try {
      istream.read();
      Assert.fail();
    } catch (IOException e) {}

    try {
      istream.readBoolean();
      Assert.fail();
    } catch (IOException e) {}

    try {
      istream.readByte();
      Assert.fail();
    } catch (IOException e) {}

    try {
      istream.readChar();
      Assert.fail();
    } catch (IOException e) {}

    try {
      istream.readFloat();
      Assert.fail();
    } catch (IOException e) {}

    try {
      istream.readDouble();
      Assert.fail();
    } catch (IOException e) {}

    try {
      istream.readChar();
      Assert.fail();
    } catch (IOException e) {}

    try {
      istream.readInt();
      Assert.fail();
    } catch (IOException e) {}
    try {
      istream.readShort();
      Assert.fail();
    } catch (IOException e) {}
    try {
      istream.readLong();
      Assert.fail();
    } catch (IOException e) {}

  }

  @Test
  public void testInteropByteBufferToDataInputStream() throws IOException {
    ByteBufferStreams.Output os = new ByteBufferStreams.Output(ByteBuffer.allocate(1024));
    os.write(10);
    os.writeBoolean(true);
    os.writeByte(1);
    os.writeChar('v');
    os.writeFloat(1.1f);
    os.writeDouble(2.2d);
    os.writeChars("Hello");
    os.writeInt(100);
    os.writeShort(10);
    os.writeLong(Integer.MAX_VALUE + 10L);
    os.write(new byte[] { 10, 4, -11 });
    os.writeUTF("Bool");
    ByteBuffer p = os.getBuffer();
    p.flip();
    ByteArrayInputStream bais = new ByteArrayInputStream(p.array(), 0, p.remaining());
    DataInputStream istream = new DataInputStream(bais);
    assertThat(istream.read(), is(10));
    assertThat(istream.readBoolean(), is(true));
    assertThat(istream.readByte(), is((byte) 1));
    assertThat(istream.readChar(), is('v'));
    assertThat(istream.readFloat(), is(1.1f));
    assertThat(istream.readDouble(), is(2.2d));
    assertThat(istream.readChar(), is('H'));
    assertThat(istream.readChar(), is('e'));
    assertThat(istream.readChar(), is('l'));
    assertThat(istream.readChar(), is('l'));
    assertThat(istream.readChar(), is('o'));
    assertThat(istream.readInt(), is(100));
    assertThat(istream.readShort(), is((short) 10));
    assertThat(istream.readLong(), is(Integer.MAX_VALUE + 10L));
    byte[] b = new byte[3];
    istream.readFully(b);
    assertThat(b[0], is((byte) 10));
    assertThat(b[1], is((byte) 4));
    assertThat(b[2], is((byte) -11));
    assertThat(istream.readUTF(), is("Bool"));
  }

  @Test
  public void testInteropDataOutputStreamToByteBuffer() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream os = new DataOutputStream(baos);
    os.write(10);
    os.writeBoolean(true);
    os.writeByte(1);
    os.writeChar('v');
    os.writeFloat(1.1f);
    os.writeDouble(2.2d);
    os.writeChars("Hello");
    os.writeInt(100);
    os.writeShort(10);
    os.writeLong(Integer.MAX_VALUE + 10L);
    os.write(new byte[] { 10, 4, -11 });
    os.writeUTF("Bool");
    os.flush();
    byte[] bb = baos.toByteArray();
    ByteBufferStreams.Input istream = new ByteBufferStreams.Input(ByteBuffer.wrap(bb));
    assertThat(istream.read(), is(10));
    assertThat(istream.readBoolean(), is(true));
    assertThat(istream.readByte(), is((byte) 1));
    assertThat(istream.readChar(), is('v'));
    assertThat(istream.readFloat(), is(1.1f));
    assertThat(istream.readDouble(), is(2.2d));
    assertThat(istream.readChar(), is('H'));
    assertThat(istream.readChar(), is('e'));
    assertThat(istream.readChar(), is('l'));
    assertThat(istream.readChar(), is('l'));
    assertThat(istream.readChar(), is('o'));
    assertThat(istream.readInt(), is(100));
    assertThat(istream.readShort(), is((short) 10));
    assertThat(istream.readLong(), is(Integer.MAX_VALUE + 10L));
    byte[] b = new byte[3];
    istream.readFully(b);
    assertThat(b[0], is((byte) 10));
    assertThat(b[1], is((byte) 4));
    assertThat(b[2], is((byte) -11));
    assertThat(istream.readUTF(), is("Bool"));
  }

  @Test
  public void testExpansion() throws IOException {
    AtomicInteger expandos = new AtomicInteger(0);
    ByteBufferStreams.Output os = new ByteBufferStreams.Output(ByteBuffer.allocate(16), (o, n) -> {
      ByteBuffer newB = ByteBuffer.allocate(o.getBuffer().capacity() + n);
      ByteBuffer old = o.getBuffer();
      old.flip();
      newB.put(old);
      expandos.incrementAndGet();
      return newB;
    });

    for (int i = 0; i < 10; i++) {
      os.writeInt(i);
    }

    assertThat(os.getBuffer().position(), is(40));
    assertThat(expandos.get(), is(6));

    ByteBufferStreams.Input istream = new ByteBufferStreams.Input((ByteBuffer) os.getBuffer().flip());
    for (int i = 0; i < 10; i++) {
      assertThat(istream.readInt(), is(i));
    }
  }
}

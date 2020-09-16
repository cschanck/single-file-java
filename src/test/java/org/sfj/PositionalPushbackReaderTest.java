package org.sfj;

import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class PositionalPushbackReaderTest {

  @Test
  public void testSimpleRead() throws IOException {
    PositionalPushbackReader r = new PositionalPushbackReader("0123456789");
    for (int i = 0; i < 10; i++) {
      assertThat((char) r.read(), is((i + "").charAt(0)));
      assertThat(r.getLineNumber(), is(1));
      assertThat(r.getColumnPosition(), is(i + 1));
    }
    assertThat(r.read(), is(-1));
  }

  @Test
  public void testMultiline() throws IOException {
    PositionalPushbackReader r = new PositionalPushbackReader("01234\n56789");
    for (int i = 0; i < 5; i++) {
      assertThat((char) r.read(), is((i + "").charAt(0)));
      assertThat(r.getLineNumber(), is(1));
      assertThat(r.getColumnPosition(), is(i + 1));
    }

    assertThat((char) r.read(), is('\n'));
    assertThat(r.getLineNumber(), is(1));
    assertThat(r.getColumnPosition(), is(6));

    for (int i = 5; i < 10; i++) {
      assertThat((char) r.read(), is((i + "").charAt(0)));
      assertThat(r.getLineNumber(), is(2));
      assertThat(r.getColumnPosition(), is(i + 1 - 5));
    }
    assertThat(r.read(), is(-1));
  }

  @Test
  public void testSimplePushback() throws IOException {
    PositionalPushbackReader r = new PositionalPushbackReader("0123456789");
    for (int i = 0; i < 8; i++) {
      assertThat((char) r.read(), is((i + "").charAt(0)));
      assertThat(r.getLineNumber(), is(1));
      assertThat(r.getColumnPosition(), is(i + 1));
    }
    r.pushback('7');
    r.pushback('6');
    r.pushback('5');
    for (int i = 5; i < 10; i++) {
      char ch = (char) r.read();
      assertThat(ch, is((i + "").charAt(0)));
      assertThat(r.getLineNumber(), is(1));
      assertThat(r.getColumnPosition(), is(i + 1));
    }

    assertThat(r.read(), is(-1));
  }

  @Test
  public void testMultilinePushback() throws IOException {
    PositionalPushbackReader r = new PositionalPushbackReader("0123\n456\n7890");
    for (int i = 0; i < 4; i++) {
      assertThat((char) r.read(), is((i + "").charAt(0)));
      assertThat(r.getLineNumber(), is(1));
      assertThat(r.getColumnPosition(), is(i + 1));
    }
    assertThat((char) r.read(), is('\n'));
    assertThat(r.getLineNumber(), is(1));
    assertThat(r.getColumnPosition(), is(5));
    for (int i = 4; i < 7; i++) {
      assertThat((char) r.read(), is((i + "").charAt(0)));
      assertThat(r.getLineNumber(), is(2));
      assertThat(r.getColumnPosition(), is(i + 1 - 4));
    }
    assertThat((char) r.read(), is('\n'));
    assertThat(r.getLineNumber(), is(2));
    assertThat(r.getColumnPosition(), is(4));

    assertThat((char) r.read(), is('7'));
    assertThat(r.getLineNumber(), is(3));
    assertThat(r.getColumnPosition(), is(1));

    assertThat((char) r.read(), is('8'));
    assertThat(r.getLineNumber(), is(3));
    assertThat(r.getColumnPosition(), is(2));

    r.pushback('8');
    r.pushback('7');
    r.pushback('\n');
    r.pushback('6');
    r.pushback('5');
    r.pushback('4');
    r.pushback('\n');
    r.pushback('3');

    assertThat(r.getLineNumber(), is(1));
    assertThat(r.getColumnPosition(), is(3));

    assertThat((char) r.read(), is('3'));
    assertThat(r.getLineNumber(), is(1));
    assertThat(r.getColumnPosition(), is(4));

    assertThat((char) r.read(), is('\n'));
    assertThat(r.getLineNumber(), is(1));
    assertThat(r.getColumnPosition(), is(5));

    assertThat((char) r.read(), is('4'));
    assertThat(r.getLineNumber(), is(2));
    assertThat(r.getColumnPosition(), is(1));

    assertThat((char) r.read(), is('5'));
    assertThat(r.getLineNumber(), is(2));
    assertThat(r.getColumnPosition(), is(2));

    assertThat((char) r.read(), is('6'));
    assertThat(r.getLineNumber(), is(2));
    assertThat(r.getColumnPosition(), is(3));

    assertThat((char) r.read(), is('\n'));
    assertThat(r.getLineNumber(), is(2));
    assertThat(r.getColumnPosition(), is(4));

    assertThat((char) r.read(), is('7'));
    assertThat(r.getLineNumber(), is(3));
    assertThat(r.getColumnPosition(), is(1));

    assertThat((char) r.read(), is('8'));
    assertThat(r.getLineNumber(), is(3));
    assertThat(r.getColumnPosition(), is(2));

    assertThat((char) r.read(), is('9'));
    assertThat(r.getLineNumber(), is(3));
    assertThat(r.getColumnPosition(), is(3));

    assertThat((char) r.read(), is('0'));
    assertThat(r.getLineNumber(), is(3));
    assertThat(r.getColumnPosition(), is(4));

    assertThat(r.read(), is(-1));

  }

}

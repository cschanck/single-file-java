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

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;

/**
 * This is a basically an infinite pushback Reader, which handles line endings
 * resiliently. It will not be super fast, as all calls end up going through
 * the single char read() method, but it will keep line/column position properly
 * across line endings, across character and line endings being pushed back, etc.
 * <p>Since it is inifinite, it keeps track of the line lengths of all read lines in
 * a humpty-dumpty linked list, so when newlines are pushed back, the column position
 * is maintained. A similarly linked list is used for pushback chars. I am not certain
 * of the need for a hand rolled linked list for this, but the overhead of boxing
 * seems blah.
 * <p>End-of-line is denoted by newlines only, and only newlines should be pushed back.
 * <p>It would have been nice to fit this into PegLeg, as it would be much
 * more efficient for parsing.
 */
public class PositionalPushbackReader extends LineNumberReader {

  private static class IntNode {
    IntNode next;
    int val;

    public IntNode(int val) {
      this.val = val;
    }
  }

  private static class IntQueue {
    private IntNode head = null;
    private IntNode tail = null;

    public void clear() {
      head = tail = null;
    }

    void push(int val) {
      IntNode p = new IntNode(val);
      if (head == null) {
        head = tail = p;
      } else if (head == tail) {
        p.next = tail;
        head = p;
      } else {
        p.next = head;
        head = p;
      }
    }

    int pop() {
      if (head == null) {
        throw new IllegalStateException();
      }
      int ret = head.val;
      if (head == tail) {
        head = tail = null;
      } else {
        head = head.next;
      }
      return ret;
    }

    boolean isEmpty() {
      return head == null;
    }
  }

  private final IntQueue pushback = new IntQueue();
  private final IntQueue lineLengths = new IntQueue();
  private int latestReadPosInLine = 0;
  private int latestReadLineNumber = 0;
  private boolean lastReadWasNewline = false;

  public PositionalPushbackReader(String source) {
    this(new StringReader(source));
  }

  public PositionalPushbackReader(Reader r) {
    super(r);
  }

  /**
   * Core method. Reads a char, possibly from pushback, return a char, or -1
   * on end of input.
   * @return char as int, or -1 on end of input.
   * @throws IOException
   */
  public int read() throws IOException {
    int ch = pushback.isEmpty() ? super.read() : pushback.pop();
    if (ch == -1) { return -1; }
    if (lastReadWasNewline) {
      lastReadWasNewline = false;
      latestReadPosInLine = 0;
      latestReadLineNumber++;
    }
    if (ch == '\n') {
      lastReadWasNewline = true;
      latestReadPosInLine++;
      lineLengths.push(latestReadPosInLine);
    } else {
      lastReadWasNewline = false;
      latestReadPosInLine++;
    }
    return ch;
  }

  /**
   * Pushback a character. Use '\n' newline for end of line. If you
   * pushback a char that was not read, the line number/column position
   * maintanence behavior is undefined.
   * @param ch char to pushback.
   */
  public void pushback(int ch) {
    if (ch == -1) { throw new IllegalArgumentException(); }
    if (--latestReadPosInLine == 0) {
      latestReadLineNumber--;
      latestReadPosInLine = lineLengths.pop();
      lastReadWasNewline = true;
    } else {
      lastReadWasNewline = false;
    }
    pushback.push(ch);
  }

  @Override
  public long skip(long n) throws IOException {
    int cnt = 0;
    while (cnt < n) {
      int ch = read();
      if (ch == -1) { break; }
      cnt++;
    }
    return cnt;
  }

  @Override
  public boolean ready() throws IOException {
    return !pushback.isEmpty() || super.ready();
  }

  @Override
  public void reset() throws IOException {
    pushback.clear();
    latestReadLineNumber = 0;
    latestReadPosInLine = 0;
    super.reset();
  }

  @Override
  public int read(CharBuffer target) throws IOException {
    return super.read(target);
  }

  @Override
  public int read(char[] cbuf) throws IOException {
    return read(cbuf, 0, cbuf.length);
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public void mark(int readAheadLimit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    int cnt = 0;
    while (cnt < len) {
      int ch = read();
      if (ch == -1) { break; }
      cnt++;
      cbuf[off + cnt] = (char) ch;
    }
    return cnt;
  }

  @Override
  public void close() throws IOException {
    pushback.clear();
    super.close();
  }

  /**
   * Position of last read character in the current line. 1 is the first position.
   * @return position
   */
  public int getColumnPosition() {
    return latestReadPosInLine;
  }

  /**
   * Line number of last read character. 1 indexed.
   * @return line number
   */
  public int getLineNumber() {
    return latestReadLineNumber + 1;
  }

  @Override
  public void setLineNumber(int lineNumber) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String readLine() throws IOException {
    StringBuilder sb = new StringBuilder();

    int ch = read();
    if (ch == -1) {
      return null;
    }
    for (; ch != -1 && ((char) ch != '\n'); ch = read()) {
      sb.append(ch);
    }
    return sb.toString();
  }
}

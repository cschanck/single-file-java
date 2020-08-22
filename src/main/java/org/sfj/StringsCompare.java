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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Compare two string sequences (files, resource streams, anything that
 * can be expressed as sequence of lines). Possibly ignore line endings,
 * possible ignore leading and/or trailing whitespace, possibly ignore case.
 * <p>Useful for testing when you have an expected file as a resource, say, and
 * need to compare it to output of some execution.
 */
public class StringsCompare {
  /**
   * A source of lines. Essentially, an iterator with IOExceptions. Also
   * supports the use of predicates to skip lines testing true.
   */
  interface LineSource extends Closeable {
    /**
     * Has a line?
     * @return ture if another line exists.
     * @throws IOException on error
     */
    boolean hasLine() throws IOException;

    /**
     * Fetch next line.
     * @return next line
     * @throws IOException on error
     */
    String nextLine() throws IOException;

    @Override
    default void close() throws IOException {

    }
  }

  /**
   * Predicate to skip no lines.
   */
  public static Predicate<String> SKIP_NONE = (s) -> false;

  /**
   * Predicate to skip whitespace only lines.
   */
  public static Predicate<String> SKIP_BLANK = (s) -> s.trim().length() == 0;

  /**
   * Predicate to skip empty lines.
   */
  public static Predicate<String> SKIP_EMPTY = (s) -> s.length() == 0;

  /**
   * Ident transform, use when you want no mapping done on strings.
   */
  public static final Function<String, String> IDENT = (s) -> s;

  /**
   * Mapping transform to remove leading whitespace.
   */
  public static final Function<String, String> TRIM_LEADING_WHITESPACE = (s) -> {
    int i = 0;
    for (; i < s.length(); i++) {
      if (!Character.isWhitespace(s.charAt(i))) {
        break;
      }
    }
    if (i > 0) {
      return s.substring(i);
    }
    return s;
  };

  /**
   * Mapping transform to remove trailing whitespace.
   */
  public static final Function<String, String> TRIM_TRAILING_WHITESPACE = (s) -> {
    int i = s.length();
    for (; i > 0; i--) {
      if (!Character.isWhitespace(s.charAt(i - 1))) {
        break;
      }
    }
    if (i < s.length()) {
      return s.substring(0, i);
    }
    return s;
  };

  /**
   * Mapping transform to lowercase a string, allowing for case insensitive
   * compares.
   */
  public static final Function<String, String> LOWER_CASE = String::toLowerCase;

  /**
   * Mapping transform to trim leading and trailing whitespace.
   */
  public static final Function<String, String> TRIM_LEADING_AND_TRAILING_WHITESPACE = String::trim;

  /**
   * Default operation is to remove trailing whitespace before compare.
   */
  public static final Function<String, String> DEFAULT_OPERATION = TRIM_TRAILING_WHITESPACE;

  private StringsCompare() {
  }

  static class SkippingLineSource implements LineSource {
    private final Predicate<String> predicate;
    private final LineSource delegate;
    private String next = null;

    public SkippingLineSource(Predicate<String> predicate, LineSource delegate) throws IOException {
      this.predicate = predicate;
      this.delegate = delegate;
      while (delegate.hasLine()) {
        String p = delegate.nextLine();
        if (!predicate.test(p)) {
          next = p;
          break;
        }
      }
    }

    @Override
    public boolean hasLine() {
      return next != null;
    }

    @Override
    public String nextLine() throws IOException {
      if (hasLine()) {
        String ret = next;
        next = null;
        while (delegate.hasLine()) {
          String p = delegate.nextLine();
          if (!predicate.test(p)) {
            next = p;
            break;
          }
        }
        return ret;
      }
      throw new NoSuchElementException();
    }

  }

  /**
   * Compare two {@link LineSource} objects using the default operation.
   * @param left one side.
   * @param right the other.
   * @return true if they match
   * @throws IOException on exception
   */
  public static boolean stringsCompare(LineSource left, LineSource right) throws IOException {
    return stringsCompare(left, right, SKIP_NONE, DEFAULT_OPERATION);
  }

  /**
   * Compare with a specified skipping predicate and the default mapping.
   * @param left left source
   * @param right right source.
   * @param filter filter to use to skip lines
   * @return true if they match
   * @throws IOException on exception
   */
  public static boolean stringsCompare(LineSource left, LineSource right, Predicate<String> filter) throws IOException {
    return stringsCompare(left, right, filter, DEFAULT_OPERATION);
  }

  /**
   * Compare with no skipping filter but the specified mapper.
   * @param left left source
   * @param right right source
   * @param mapper mapper per line.
   * @return true if the sources match
   * @throws IOException on exception
   */
  public static boolean stringsCompare(LineSource left, LineSource right, Function<String, String> mapper) throws IOException {
    return stringsCompare(left, right, SKIP_NONE, mapper);
  }

  /**
   * Compare two {@link LineSource} objects using a specified operation. Note that any
   * {@link Function} operation can be changed to make compound operations.
   * @param left left source
   * @param right right source
   * @param filter predicate to designate lines to skip
   * @param mapper mapper per line.
   * @return true if they match
   * @throws IOException on exception
   */
  public static boolean stringsCompare(LineSource left,
                                       LineSource right,
                                       Predicate<String> filter,
                                       Function<String, String> mapper) throws IOException {
    try {
      left = new SkippingLineSource(filter, left);
      right = new SkippingLineSource(filter, right);
      while (left.hasLine() && right.hasLine()) {
        String p1 = left.nextLine();
        String p2 = right.nextLine();
        p1 = mapper.apply(p1);
        p2 = mapper.apply(p2);
        if (!p1.equals(p2)) {
          return false;
        }
      }
      return !left.hasLine() && !right.hasLine();
    } finally {
      left.close();
      right.close();
    }
  }

  /**
   * Source for a reader.
   * @param r reader to draw from
   * @return LineSource
   * @throws IOException on underlying exception
   */
  public static LineSource source(Reader r) throws IOException {
    BufferedReader br = new BufferedReader(r);
    String first = br.readLine();
    return new LineSource() {
      String next = first;

      @Override
      public boolean hasLine() {
        return next != null;
      }

      @Override
      public String nextLine() throws IOException {
        if (hasLine()) {
          String p = next;
          next = br.readLine();
          return p;
        }
        throw new NoSuchElementException();
      }

      @Override
      public void close() throws IOException {
        r.close();
      }
    };
  }

  /**
   * Source from a string.
   * @param s String to use
   * @return LineSource
   * @throws IOException on exception
   */
  public static LineSource source(String s) throws IOException {
    return source(new StringReader(s));
  }

  /**
   * Source from an input stream, using UTF-8 encoding.
   * @param is input stream
   * @return LineSource
   * @throws IOException on exception
   */
  public static LineSource source(InputStream is) throws IOException {
    return source(is, StandardCharsets.UTF_8);
  }

  /**
   * Source from an input stream using a specific char set.
   * @param is input stream
   * @param cset charset encoding
   * @return LineSource
   * @throws IOException on exception
   */
  public static LineSource source(InputStream is, Charset cset) throws IOException {
    InputStreamReader isr = new InputStreamReader(is, cset);
    return source(isr);
  }

  /**
   * Source from a resource stream, UTF-8 charset.
   * @param clz class to use for getResourceAsStream()
   * @param name name to open
   * @return LineSource
   * @throws IOException on exception
   */
  public static LineSource source(Class<?> clz, String name) throws IOException {
    return source(clz, name, StandardCharsets.UTF_8);
  }

  /**
   * Source from a resource stream, specified charset
   * @param clz class to use
   * @param name name to use for resource
   * @param cset charset encoding to use.
   * @return LineSource
   * @throws IOException on exception
   */
  public static LineSource source(Class<?> clz, String name, Charset cset) throws IOException {
    InputStream res = clz.getResourceAsStream(name);
    return source(res, cset);
  }

  /**
   * Source from an array of strings.
   * @param arr array of strings
   * @return LineSource
   */
  public static LineSource source(String[] arr) {
    return source(arr, 0, arr.length);
  }

  /**
   * Source from an array of strings.
   * @param arr array of strings
   * @param pos position of first entry to use
   * @param len number of entries to use
   * @return LineSource
   */
  public static LineSource source(String[] arr, int pos, int len) {
    return new LineSource() {
      int idx = pos;

      @Override
      public boolean hasLine() {
        return idx < pos + len;
      }

      @Override
      public String nextLine() {
        if (hasLine()) {
          return arr[idx++];
        }
        throw new NoSuchElementException();
      }
    };
  }

  /**
   * Source from an arbitrary iterator.
   * @param iter base iterator
   * @return LineSource
   */
  public static LineSource source(Iterator<String> iter) {
    return new LineSource() {

      @Override
      public boolean hasLine() {
        return iter.hasNext();
      }

      @Override
      public String nextLine() {
        if (hasLine()) {
          return iter.next();
        }
        throw new NoSuchElementException();
      }
    };
  }

  /**
   * Source from an iterable. What did you expect, exactly?
   * @param iter iterable
   * @return LineSource
   */
  public static LineSource source(Iterable<String> iter) {
    return source(iter.iterator());
  }

  /**
   * Source from a file using UTF8 charset encoding.
   * @param f file to open
   * @return LineSource
   * @throws IOException on exception
   */
  public static LineSource source(File f) throws IOException {
    return source(f, StandardCharsets.UTF_8);
  }

  /**
   * Source from a file using specified encoding.
   * @param f file
   * @param cSet charset
   * @return LineSource
   * @throws IOException on exception
   */
  public static LineSource source(File f, Charset cSet) throws IOException {
    FileInputStream fis = new FileInputStream(f);
    return source(fis, cSet);
  }

}

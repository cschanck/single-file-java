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
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This attempts to implement a very tiny RFC4180 compliant CSV parser.
 * It relies on {@link BufferedReader}'s end of line handling, so it will
 * swallow '\n', '\r' or '\r\n' line endings.
 * <p>It attempts to implement the subset of RFC4180 called out on wikipedia:
 * <ul>
 * <li>MS-DOS-style lines that end with (CR/LF) characters (optional for the last line).
 * <li>An optional header record (there is no sure way to detect whether it is present, so care
 * is required when importing).
 * <li>Each record should contain the same number of comma-separated fields.
 * <li>Any field may be quoted (with double quotes).
 * <li>Fields containing a line-break, double-quote or commas should be quoted. (If they
 * are not, the file will likely be impossible to process correctly.)
 * <li>If double-quotes are used to enclose fields, then a double-quote must be
 * represented by two double-quote characters.
 * </ul>
 * Additionally, it will ignore blank lines. The "header" line is not actually handled
 * at all, you must handle it explicitly. It should handle quoted multi-line fields
 * properly. You can, in the constructor, specify to parse a single header,
 * which is then available via the getHeader() method.
 *
 * <p>
 *   Mainly, I implemented this simple subset because it is ... useful. If you
 *   need more than this, use a real library, like the excellent Apache one or
 *   OpenCSV, or, or, or.
 * @author cschanck
 */
public class RFC4180CSVParser implements Iterable<String[]>, Closeable {

  private final BufferedReader bReader;
  private int index = 0;
  private String line;
  private final char sep;
  private String[] header = null;

  /**
   * Constructor, no header, comma seperator
   * @param reader reader to read.
   * @throws IOException on IO exception
   */
  public RFC4180CSVParser(Reader reader) throws IOException {
    this(false, reader);
  }

  /**
   * Constructor, specifying if there is a header to read.
   * @param hasHeader true if there is a header to read
   * @param reader reader
   * @throws IOException on IO exception
   */
  public RFC4180CSVParser(boolean hasHeader, Reader reader) throws IOException {
    this(',', hasHeader, reader);
  }

  /**
   * Constructor, specifying field separator, and if there is a header to read.
   * @param sep character separator
   * @param hasHeader true if there is a header to read
   * @param reader reader
   * @throws IOException on IO exception
   */
  public RFC4180CSVParser(char sep, boolean hasHeader, Reader reader) throws IOException {
    this.sep = sep;
    this.bReader = new BufferedReader(reader);
    // preload
    line = bReader.readLine();
    if (hasHeader) {
      header = record();
    }
  }

  /**
   * If reading a header was specified, then it can be retrieved via this
   * method. If not specified, this will return null.
   * @return header or null
   */
  public String[] getHeader() {
    return header;
  }

  /**
   * Close this, close the reader. Idempotent, swallows exceptions.
   */
  @Override
  public void close() {
    try {
      // aggressively close it, ignore failures
      bReader.close();
    } catch (Exception e) {
    }
  }

  @Override
  public Iterator<String[]> iterator() {
    return new Iterator<String[]>() {
      private String[] next;

      {
        try {
          next = record();
        } catch (IOException e) {
          next = null;
          throw new IllegalStateException(e);
        }
      }

      @Override
      public boolean hasNext() {
        return next != null;
      }

      @Override
      public String[] next() {
        if (hasNext()) {
          try {
            String[] ret = next;
            next = record();
            if (next == null) {
              close();
            }
            return ret;
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }
        throw new NoSuchElementException();
      }
    };
  }

  private int next() throws IOException {
    if (line == null) {
      close();
      return -1;
    }
    if (index == line.length()) {
      do {
        // eat blank lines. might as well do it here.
        line = bReader.readLine();
        index = 0;
      } while (line != null && line.length() == 0);
      // return line end char.
      return '\n';
    }
    return line.charAt(index++);
  }

  /**
   * Retrieve the next record in the CSV file. Used as basis for
   * iteration on this class, can be used directly if that is preferred.
   *
   * @return next record or null if at end of file.
   * @throws IOException on exception
   */
  public String[] record() throws IOException {
    // read one record...

    ArrayList<String> list = new ArrayList<>();
    boolean[] eol = new boolean[] { false };

    StringBuilder whiteSpace = new StringBuilder();
    // consume initial whitespace
    int p;
    for (p = next(); p >= 0; p = next()) {
      char ch = (char) p;
      if (ch == '\n') {
        // restart, skip line.
        whiteSpace.setLength(0);
      } else if (ch == sep) {
        break;
      } else if (Character.isWhitespace(ch)) {
        whiteSpace.append(ch);
      } else {
        break;
      }
    }

    // eof give up and go home
    if (p < 0) {
      return null;
    }

    // process chars
    for (; p >= 0; p = next()) {
      char ch = (char) p;
      if (ch == '"') {
        // start of double quote field
        eol[0] = false;
        list.add(doubleQuote(eol));
        if (eol[0]) {
          // handle if field was at end of line
          break;
        }
      } else if (ch == sep) {
        // empty field
        list.add("");
      } else if (ch == '\n') {
        // eol, we done with this record
        break;
      } else {
        // unquoted field, throw the whitespace in, it counts.
        whiteSpace.append(ch);
        eol[0] = false;
        list.add(noQuote(whiteSpace, eol));
        if (eol[0]) {
          // handle if field was at end of line
          break;
        }
        whiteSpace.setLength(0);
      }
    }
    return list.toArray(new String[0]);
  }

  private String noQuote(StringBuilder sb, boolean[] eol) throws IOException {
    // grab a single unquoted value.
    for (int p = next(); p >= 0; p = next()) {
      char ch = (char) p;
      if (ch == sep) {
        // sep we done
        break;
      } else if (ch == '\n') {
        // eol we done
        eol[0] = true;
        break;
      } else if (ch == '"') {
        // gulp
        throw new IllegalStateException("Double quote char detected in unquoted field");
      }
      sb.append(ch);
    }
    return sb.toString();
  }

  private String doubleQuote(boolean[] eol) throws IOException {
    // process double quoted field
    StringBuilder sb = new StringBuilder();
    for (int p = next(); p >= 0; p = next()) {
      char ch = (char) p;
      if (ch != '"') {
        // easy case
        sb.append(ch);
      } else {
        // check next char for eof
        p = next();
        if (p < 0) {
          throw new IllegalStateException("EOL in double quoted field encountered.");
        }
        // check for second consecutive double quote
        ch = (char) p;
        if (ch == '"') {
          sb.append('"');
        } else {
          // eat whitespace and next comma, or eol
          for (; p >= 0; p = next()) {
            ch = (char) p;
            if (ch == sep) {
              break;
            } else if (ch == '\n') {
              eol[0] = true;
              break;
            } else if (!Character.isWhitespace(ch)) {
              throw new IllegalStateException("Non-white space character detected after double quote!");
            }
          }
          return sb.toString();
        }
      }
    }
    return sb.toString();
  }
}

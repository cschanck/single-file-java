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

import junit.framework.TestCase;
import org.hamcrest.Matchers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RFC4180CSVParserTest extends TestCase {

  public void testSimplest() throws IOException {
    Reader br = new InputStreamReader(this.getClass().getResourceAsStream("simplest.csv"));
    RFC4180CSVParser parser = new RFC4180CSVParser(br);

    String[] one = parser.record();
    assertThat(one.length, is(6));
    assertThat(one[0], is("foo"));
    assertThat(one[1], is("bar"));
    assertThat(one[2], is("baz"));
    assertThat(one[3], is("1"));
    assertThat(one[4], is("2"));
    assertThat(one[5], is("3"));

    String[] two = parser.record();
    assertThat(two.length, is(6));
    assertThat(two[0], is("fooo"));
    assertThat(two[1], is("barr"));
    assertThat(two[2], is("bazz"));
    assertThat(two[3], is("11"));
    assertThat(two[4], is("22"));
    assertThat(two[5], is("33"));

    String[] three = parser.record();
    assertThat(three, Matchers.nullValue());
  }

  public void testCommaInQuotes() throws IOException {
    Reader br = new InputStreamReader(this.getClass().getResourceAsStream("comma_in_quotes.csv"));
    RFC4180CSVParser parser = new RFC4180CSVParser(br);

    String[] one = parser.record();
    assertThat(one.length, is(5));
    assertThat(one[0], is("first"));
    assertThat(one[1], is("last"));
    assertThat(one[2], is("address"));
    assertThat(one[3], is("city"));
    assertThat(one[4], is("zip"));

    String[] two = parser.record();
    assertThat(two.length, is(5));
    assertThat(two[0], is("John"));
    assertThat(two[1], is("Doe"));
    assertThat(two[2], is("120 any st."));
    assertThat(two[3], is("Anytown, WW"));
    assertThat(two[4], is("08123"));

    String[] three = parser.record();
    assertThat(three, Matchers.nullValue());

  }

  public void testEmpty() throws IOException {
    Reader br = new InputStreamReader(this.getClass().getResourceAsStream("empty.csv"));
    RFC4180CSVParser parser = new RFC4180CSVParser(br);

    String[] one = parser.record();
    assertThat(one.length, is(3));
    assertThat(one[0], is("a"));
    assertThat(one[1], is("b"));
    assertThat(one[2], is("c"));

    String[] two = parser.record();
    assertThat(two.length, is(3));
    assertThat(two[0], is("1"));
    assertThat(two[1], is(""));
    assertThat(two[2], is(""));

    String[] three = parser.record();
    assertThat(three.length, is(3));
    assertThat(three[0], is("2"));
    assertThat(three[1], is("3"));
    assertThat(three[2], is("4"));

    String[] four = parser.record();
    assertThat(four, Matchers.nullValue());

  }

  public void testEscapedQuotes() throws IOException {
    Reader br = new InputStreamReader(this.getClass().getResourceAsStream("escaped_quotes.csv"));
    RFC4180CSVParser parser = new RFC4180CSVParser(br);

    String[] one = parser.record();
    assertThat(one.length, is(2));
    assertThat(one[0], is("a"));
    assertThat(one[1], is("b"));

    String[] two = parser.record();
    assertThat(two.length, is(2));
    assertThat(two[0], is("1"));
    assertThat(two[1], is("ha \"ha\" ha"));

    String[] three = parser.record();
    assertThat(three.length, is(2));
    assertThat(three[0], is("3"));
    assertThat(three[1], is("4"));

    String[] four = parser.record();
    assertThat(four, Matchers.nullValue());

  }

  public void testJson() throws IOException {
    Reader br = new InputStreamReader(this.getClass().getResourceAsStream("json.csv"));
    RFC4180CSVParser parser = new RFC4180CSVParser(br);

    String[] one = parser.record();
    assertThat(one.length, is(2));
    assertThat(one[0], is("key"));
    assertThat(one[1], is("val"));

    String[] two = parser.record();
    assertThat(two.length, is(2));
    assertThat(two[0], is("1"));
    assertThat(two[1], is("{\"type\": \"Point\", \"coordinates\": [102.0, 0.5]}"));

    String[] three = parser.record();
    assertThat(three, Matchers.nullValue());

  }

  public void testNewlines() throws IOException {
    Reader br = new InputStreamReader(this.getClass().getResourceAsStream("newlines.csv"));
    RFC4180CSVParser parser = new RFC4180CSVParser(br);

    String[] one = parser.record();
    assertThat(one.length, is(3));
    assertThat(one[0], is("a"));
    assertThat(one[1], is("b"));
    assertThat(one[2], is("c"));

    String[] two = parser.record();
    assertThat(two.length, is(3));
    assertThat(two[0], is("1"));
    assertThat(two[1], is("2"));
    assertThat(two[2], is("3"));

    String[] three = parser.record();
    assertThat(three.length, is(3));
    assertThat(three[0], is("Once upon \na time"));
    assertThat(three[1], is("5"));
    assertThat(three[2], is("6"));

    String[] four = parser.record();
    assertThat(four.length, is(3));
    assertThat(four[0], is("7"));
    assertThat(four[1], is("8"));
    assertThat(four[2], is("9"));

    String[] five = parser.record();
    assertThat(five, Matchers.nullValue());

  }

  public void testQuotesAndNewlines() throws IOException {
    Reader br = new InputStreamReader(this.getClass().getResourceAsStream("quotes_and_newlines.csv"));
    RFC4180CSVParser parser = new RFC4180CSVParser(br);

    String[] one = parser.record();
    assertThat(one.length, is(2));
    assertThat(one[0], is("a"));
    assertThat(one[1], is("b"));

    String[] two = parser.record();
    assertThat(two.length, is(2));
    assertThat(two[0], is("1"));
    assertThat(two[1], is("ha \n" + "\"ha\" \n" + "ha"));

    String[] three = parser.record();
    assertThat(three.length, is(2));
    assertThat(three[0], is("3"));
    assertThat(three[1], is("4"));

    String[] five = parser.record();
    assertThat(five, Matchers.nullValue());

  }

  public void testBlankLines() throws IOException {
    Reader br = new InputStreamReader(this.getClass().getResourceAsStream("blanklines.csv"));
    RFC4180CSVParser parser = new RFC4180CSVParser(br);

    String[] one = parser.record();
    assertThat(one.length, is(2));
    assertThat(one[0], is("1"));
    assertThat(one[1], is("2"));

    String[] two = parser.record();
    assertThat(two.length, is(2));
    assertThat(two[0], is("3"));
    assertThat(two[1], is("4"));

    String[] five = parser.record();
    assertThat(five, Matchers.nullValue());

  }

  public void testYelp() throws IOException {
    InputStream resource = this.getClass().getResourceAsStream("yelp.csv");
    Reader br = new InputStreamReader(resource);
    RFC4180CSVParser parser = new RFC4180CSVParser(br);

    int cnt = 1;
    String[] hdr = parser.record();
    assertThat(hdr.length, is(9));
    for (String[] rec = parser.record(); rec != null; rec = parser.record()) {
      assertThat(rec.length, is(hdr.length));
      cnt++;
    }
    assertThat(cnt, is(355));
  }

  public void testDataIsBeautiful() throws IOException {
    InputStream resource = this.getClass().getResourceAsStream("dataisbeautifulposts.csv");
    Reader br = new InputStreamReader(resource);
    RFC4180CSVParser parser = new RFC4180CSVParser(true, br);
    String[] hdr = parser.getHeader();
    assertThat(hdr.length, is(12));
    int cnt = 0;
    for (String[] rec : parser) {
      assertThat(rec.length, is(hdr.length));
      cnt++;
    }
    assertThat(cnt, is(173611));
  }
}
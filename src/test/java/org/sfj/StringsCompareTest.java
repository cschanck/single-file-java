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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.sfj.StringsCompare.IDENT;
import static org.sfj.StringsCompare.LOWER_CASE;
import static org.sfj.StringsCompare.TRIM_LEADING_AND_TRAILING_WHITESPACE;
import static org.sfj.StringsCompare.stringsCompare;
import static org.sfj.StringsCompare.source;

public class StringsCompareTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void testSimpleCompares() throws IOException {
    String[] arr1 = new String[] { "foo", "fie", "flow" };
    String[] arr2 = new String[] { "foo", "fie", "flow" };
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr2)), is(true));
    String[] arr3 = new String[] { "f oo", "fie", "flow" };
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr3)), is(false));
    String[] arr4 = new String[] { "Foo", "fie", "flow" };
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr4)), is(false));
    assertThat(stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr4), LOWER_CASE), is(true));
    String[] arr5 = new String[] { " foo", "fie", " flow" };
    assertThat(stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr5), TRIM_LEADING_AND_TRAILING_WHITESPACE), is(true));
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr5)), is(false));
    String[] arr6 = new String[] { "foo ", "fie", "flow " };
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr6)), is(true));
    assertThat(stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr6), IDENT), is(false));
    String[] arr7 = new String[] { " foo ", "fie", "flow" };
    assertThat(stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr7), TRIM_LEADING_AND_TRAILING_WHITESPACE), is(true));
    assertThat(stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr7), IDENT), is(false));
    String[] arr8 = new String[] { "foo", "fie", " flow", "blo" };
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr8)), is(false));
    String[] arr9 = new String[] { "foo", "flie", " flow" };
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr9)), is(false));
    String[] arr10 = new String[] { "foo", "fie", "    ", "flow" };
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr10), StringsCompare.SKIP_BLANK), is(true));
    String[] arr11 = new String[] { "foo", "fie", "", "flow" };
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr11), StringsCompare.SKIP_BLANK), is(true));
    String[] arr12 = new String[] { "foo", "fie", "    ", "flow" };
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr12), StringsCompare.SKIP_EMPTY), is(false));
    String[] arr13 = new String[] { "foo", "fie", "", "flow" };
    assertThat(StringsCompare.stringsCompare(StringsCompare.source(arr1), StringsCompare.source(arr13), StringsCompare.SKIP_EMPTY), is(true));
  }

  @Test
  public void testArraySources() throws IOException {
    // array
    String[] arr = new String[] { "foo", "fie", "faz" };
    StringsCompare.LineSource src = StringsCompare.source(arr);
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("foo"));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("fie"));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("faz"));
    assertThat(src.hasLine(), is(false));
    src = source(arr, 1, 1);
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("fie"));
    assertThat(src.hasLine(), is(false));
  }

  // iterable
  public void testIterable() throws IOException {
    ArrayList<String> dut = new ArrayList<>();
    dut.add("foo");
    dut.add("fie");
    dut.add("faz");

    StringsCompare.LineSource src = StringsCompare.source(dut);
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("foo"));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("fie"));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("faz"));
    assertThat(src.hasLine(), is(false));
  }

  // iterator
  @Test
  public void testIterator() throws IOException {
    ArrayList<String> dut = new ArrayList<>();
    dut.add("foo");
    dut.add("fie");
    dut.add("faz");

    StringsCompare.LineSource src = StringsCompare.source(dut.iterator());
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("foo"));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("fie"));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("faz"));
    assertThat(src.hasLine(), is(false));
  }

  // resource
  @Test
  public void testResource() throws IOException {
    StringsCompare.LineSource src = StringsCompare.source(this.getClass(), "simplest.csv");
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("foo,bar,baz,1,2,3"));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("fooo,barr,bazz,11,22,33"));
    assertThat(src.hasLine(), is(false));
  }

  // reader
  @Test
  public void testReader() throws IOException {
    File f = makeTmp();
    StringsCompare.LineSource src = StringsCompare.source(new FileReader(f));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("hello"));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("goodbye"));
    assertThat(src.hasLine(), is(false));
  }

  // inputstream
  @Test
  public void testInputStream() throws IOException {
    File f = makeTmp();
    StringsCompare.LineSource src = StringsCompare.source(new FileInputStream(f));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("hello"));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("goodbye"));
    assertThat(src.hasLine(), is(false));
  }

  // file file
  @Test
  public void testFile() throws IOException {
    File f = makeTmp();
    StringsCompare.LineSource src = StringsCompare.source(f);
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("hello"));
    assertThat(src.hasLine(), is(true));
    assertThat(src.nextLine(), is("goodbye"));
    assertThat(src.hasLine(), is(false));
  }

  private File makeTmp() throws IOException {
    File f = tmp.newFile();
    FileWriter fw = new FileWriter(f);
    fw.append("hello");
    fw.append(System.lineSeparator());
    fw.append("goodbye");
    fw.close();
    return f;
  }
}

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

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JSONOneTest {

  @Test
  public void testParseBoolean() throws ParseException {
    JSONOne.Parser p = new JSONOne.Parser("true");
    JSONOne.JObject obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.BOOLEAN));
    assertThat(obj.boolValue(), is(true));

    p = new JSONOne.Parser("tRUe");
    obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.BOOLEAN));
    assertThat(obj.boolValue(), is(true));

    p = new JSONOne.Parser("false");
    obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.BOOLEAN));
    assertThat(obj.boolValue(), is(false));
  }

  @Test
  public void testParseNumber() throws ParseException {
    JSONOne.Parser p = new JSONOne.Parser("5");
    JSONOne.JObject obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.NUMBER));
    assertThat(obj.numberValue().longValue(), is(5L));

    p = new JSONOne.Parser("5.3");
    obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.NUMBER));
    assertThat(obj.numberValue().doubleValue(), is(5.3d));

    p = new JSONOne.Parser("-5.3");
    obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.NUMBER));
    assertThat(obj.numberValue().doubleValue(), is(-5.3d));

    p = new JSONOne.Parser("5.3E4");
    obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.NUMBER));
    assertThat(obj.numberValue().doubleValue(), is(5.3e4d));

    p = new JSONOne.Parser("5.3E-4");
    obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.NUMBER));
    assertThat(obj.numberValue().doubleValue(), is(5.3e-4d));
  }

  @Test
  public void testParseString() throws ParseException {
    JSONOne.Parser p = new JSONOne.Parser("\"foo\"");
    JSONOne.JObject obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.STRING));
    assertThat(obj.stringValue(), is("foo"));

    p = new JSONOne.Parser("\"foo is a bar\"");
    obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.STRING));
    assertThat(obj.stringValue(), is("foo is a bar"));

    p = new JSONOne.Parser("\"fo\\'o \\ni\\\"s a bar\"");
    obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.STRING));
    assertThat(obj.stringValue(), is("fo'o \ni\"s a bar"));
  }

  @Test
  public void testParseNull() throws ParseException {
    JSONOne.Parser p = new JSONOne.Parser("null");
    JSONOne.JObject obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.NULL));
    assertThat(obj.nullValue(), is(true));

    p = new JSONOne.Parser("nUll");
    obj = p.singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.NULL));
    assertThat(obj.nullValue(), is(true));
  }

  @Test
  public void testSimpleArrays() throws ParseException, IOException {
    JSONOne.JArray arr = new JSONOne.JArray();
    arr.addBoolean(true);
    arr.addNumber(1003);
    arr.addString("foobar");
    assertThat(arr.size(), is(3));
    assertThat(arr.get(0).getType(), is(JSONOne.Type.BOOLEAN));
    assertThat(arr.get(1).getType(), is(JSONOne.Type.NUMBER));
    assertThat(arr.get(2).getType(), is(JSONOne.Type.STRING));

    JSONOne.Parser p = new JSONOne.Parser("[ true, 1003, \"foobar\"]");
    JSONOne.JArray arr2 = (JSONOne.JArray) p.singleObject();
    assertThat(arr, is(arr2));
  }

  @Test
  public void testSimpleMaps() throws ParseException, IOException {
    JSONOne.JMap map = new JSONOne.JMap();
    map.putBoolean("one", false);
    map.putNull("two");
    map.putNumber("three", 102.4d);
    map.putString("four", "taffeta");

    assertThat(map.size(), is(4));
    assertThat(map.getBoolean("one", null), is(false));
    assertThat(map.get("two").nullValue(), is(true));
    assertThat(map.getNumber("three", null), is(102.4d));
    assertThat(map.getString("four", null), is("taffeta"));

    JSONOne.Parser
      p =
      new JSONOne.Parser("{ \"one\" : false, \"two\": null, \"three\":102.4d, \"four\": \"taffeta\" }");
    JSONOne.JMap map2 = (JSONOne.JMap) p.singleObject();
    assertThat(map, is(map2));
  }

  @Test
  public void testParseColors() throws ParseException, IOException {
    InputStream stream = JSONOneTest.class.getResourceAsStream("colors.json");
    StringBuilder sb = new StringBuilder();
    new BufferedReader(new InputStreamReader(stream)).lines().forEach(l -> {
      sb.append(l);
      sb.append('\n');
    });
    JSONOne.JObject obj = new JSONOne.Parser(sb.toString()).singleObject();
    assertThat(obj.getType(), is(JSONOne.Type.MAP));
    JSONOne.JMap map = obj.mapValue();
    assertThat(map.size(), is(1));
    JSONOne.JArray arr = map.getArray("colors", null);
    assertThat(arr.size(), is(6));

    // check something random
    assertThat(arr.get(4).mapValue().getString("type", null), is("primary"));

  }

  @Test
  public void testRoundTrip() throws ParseException, IOException {
    roundTrip("colors.json", "mockCrud.json", "twitter.json");
  }

  private void roundTrip(String... resources) throws ParseException, IOException {
    for (String res : resources) {
      InputStream stream = JSONOneTest.class.getResourceAsStream(res);
      StringBuilder sb = new StringBuilder();
      new BufferedReader(new InputStreamReader(stream)).lines().forEach(l -> {
        sb.append(l);
        sb.append(System.lineSeparator());
      });
      JSONOne.JObject obj1 = new JSONOne.Parser(sb.toString()).singleObject();
      if (obj1.getType().equals(JSONOne.Type.MAP) || obj1.getType().equals(JSONOne.Type.ARRAY)) {
        String cString = obj1.print(0, true);
        String nocString = obj1.print(0, false);
        JSONOne.JObject obj3 = new JSONOne.Parser(nocString).singleObject();
        JSONOne.JObject obj2 = new JSONOne.Parser(cString).singleObject();
        assertThat(obj1, is(obj2));
        assertThat(obj1, is(obj3));
        assertThat(obj2, is(obj3));
      }
    }
  }
}

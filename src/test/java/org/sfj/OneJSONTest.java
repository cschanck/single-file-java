package org.sfj;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class OneJSONTest {

  @Test
  public void testScanner() throws ParseException {
    OneJSON.Scanner s = new OneJSON.Scanner("{ \"foo\" : 10, [ \"one\", 2, 3.2E10, true, null ] }");
    for (OneJSON.Token t = s.nextToken(); !(t.type == OneJSON.TokenType.EOF); t = s.nextToken()) {
      System.out.println(t);
    }
  }

  @Test
  public void testParseBoolean() throws ParseException {
    OneJSON.Parser p = new OneJSON.Parser("true");
    OneJSON.JSONObject obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.BOOLEAN));
    assertThat(obj.boolValue(), is(true));

    p = new OneJSON.Parser("tRUe");
    obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.BOOLEAN));
    assertThat(obj.boolValue(), is(true));

    p = new OneJSON.Parser("false");
    obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.BOOLEAN));
    assertThat(obj.boolValue(), is(false));
  }

  @Test
  public void testParseNumber() throws ParseException {
    OneJSON.Parser p = new OneJSON.Parser("5");
    OneJSON.JSONObject obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.NUMBER));
    assertThat(obj.numberValue().longValue(), is(5L));

    p = new OneJSON.Parser("5.3");
    obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.NUMBER));
    assertThat(obj.numberValue().doubleValue(), is(5.3d));

    p = new OneJSON.Parser("-5.3");
    obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.NUMBER));
    assertThat(obj.numberValue().doubleValue(), is(-5.3d));

    p = new OneJSON.Parser("5.3E4");
    obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.NUMBER));
    assertThat(obj.numberValue().doubleValue(), is(5.3e4d));

    p = new OneJSON.Parser("5.3E-4");
    obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.NUMBER));
    assertThat(obj.numberValue().doubleValue(), is(5.3e-4d));
  }

  @Test
  public void testParseString() throws ParseException {
    OneJSON.Parser p = new OneJSON.Parser("\"foo\"");
    OneJSON.JSONObject obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.STRING));
    assertThat(obj.stringValue(), is("foo"));

    p = new OneJSON.Parser("\"foo is a bar\"");
    obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.STRING));
    assertThat(obj.stringValue(), is("foo is a bar"));
  }

  @Test
  public void testParseNull() throws ParseException {
    OneJSON.Parser p = new OneJSON.Parser("null");
    OneJSON.JSONObject obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.NULL));
    assertThat(obj.nullValue(), is(true));

    p = new OneJSON.Parser("nUll");
    obj = p.singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.NULL));
    assertThat(obj.nullValue(), is(true));
  }

  @Test
  public void testSimpleArrays() throws ParseException {
    OneJSON.JSONArray arr = new OneJSON.JSONArray();
    arr.addBoolean(true);
    arr.addNumber(1003);
    arr.addString("foobar");
    assertThat(arr.size(), is(3));
    assertThat(arr.get(0).getType(), is(OneJSON.Type.BOOLEAN));
    assertThat(arr.get(1).getType(), is(OneJSON.Type.NUMBER));
    assertThat(arr.get(2).getType(), is(OneJSON.Type.STRING));

    OneJSON.Parser p = new OneJSON.Parser("[ true, 1003, \"foobar\"]");
    OneJSON.JSONArray arr2 = (OneJSON.JSONArray) p.singleObject();
    assertThat(arr, is(arr2));
  }

  @Test
  public void testSimpleMaps() throws ParseException {
    OneJSON.JSONMap map = new OneJSON.JSONMap();
    map.putBoolean("one", false);
    map.putNull("two");
    map.putNumber("three", 102.4d);
    map.putString("four", "taffeta");

    assertThat(map.size(), is(4));
    assertThat(map.getBoolean("one", null), is(false));
    assertThat(map.get("two").nullValue(), is(true));
    assertThat(map.getNumber("three", null), is(102.4d));
    assertThat(map.getString("four", null), is("taffeta"));

    OneJSON.Parser
      p =
      new OneJSON.Parser("{ \"one\" : false, \"two\": null, \"three\":102.4d, \"four\": \"taffeta\" }");
    OneJSON.JSONMap map2 = (OneJSON.JSONMap) p.singleObject();
    assertThat(map, is(map2));
  }

  @Test
  public void testParseColors() throws ParseException {
    InputStream stream = OneJSONTest.class.getResourceAsStream("colors.json");
    StringBuilder sb = new StringBuilder();
    new BufferedReader(new InputStreamReader(stream)).lines().forEach(l -> {
      sb.append(l);
      sb.append('\n');
    });
    OneJSON.JSONObject obj = new OneJSON.Parser(sb.toString()).singleObject();
    assertThat(obj.getType(), is(OneJSON.Type.MAP));
    OneJSON.JSONMap map = obj.mapValue();
    assertThat(map.size(), is(1));
    OneJSON.JSONArray arr = map.getArray("colors", null);
    assertThat(arr.size(), is(6));

    // check something random
    assertThat(arr.get(4).mapValue().getString("type", null), is("primary"));

  }
}

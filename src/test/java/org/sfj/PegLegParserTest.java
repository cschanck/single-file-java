package org.sfj;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;

import static java.lang.Long.parseLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class PegLegParserTest {

  /**
   * Dumb test grammar to see what worked.
   */
  static class Test1 extends PegLegParser<Object> {

    PegLegRule<Object> bc() {
      return () -> str("bc").rule();
    }

    PegLegRule<Object> abcd() {
      return () -> seqOf('a', 'b', 'c', 'd', eof()).rule();
    }

    PegLegRule<Object> abcd2() {
      return () -> seqOf('a', bc(), 'd', eof()).rule();
    }

    PegLegRule<Object> orabcd() {
      return () -> firstOf('a', 'b', 'c', 'd').rule();
    }

    PegLegRule<Object> fourChars() {
      return () -> seqOf(orabcd(), orabcd(), orabcd(), orabcd(), eof()).rule();
    }
  }

  @Test
  public void testNothing() {
    Test1 parser = new Test1();
    parser.using("abcd");
    PegLegParser.RuleReturn<Object> ret = parser.abcd().rule();
    assertThat(ret.matched(), is(true));

    parser.using("dabcd");
    ret = parser.orabcd().rule();
    assertThat(ret.matched(), is(true));

    parser.using("dabc");
    ret = parser.fourChars().rule();
    assertThat(ret.matched(), is(true));

    parser.using("dabb");
    ret = parser.fourChars().rule();
    assertThat(ret.matched(), is(true));

    parser.using("abcd");
    ret = parser.abcd2().rule();
    assertThat(ret.matched(), is(true));

  }

  /**
   * Calculator parser, which does the match, supports parenthesis.
   * <p>Two things of interest. Note the use of the lambda form in methods like
   * {@link #expression()} because there is a path that is recursive. While other
   * methods like {@link #number()} } are terminal, and therefore do not
   * need to be a lambda.</p>
   * <p>Also, you see the use of Ref variables and exec() calls to actually
   * calculate the values.</p>
   */
  static class CalcParser extends PegLegParser<Long> {

    PegLegRule<Long> one() {
      return seqOf(expression(), eof());
    }

    PegLegRule<Long> expression() {
      Ref<String> op = new Ref<>("");
      return seqOf(term(), zeroPlusOf(seqOf(anyOf("+-"), exec(() -> op.set(match().get())), term(), exec(() -> doMath(op)))))
               .refs(op);
    }

    private void doMath(Ref<String> op) {
      switch (op.get().charAt(0)) {
        case '+':
          values().push(values().pop() + values().pop());
          break;
        case '-':
          values().push(values().pop(1) - values().pop());
          break;
        case '*':
          values().push(values().pop() * values().pop());
          break;
        case '/':
          values().push(values().pop(1) / values().pop());
          break;
      }
    }

    PegLegRule<Long> term() {
      return () -> {
        Ref<String> op = new Ref<>("");
        return seqOf(factor(), zeroPlusOf(seqOf(anyOf("*/"), exec(() -> op.set(match().get())), factor(), exec(() -> doMath(op)))))
                 .refs(op)
                 .rule();
      };
    }

    PegLegRule<Long> factor() {
      return firstOf(number(), seqOf(ws('('), expression(), ws(')')));
    }

    PegLegRule<Long> number() {
      return seqOf(ws(), onePlusOf(charRange('0', '9')), exec(() -> values().push(parseLong(match().orElse("0")))), ws());
    }
  }

  @Test
  public void testCalc() {
    CalcParser parser = new CalcParser();
    parser.using("1+2 *(120- 100)/ 20 ");
    PegLegParser.RuleReturn<Long> ret = parser.one().rule();
    assertThat(ret.matched(), is(true));
    assertThat(parser.values().peek().get(), is(3L));

    parser = new CalcParser();
    parser.using("1-+2 *(120- 100)/ 20 ");
    ret = parser.one().rule();
    assertThat(ret.matched(), is(false));

    parser = new CalcParser();
    parser.using("3*1+2 *(120- 100)/ 20 ");
    ret = parser.one().rule();
    assertThat(ret.matched(), is(true));
    assertThat(parser.values().peek().get(), is(5L));

    parser = new CalcParser();
    parser.using("3*(1+2 *(120- 100)/ 20 )/1000");
    ret = parser.one().rule();
    assertThat(ret.matched(), is(true));
    assertThat(parser.values().peek().get(), is(0L));
  }

  /**
   * The following parsing expression grammar describes the classic non-context-free
   * language a^Nb^Nc^N (courtesy wikipedia)
   * <p>
   * S ← &(A 'c') 'a'+ B !.
   * A ← 'a' A? 'b'
   * B ← 'b' B? 'c'
   * <p>
   * This is just a matching parser, generates nothing. Just matches or not.
   */
  static class ABCGrammer extends PegLegParser<Object> {

    public PegLegRule<Object> S() {
      return seqOf(testOf(seqOf(A(), 'c')), onePlusOf('a'), B(), testNotOf(anyOf("abc")), eof());
    }

    public PegLegRule<Object> A() {
      return () -> seqOf('a', optOf(A()), 'b').rule();
    }

    public PegLegRule<Object> B() {
      return () -> seqOf('b', optOf(B()), 'c').rule();
    }
  }

  @Test
  public void testABC() {
    ABCGrammer parser = new ABCGrammer();
    parser.using("aaabbbccc");
    PegLegParser.RuleReturn<Object> ret = parser.parse(parser.S());
    assertThat(ret.matched(), is(true));

    parser.using("aaabbbbccc");
    ret = parser.parse(parser.S());
    assertThat(ret.matched(), is(false));

    parser.using("aaabbbcccd");
    ret = parser.parse(parser.S());
    assertThat(ret.matched(), is(false));

    parser.using("abc");
    ret = parser.parse(parser.S());
    assertThat(ret.matched(), is(true));
  }

  /**
   * JSON parser, leaves a single object on top of the stack, using
   * the JSONOne package.
   */
  static class Json extends PegLegParser<JSONOne.JObject> {
    PegLegRule<JSONOne.JObject> json() {
      return firstOf(jsonObject(), jsonArray());
    }

    PegLegRule<JSONOne.JObject> jsonObject() {
      Ref<JSONOne.JMap> map = new Ref<>(JSONOne.JMap::new);
      return () -> seqOf(ws('{'), optOf(seqOf(pair(), exec(addPair(map)), zeroPlusOf(',', pair(), exec(addPair(map))), optOf(ws(',')))), ws('}'), e(() -> values()
                                                                                                                                                            .push(map
                                                                                                                                                                    .get())))
                     .refs(map)
                     .rule();
    }

    private Runnable addPair(Ref<JSONOne.JMap> map) {
      return () -> {
        JSONOne.JObject val = values().pop();
        JSONOne.JString key = (JSONOne.JString) values().pop();
        map.get().put(key.stringValue(), val);
      };
    }

    PegLegRule<JSONOne.JObject> pair() {
      return seqOf(jsonString(), ws(':'), value());
    }

    PegLegRule<JSONOne.JObject> value() {
      return firstOf(jsonString(), // pushed below
                     seqOf(jsonNumber(), e(this::matchAsNumber)), jsonObject(), // pushed below
                     jsonArray(), // pushed below
                     seqOf(ws("true"), e(() -> values().push(new JSONOne.JBoolean(true)))), seqOf(ws("false"), e(() -> values()
                                                                                                                         .push(new JSONOne.JBoolean(false)))), seqOf(ws("null"), e(() -> values()
                                                                                                                                                                                           .push(new JSONOne.JNull()))));
    }

    private void matchAsNumber() {
      try {
        Number num = NumberFormat.getInstance().parse(get().match().get());
        values().push(new JSONOne.JNumber(num));
      } catch (ParseException e) {
        error(e.getMessage());
      }
    }

    PegLegRule<JSONOne.JObject> jsonString() {
      return seqOf(ws(), "\"", zeroPlusOf(chars()), e(() -> {
        values().push(new JSONOne.JString(JSONOne.unescapeString(get().match().orElse(""))));
      }), "\"", ws());
    }

    PegLegRule<JSONOne.JObject> jsonNumber() {
      return seqOf(integer(), optOf(frac(), optOf(exp())), ws());
    }

    PegLegRule<JSONOne.JObject> jsonArray() {
      Ref<JSONOne.JArray> arr = new Ref<>(JSONOne.JArray::new);
      return () -> seqOf(ws('['), optOf(value(), exec(() -> arr.get()
                                                              .add(values().pop())), zeroPlusOf(ws(','), value(), e(() -> arr
                                                                                                                            .get()
                                                                                                                            .add(values()
                                                                                                                                   .pop()))), optOf(ws(','))), ws(']'), e(() -> values()
                                                                                                                                                                                  .push(arr
                                                                                                                                                                                          .get())))
                     .refs(arr)
                     .rule();
    }

    PegLegRule<JSONOne.JObject> chars() {
      return firstOf(escapedChar(), normalChar());
    }

    PegLegRule<JSONOne.JObject> escapedChar() {
      return seqOf("\\", firstOf(anyOf("\"\\/fnrt"), unicode()));
    }

    PegLegRule<JSONOne.JObject> normalChar() {
      return noneOf("\"\\");
    }

    PegLegRule<JSONOne.JObject> unicode() {
      return seqOf("u", hex(), hex(), hex(), hex());
    }

    PegLegRule<JSONOne.JObject> integer() {
      return seqOf(optOf(anyOf("+-")), firstOf('0', seqOf(charRange('1', '9'), zeroPlusOf(digit()))));
    }

    PegLegRule<JSONOne.JObject> digits() {
      return onePlusOf(digit());
    }

    PegLegRule<JSONOne.JObject> digit() {
      return charRange('0', '9');
    }

    PegLegRule<JSONOne.JObject> hex() {
      return firstOf(charRange('0', '9'), charRange('a', 'f').ignoreCase());
    }

    PegLegRule<JSONOne.JObject> frac() {
      return seqOf('.', digits());
    }

    PegLegRule<JSONOne.JObject> exp() {
      return seqOf(ch('e').ignoreCase(), optOf(anyOf("+-")), digits());
    }

  }

  @Test
  public void testSimpleJson() throws IOException {
    Json json = new Json();
    json.using("[1 ,2, 3,4]");
    PegLegParser.RuleReturn<JSONOne.JObject> ret = json.parse(json.jsonArray());
    assertThat(ret.matched(), is(true));
    JSONOne.JObject obj = json.values().pop();
    assertThat(obj.getType(), is(JSONOne.Type.ARRAY));
    assertThat(obj.arrayValue().get(0).numberValue().intValue(), is(1));
    assertThat(obj.arrayValue().get(1).numberValue().intValue(), is(2));
    assertThat(obj.arrayValue().get(2).numberValue().intValue(), is(3));
    assertThat(obj.arrayValue().get(3).numberValue().intValue(), is(4));
    assertThat(obj.arrayValue().size(), is(4));

    json.using("[1 ,2, 3,4 5]");
    ret = json.parse(json.jsonArray());
    assertThat(ret.matched(), is(false));

    json.using("{ \"foo\": true }");
    ret = json.parse(json.json());
    assertThat(ret.matched(), is(true));
    obj = json.values().pop();
    assertThat(obj.getType(), is(JSONOne.Type.MAP));
    assertThat(obj.mapValue().getBoolean("foo", false), is(true));
    assertThat(obj.mapValue().size(), is(1));

    json.using("[   10, true, { \"foo\":1001 }, null, [false, 10.3] ]");
    ret = json.parse(json.json());
    assertThat(ret.matched(), is(true));
    obj = json.values().pop();
    assertThat(obj.getType(), is(JSONOne.Type.ARRAY));
    assertThat(obj.arrayValue().get(0).numberValue().intValue(), is(10));
    assertThat(obj.arrayValue().get(1).boolValue(), is(true));
    assertThat(obj.arrayValue().get(2).mapValue().get("foo").numberValue().intValue(), is(1001));
    assertThat(obj.arrayValue().get(3).nullValue(), is(true));
    assertThat(obj.arrayValue().get(4).arrayValue().get(0).boolValue(), is(false));
    assertThat(obj.arrayValue().get(4).arrayValue().get(1).numberValue().doubleValue(), is(10.3d));

    json.using("[   10,, true, { \"foo\":10 }, null ]");
    ret = json.parse(json.json());
    assertThat(ret.matched(), is(false));

    json.using("[  10, true, { \"foo\":1001, \"blah\": { \"a\":1, \"b\":2 }}, null, [false, [-1, -2, false, \"thingy\"], 10.3] ]");
    ret = json.parse(json.json());
    assertThat(ret.matched(), is(true));

    CharSequence inp = fromReader(new InputStreamReader(PegLegParserTest.class.getResourceAsStream("pokedex.json")));
    json.using(inp);
    ret = json.parse(json.json());
    assertThat(ret.matched(), is(true));
    assertThat(ret.matchLen(), is(inp.length()));

    inp = "{ \"foo\":\"foo\\nbar\"}";
    json.using(inp);
    ret = json.parse(json.json());
    assertThat(ret.matched(), is(true));
    JSONOne.JMap mval = json.values().peek().get().mapValue();
    String str = mval.get("foo").stringValue();
    assertThat(str.indexOf('\n') >= 0, is(true));

  }

  /**
   * This is a grammar to parse a traversal language for json-style
   * nested maps and arrays. .foo.bar.[4] gets the map at key "foo", then
   * the array inside that at "bar", then the 4th element of that array.
   * <p> a single step spec can be .["keyname"], .[keyname], .keyname,
   * .[number,], [-number], [from:to], or [] for wildcard.
   */
  static class Dots extends PegLegParser<String> {

    PegLegRule<String> steps() {
      return seqOf(firstOf(onePlusOf(singleStep()), '.'), eof());
    }

    private Runnable pushLast() {
      return () -> values().push(match().get());
    }

    PegLegRule<String> singleStep() {
      return seqOf('.', firstOf(emptyAngle(), simpleStep(), quoteStep(), angleStep()));
    }

    PegLegRule<String> simpleStep() {
      return seqOf(simpleString(), exec(pushLast()));
    }

    PegLegRule<String> quoteStep() {
      return seqOf('"', onePlusOf(chars()), exec(pushLast()), '"');
    }

    PegLegRule<String> emptyAngle() {
      return seqOf(str("[]"), pushLast());
    }

    PegLegRule<String> angleStep() {
      return seqOf('[', seqOf(firstOf(fullAngle(), seqOf(simpleString(), pushLast()), arrSpec()), ']'));
    }

    PegLegRule<String> fullAngle() {
      return seqOf('"', onePlusOf(chars()), pushLast(), '"');
    }

    PegLegRule<String> simpleString() {
      return seqOf(alpha(), zeroPlusOf(firstOf(digit(), alpha())));
    }

    PegLegRule<String> arrSpec() {
      return seqOf(firstOf(sliceArraySpec(), singleArraySpec()), exec(pushLast()));
    }

    PegLegRule<String> sliceArraySpec() {
      return seqOf(singleArraySpec(), ':', singleArraySpec());
    }

    PegLegRule<String> singleArraySpec() {
      return seqOf(optOf('-'), onePlusOf(digit()));
    }

    PegLegRule<String> digit() {
      return charRange('0', '9');
    }

    PegLegRule<String> alpha() {
      return onePlusOf(charRange('a', 'z').ignoreCase());
    }

    PegLegRule<String> chars() {
      return firstOf(escapedChar(), normalChar());
    }

    PegLegRule<String> escapedChar() {
      return seqOf("\\", firstOf(anyOf("\"\\/fnrt"), unicode()));
    }

    CharTerminal<String> normalChar() {
      return noneOf("\"\\");
    }

    PegLegRule<String> unicode() {
      return seqOf("u", hex(), hex(), hex(), hex());
    }

    PegLegRule<String> hex() {
      return firstOf(charRange('0', '9'), charRange('a', 'f').ignoreCase());
    }
  }

  @Test
  public void testDots() {
    Dots parser = new Dots();
    parser.using(".foo.[\"b\\\\ar\"].[].[5].\"baz\".[-1].[3:2]");
    PegLegParser.RuleReturn<String> ret = parser.parse(parser.steps());
    assertThat(ret.matched(), is(true));
    assertThat(parser.values().allValues().size(), is(7));
    List<String> vals = parser.values().allValues();

    // note they come out of the values stack backwards.
    assertThat(vals.get(0), is("3:2"));
    assertThat(vals.get(1), is("-1"));
    assertThat(vals.get(2), is("baz"));
    assertThat(vals.get(3), is("5"));
    assertThat(vals.get(4), is("[]"));
    assertThat(vals.get(5), is("b\\\\ar"));
    assertThat(vals.get(6), is("foo"));

    parser.using(".foo.bar.[].[]");
    ret = parser.parse(parser.steps());
    assertThat(ret.matched(), is(true));
    assertThat(parser.values().allValues().size(), is(4));

    parser.using(".foo.bar.[].[1a]");
    ret = parser.parse(parser.steps());
    assertThat(ret.matched(), is(false));

    parser.using(".");
    ret = parser.parse(parser.steps());
    assertThat(ret.matched(), is(true));
    assertThat(parser.values().allValues().size(), is(0));

    parser.using(".");
    ret = parser.parse(parser.steps());
    assertThat(ret.matched(), is(true));
    assertThat(parser.values().allValues().size(), is(0));
  }

  static CharSequence fromReader(Reader in) throws IOException {
    StringBuilder sb = new StringBuilder(1024);
    char[] chars = new char[1024];
    for (int read; ((read = in.read(chars)) >= 0); ) { sb.append(chars, 0, read); }
    return sb;
  }

}

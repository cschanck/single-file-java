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
import org.sfj.exemplars.JsonPegParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
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
   * need to be a lambda.
   * <p>Also, you see the use of Ref variables and exec() calls to actually
   * calculate the values.
   */
  static class CalcParser extends PegLegParser<Long> {

    PegLegRule<Long> one() {
      return seqOf(expression(), eof());
    }

    PegLegRule<Long> expression() {
      Ref<String> op = new Ref<>("");
      return seqOf(term(), zeroPlusOf(seqOf(anyOf("+-"), ex(() -> op.set(match().get())), term(), ex(() -> doMath(op))))).refs(op);
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
        return seqOf(factor(),
          zeroPlusOf(seqOf(anyOf("*/"), ex(() -> op.set(match().get())), factor(), ex(() -> doMath(op))))).refs(op).rule();
      };
    }

    PegLegRule<Long> factor() {
      return firstOf(number(), seqOf(ws('('), expression(), ws(')')));
    }

    PegLegRule<Long> number() {
      return seqOf(ws(), onePlusOf(charRange('0', '9')), ex(() -> values().push(parseLong(match().orElse("0")))), ws());
    }
  }

  @Test
  public void testCalc() {
    CalcParser parser = new CalcParser();
    parser.using("1+2");
    PegLegParser.RuleReturn<Long> ret = parser.parse(parser.expression());
    assertThat(ret.matched(), is(true));
    assertThat(parser.values().peek().get(), is(3L));

    parser = new CalcParser();
    parser.using("1+2 *(120- 100)/ 20 ");
    ret = parser.one().rule();
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

  @Test
  public void testSimpleJson() throws IOException {
    JsonPegParser json = new JsonPegParser();
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

  @Test
  public void testJSONFailures() {
    JsonPegParser json = new JsonPegParser();
    String str = "[1, 3 4, 10.3]";
    json.using(str);
    PegLegParser.RuleReturn<JSONOne.JObject> ret = json.parse(json.jsonArray());
    assertThat(ret.matched(), is(false));
    assertThat(json.getFarthestSuccessfulPos().srcPos, is(6));
    System.out.println(json.getFailureMessage());

    str = "[  10, true, [-1, -2, blerg, false, { \"1\" : 3, \"10\" : 4, } , \"thingy\"], 10.3]";
    json.using(str);
    ret = json.parse(json.json());
    assertThat(ret.matched(), is(false));
    assertThat(json.getFarthestSuccessfulPos().srcPos, is(22));
    System.out.println(json.getFailureMessage());

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
      return seqOf(simpleString(), ex(pushLast()));
    }

    PegLegRule<String> quoteStep() {
      return seqOf('"', onePlusOf(chars()), ex(pushLast()), '"');
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
      return seqOf(firstOf(sliceArraySpec(), singleArraySpec()), ex(pushLast()));
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

  static class CmdParser extends PegLegParser<Object> {
    public CmdParser() {
    }

    PegLegRule<Object> top() {
      return seqOf(ws(), onePlusOf(cmd(), ws()), ws('{'), arg(), ws('}'));
    }

    PegLegRule<Object> cmd() {
      return dictionaryOf("one", "two", "three", "happy").ignoreCase();
    }

    PegLegRule<Object> arg() {
      return zeroPlusOf(firstOf(charRange('0', '9'), charRange('a', 'z').ignoreCase()));
    }
  }

  @Test
  public void testDictParser() {
    CmdParser parser = new CmdParser();
    parser.using("one { thing }");
    PegLegParser.RuleReturn<Object> ret = parser.parse(parser.top());
    assertThat(ret.matched(), is(true));

    parser.using("oNe { thing }");
    ret = parser.parse(parser.top());
    assertThat(ret.matched(), is(true));

    parser.using("onmes { thing }");
    ret = parser.parse(parser.top());
    assertThat(ret.matched(), is(false));

    parser.using("happy { times }");
    ret = parser.parse(parser.top());
    assertThat(ret.matched(), is(true));

    parser.using("one two { times }");
    ret = parser.parse(parser.top());
    assertThat(ret.matched(), is(true));

  }
}

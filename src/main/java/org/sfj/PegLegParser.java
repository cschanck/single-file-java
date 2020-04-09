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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

/**
 * <p>This class implements a complete PEG parser, a la https://en.wikipedia.org/wiki/Parsing_expression_grammar
 * <p>I have long been a big fan of the Parboiled parser framework ( https://github.com/sirthias/parboiled/wiki ),
 * especially for quick and dirty things. But I also always disliked the proxying/byte code manipulation
 * in it. A looong while ago I looked at building one myself with just anon classes, around Java 6
 * timeframe, but it was super clunky. For a while now I have wanted to take another swing at it
 * using lambdas; it seemed like a way to do all of what Parboiled (and Rats!, etc) did without
 * needing anything too exotic.
 * <p>Turns out, yup, works pretty well. An approach like this will never be the quickest parser
 * to run, I mean, there is no packrat processing, no memoization, etc. So it is not a speed
 * demon. But the intention was to make it super expressive and a speed demon to write parsers.
 * <p>See the PegLeParser.adoc file for an overview on how it works, and see the unit test
 * class for a set of example grammars.
 * @param <V> Value stack type.
 * @author cschanck
 */
public class PegLegParser<V> implements Supplier<PegLegParser<V>> {
  private Source source;
  private String whiteSpace = " \t\r\n";
  private String lineSep = System.lineSeparator();
  private LinkedList<SourceFrame> frame = new LinkedList<>();
  private Values<V> values = new Values<>();
  private RuleReturn<V> lastReturn = null;
  private RuleReturn<V> lastSuccessfulReturn = null;
  public SourcePosition farthestSuccessfulPos = new SourcePosition();

  /**
   * Random holder class for intra rule parser data manipulation. Used within sibling rules.
   * @param <V>
   */
  static class Ref<V> {
    private final Supplier<V> init;
    private LinkedList<V> stack = new LinkedList<>();

    public Ref(V value) { this.init = () -> value; }

    public Ref(Supplier<V> init) { this.init = init == null ? () -> null : init; }

    public V get() { return stack.peekFirst(); }

    public void set(V value) { stack.set(0, value); }

    public String toString() { return "Ref{" + get() + '}'; }

    void enterRef() { stack.push(init.get()); }

    void exitRef() { stack.pop(); }

  }

  /**
   * A Rule. Core to parsing is defining your own rules.
   * @param <V> Value type
   */
  @FunctionalInterface
  interface PegLegRule<V> {
    RuleReturn<V> rule();
  }

  /**
   * An execution, which return true to continue parsing, false to stop.
   */
  @FunctionalInterface
  interface Exec {
    boolean exec();
  }

  /**
   * Terminal rule, with optional ignoring of case.
   * @param <V> value type
   */
  @FunctionalInterface
  interface TerminalRule<V> {
    RuleReturn<V> rule(boolean ignore);
  }

  class ParentRule implements PegLegRule<V> {
    private PegLegRule<V> rule;
    private Ref<?>[] refs = null;

    public ParentRule(PegLegRule<V> rule) {
      this.rule = rule;
    }

    public PegLegRule<V> refs(Ref<?>... refs) {
      this.refs = refs;
      return this;
    }

    @Override
    public RuleReturn<V> rule() {
      if (refs == null) { return rule.rule(); }
      for (Ref<?> r : refs) { r.enterRef(); }
      try {
        return rule.rule();
      } finally {
        for (Ref<?> r : refs) { r.exitRef(); }
      }
    }
  }

  static class SourcePosition {
    int line = 1;
    int linePos = 0;
    int srcPos = 0;

    public SourcePosition() { }

    public SourcePosition(int srcPos, int line, int linePos) {
      this.line = line;
      this.linePos = linePos;
      this.srcPos = srcPos;
    }

    public SourcePosition dup() {
      return new SourcePosition(srcPos, line, linePos);
    }

    @Override
    public String toString() {
      return "Source @" + srcPos + "(line=" + line + ":linePos=" + linePos + ")";
    }
  }

  static class SourceFrame extends SourcePosition {
    final String name;

    public SourceFrame(String name, SourcePosition pos) {
      super(pos.srcPos, pos.line, pos.linePos);
      this.name = name;
    }
  }

  private static class Source {
    private final CharSequence src;
    private SourcePosition state;

    public Source(CharSequence src) {
      this.state = new SourcePosition();
      this.src = src;
    }

    public boolean atEnd() { return state.srcPos >= src.length(); }

    public SourcePosition getState() { return state.dup(); }

    public boolean peekOneOf(String chars) {
      if (state.srcPos < src.length()) { return chars.indexOf(src.charAt(state.srcPos)) >= 0; }
      return false;
    }

    public void setState(SourcePosition state) { this.state = state; }

    public int nextChar() {
      if (state.srcPos < src.length()) {
        int ret = src.charAt(state.srcPos++);
        if (((char) ret) == '\n') {
          state.line++;
          state.linePos = 0;
        } else {
          state.linePos++;
        }
        return ret;
      }
      return -1;
    }

    public String substring(int pos, int len) { return src.subSequence(pos, pos + len).toString(); }

    public String substring(int pos) { return substring(pos, src.length() - pos); }
  }

  private static class SingleNode<V> {
    V value;
    SingleNode<V> down;

    public SingleNode(V value, SingleNode<V> down) {
      this.value = value;
      this.down = down;
    }

    @Override
    public String toString() { return Objects.toString(value); }
  }

  /**
   * Stack of values. Supports normal stack ops like peek(), pop(), pus(), etc.
   * @param <V> Value type
   */
  public static class Values<V> {
    private SingleNode<V> top = null;

    /**
     * Snapshot the values in a list, top element as the 0th element
     * @return list of values.
     */
    public List<V> allValues() {
      ArrayList<V> ret = new ArrayList<>();
      for (SingleNode<V> n = top; n != null; n = n.down) { ret.add(n.value); }
      return ret;
    }

    /**
     * All the values in reverse.
     * @return list of values
     */
    public List<V> reverse() {
      List<V> all = allValues();
      Collections.reverse(all);
      return all;
    }

    public Optional<V> peek() {
      SingleNode<V> ret = top;
      return (ret != null) ? Optional.ofNullable(ret.value) : Optional.empty();
    }

    public void push(V v) { top = new SingleNode<>(v, top); }

    public void swap() {
      V p1 = pop();
      V p2 = pop();
      push(p1);
      push(p2);
    }

    public V pop() {
      V ret = top.value;
      top = top.down;
      return ret;
    }

    /**
     * Pop the value 'pos' down the stack. pos=0 is the top, pos=1 is one below the top, etc.
     * @param pos position to pop off
     * @return value
     */
    public V pop(int pos) {
      if (pos == 0) { return pop(); }
      LinkedList<V> hold = new LinkedList<>();
      for (int i = 0; i <= pos; i++) { hold.push(pop()); }
      V ret = hold.pop();
      while (!hold.isEmpty()) { push(hold.pop()); }
      return ret;
    }

    SingleNode<V> save() { return top; }

    void restore(SingleNode<V> prior) { top = prior; }

    @Override
    public String toString() { return "Values:" + allValues(); }
  }

  private int frameDepth() { return frame.size(); }

  private boolean peekOneOf(String chars) { return source.peekOneOf(chars); }

  private void tossTopFrame() { frame.pop(); }

  private SingleNode<V> saveValues() { return values.save(); }

  private void restoreValues(SingleNode<V> point) { values.restore(point); }

  private void pushFrame() { pushFrame(null); }

  private void pushFrame(String name) { frame.push(new SourceFrame(name, source.getState())); }

  private void trimFramesTo(int size) { while (frame.size() > size) { frame.pop(); } }

  private void resetToLastFrame() {
    SourcePosition state = frame.pop();
    source.setState(state);
  }

  private int nextChar() { return source.nextChar(); }

  private void rollback(int frameCount, SingleNode<V> oldTop) {
    trimFramesTo(frameCount);
    restoreValues(oldTop);
  }

  public List<String> parseTrail() {
    List<String> ret = frame.stream().filter(Objects::nonNull).map(s -> s.name).collect(toList());
    Collections.reverse(ret);
    return ret;
  }

  /**
   * The last rule's matched literal.
   * @return last match literal
   */
  public Optional<String> match() {
    if (lastReturn != null && lastReturn.matched()) {
      return Optional.of(source.substring(lastReturn.match.srcPos, lastReturn.matchLen));
    }
    return Optional.empty();
  }

  /**
   * Last rule return.
   * @return rule return
   */
  public RuleReturn<V> getLastReturn() { return lastReturn; }

  public RuleReturn<V> getLastSuccessfulReturn() { return lastSuccessfulReturn; }

  private RuleReturn<V> ruleReturn(boolean matched, boolean consumed) {
    RuleReturn<V> ret = lastReturn = new RuleReturn<>(this, matched, consumed);
    if (consumed) { tossTopFrame(); } else { resetToLastFrame(); }
    if (matched && consumed) {
      if (ret.matchLen + ret.match.srcPos > farthestSuccessfulPos.srcPos) {
        farthestSuccessfulPos.srcPos = ret.match.srcPos + ret.matchLen;
        farthestSuccessfulPos.line = ret.match.line;
        farthestSuccessfulPos.linePos = ret.match.linePos + ret.matchLen;
      }
      if ((lastSuccessfulReturn == null) || (ret.match.srcPos > lastSuccessfulReturn.match.srcPos)) {
        lastSuccessfulReturn = ret;
      }
    }
    return ret;
  }

  /**
   * Rule return.
   */
  public static class RuleReturn<V> {
    private final PegLegParser<V> parser;
    private final boolean consumed;
    private final SourcePosition match;
    private int matchLen = 0;

    private RuleReturn(PegLegParser<V> parser, boolean matched, boolean consumed) {
      this.parser = parser;
      this.consumed = consumed;
      if (matched) {
        SourcePosition prev = parser.frame.get(0);
        this.match = new SourcePosition(prev.srcPos, prev.line, prev.linePos);
        matchLen = parser.source.state.srcPos - prev.srcPos;
      } else {
        this.match = null;
      }
    }

    public Optional<String> match() {
      if (matched()) {
        return Optional.of(parser.source.substring(match.srcPos, matchLen));
      }
      return Optional.empty();
    }

    /**
     * Line of match, first line is 1.
     * @return line
     */
    public int matchLine() { return match.line; }

    /**
     * Line offset of match, 1st char on line is 0.
     * @return offset
     */
    public int matchLineOffset() { return match.linePos; }

    /**
     * Char offset into input where match occurred.
     * @return line offset
     */
    public int matchPos() { return match.srcPos; }

    /**
     * Length of match.
     * @return match len
     */
    public int matchLen() { return matchLen; }

    /**
     * Did we match.
     * @return true if matched
     */
    public boolean matched() { return match != null; }

    /**
     * Did the rule consume chars.
     * @return true if consumed
     */
    public boolean consumed() { return consumed; }

    @Override
    public String toString() {
      if (matched()) {
        return String.format("RuleReturn match=%s(%s) @ %d for %d (line %d, nextPos=%d)", match != null, consumed, match.srcPos,
          matchLen, match.line, match.linePos);
      } else {
        return "RuleReturn match=false";
      }
    }

  }

  /**
   * Terminal based on some set of characters. Allows for ignoring of case.
   * @param <V> value type
   */
  protected static class CharTerminal<V> implements PegLegRule<V> {
    private final TerminalRule<V> delegate;
    boolean ignoreCase;

    public CharTerminal(TerminalRule<V> delegate) {
      this.delegate = delegate;
    }

    /**
     * Ignore case.
     * @return rule
     */
    public CharTerminal<V> ignoreCase() {
      ignoreCase = true;
      return this;
    }

    @Override
    public RuleReturn<V> rule() { return delegate.rule(ignoreCase); }
  }

  public PegLegParser() { }

  @SuppressWarnings("unchecked")
  private class Step {
    private PegLegRule<V> rule = null;
    private Exec exec = null;

    public Step(Object thing) {
      if (thing instanceof CharSequence) {
        this.rule = str((CharSequence) thing);
      } else if (thing instanceof Character) {
        this.rule = ch((Character) thing);
      } else if (thing instanceof PegLegParser.PegLegRule) {
        this.rule = (PegLegRule<V>) thing;
      } else if (thing instanceof Exec) {
        this.exec = (Exec) thing;
      } else if (thing instanceof Runnable) { this.exec = ex((Runnable) thing); } else {
        throw new RuntimeException("Expected String/char/PegLegRule/Exec/Runnable; found: " + thing);
      }
    }

    public Exec asExec() { return this.exec; }

    boolean isRule() { return rule != null; }

    PegLegRule<V> asRule() { return rule; }
  }

  private Map.Entry<Map<String, String>, int[]> dictTable(String... options) {
    List<String> key = Arrays.asList(options);
    HashSet<Integer> lengths = new HashSet<>();
    Map<String, String> table = new HashMap<>();
    for (String s : options) {
      table.put(s.toUpperCase(), s);
      lengths.add(s.length());
    }
    Map.Entry<Map<String, String>, int[]>
      ret =
      new AbstractMap.SimpleImmutableEntry<>(table, lengths.stream().mapToInt(i -> i).sorted().toArray());
    return ret;
  }

  private RuleReturn<V> eofRule() {
    get().pushFrame("eof()");
    return get().ruleReturn(source.atEnd(), false);
  }

  private RuleReturn<V> eolRule() { return innerStr("eol()", lineSep).rule(); }

  private RuleReturn<V> wsRule() {
    get().pushFrame("ws()");
    consumeWS();
    return get().ruleReturn(true, true);
  }

  private void consumeWS() {
    for (; ; get().nextChar()) { if (!get().peekOneOf(whiteSpace)) { return; } }
  }

  private static boolean isCharMatch(boolean iCase, char ch1, char ch2) {
    return (iCase) ? Character.toUpperCase(ch1) == Character.toUpperCase(ch2) : (ch1 == ch2);
  }

  private RuleReturn<V> innerTest(String name, PegLegRule<V> rule) {
    SingleNode<V> values = get().saveValues();
    get().pushFrame(name);
    int cnt = get().frameDepth();
    RuleReturn<V> ret = rule.rule();
    get().rollback(cnt, values);
    return ret;
  }

  @SuppressWarnings("unchecked")
  private List<Step> asSteps(Object... objs) {
    if (objs.length == 0) {
      return Collections.emptyList();
    } else if (objs.length == 1) {
      if (objs[0] instanceof PegLegParser.Step) {
        return Collections.singletonList((Step) objs[0]);
      }
      return Collections.singletonList(new Step(objs[0]));
    }
    ArrayList<Step> ret = new ArrayList<>();
    for (Object o : objs) {
      ret.add(o instanceof PegLegParser.Step ? (PegLegParser<V>.Step) o : new Step(o));
    }
    return ret;
  }

  private RuleReturn<V> innerTimesOf(String name, int min, int max, Object... objs) {
    PegLegRule<V> rule = seqOf(objs);
    SingleNode<V> values = get().saveValues();
    get().pushFrame(name);
    int cnt = get().frameDepth();
    int matchCount = 0;
    for (; matchCount < max; ) {
      RuleReturn<V> ret = rule.rule();
      if (ret.matched()) {
        ++matchCount;
      } else {
        break;
      }
    }
    get().trimFramesTo(cnt);
    if (matchCount >= min && matchCount <= max) { return get().ruleReturn(true, true); }
    get().restoreValues(values);
    return get().ruleReturn(false, false);
  }

  public void setWhiteSpace(String whiteSpace) { this.whiteSpace = whiteSpace; }

  public void setLineSeparator(String sep) { this.lineSep = sep; }

  public String getWhiteSpace() { return whiteSpace; }

  /**
   * Reset parser for given input.
   * @param input string input
   * @return this parser.
   */
  public PegLegParser<V> using(CharSequence input) {
    this.source = new Source(input);
    frame = new LinkedList<>();
    values = new Values<>();
    lastReturn = null;
    lastSuccessfulReturn = null;
    farthestSuccessfulPos = new SourcePosition();
    pushFrame();
    return this;
  }

  /**
   * Get context object at the moment.
   * @return context object
   */
  public PegLegParser<V> get() { return this; }

  IllegalArgumentException error(String message) {
    return new IllegalArgumentException("[" + source.getState().line + ":" + source.getState().linePos + "] " + message);
  }

  /**
   * Generate a terminal rule for a specific character.
   * @param ch char
   * @return rule
   */
  public CharTerminal<V> ch(char ch) { return innerStr("ch(" + ch + ")", Character.toString(ch)); }

  /**
   * Generate a terminal rule for a range of characters.
   * @param from lower bound
   * @param to upper bound.
   * @return rule
   */
  public CharTerminal<V> charRange(char from, char to) {
    if ((int) from > (int) to) { throw error("Char range is illegal: [" + from + " to " + to + "]"); }
    return new CharTerminal<>((ignoreCase) -> {
      get().pushFrame("charRange(" + from + "," + to + ")");
      int n = get().nextChar();
      if (n >= 0) {
        for (int i = from; i <= (int) to; i++) {
          if (isCharMatch(ignoreCase, (char) n, (char) i)) {
            return get().ruleReturn(true, true);
          }
        }
      }
      return get().ruleReturn(false, false);
    });
  }

  /**
   * Generate a rule for a specific string (char sequence)
   * @param string char sequence
   * @return rule
   */
  public CharTerminal<V> str(CharSequence string) {
    return innerStr("str(" + string + ")", string);
  }

  private CharTerminal<V> innerStr(String name, CharSequence string) {
    return new CharTerminal<>((ignoreCase) -> {
      get().pushFrame(name);
      boolean ret = extremelyInnerString(string, ignoreCase);
      return get().ruleReturn(ret, ret);
    });
  }

  /**
   * Generates a rule for the char sequence, allowing any amount of preceding/following whitespace.
   * @param string char sequence
   * @return rule
   */
  public CharTerminal<V> ws(CharSequence string) {
    return new CharTerminal<>((ignoreCase) -> {
      get().pushFrame("ws(\"" + string + "\")");
      consumeWS();
      if (!extremelyInnerString(string, ignoreCase)) { return get().ruleReturn(false, false); }
      consumeWS();
      return get().ruleReturn(true, true);
    });
  }

  private boolean extremelyInnerString(CharSequence string, boolean ignoreCase) {
    for (int i = 0; i < string.length(); i++) {
      int n = get().nextChar();
      if (n < 0) { return false; }
      if (!isCharMatch(ignoreCase, string.charAt(i), (char) n)) { return false; }
    }
    return true;
  }

  /**
   * Generates a rule for the char, allowing any amount of preceding/following whitespace.
   * @param ch character
   * @return rule
   */
  public CharTerminal<V> ws(char ch) { return ws(Character.toString(ch)); }

  /**
   * Whitespace rule. matches zero or more whitespace chars.
   * @return rule
   */
  public PegLegRule<V> ws() { return this::wsRule; }

  /**
   * Generates a rule which matches any single char in the char sequence.
   * @param string candidate chars.
   * @return rule
   */
  public CharTerminal<V> anyOf(CharSequence string) {
    return new CharTerminal<>((ignoreCase) -> {
      get().pushFrame("anyOf(" + string + ")");
      int n = get().nextChar();
      if (n < 0) { return get().ruleReturn(false, false); }
      for (int i = 0; i < string.length(); i++) {
        if (isCharMatch(ignoreCase, string.charAt(i), (char) n)) { return get().ruleReturn(true, true); }
      }
      return get().ruleReturn(false, false);
    });
  }

  /**
   * Dictionary lookup, optimized.
   * @param strings set of strings to match
   * @return rule
   */
  public CharTerminal<V> dictionaryOf(String... strings) {
    final Map.Entry<Map<String, String>, int[]> tbl = dictTable(strings);
    return new CharTerminal<>((ignoreCase) -> {
      get().pushFrame("dictionaryOf(" + Arrays.asList(strings) + ")");
      boolean ret = innerDict(tbl, ignoreCase);
      return get().ruleReturn(ret, ret);
    });
  }

  private boolean innerDict(Map.Entry<Map<String, String>, int[]> tbl, boolean ignoreCase) {
    for (int len : tbl.getValue()) {
      if (source.state.srcPos + len <= source.src.length()) {
        String actual = source.substring(source.state.srcPos, len);
        String targetWCase = tbl.getKey().get(actual.toUpperCase());
        if ((targetWCase != null && ignoreCase) || (actual.equals(targetWCase))) {
          for (int i = 0; i < actual.length(); i++) { source.nextChar(); }
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Rule that matches any character not contained in the char sequence specified.
   * @param string char sequence not to match
   * @return rule
   */
  public CharTerminal<V> noneOf(CharSequence string) {
    return new CharTerminal<>((ignoreCase) -> {
      get().pushFrame("noneOf(" + string + ")");
      int n = get().nextChar();
      if (n >= 0) {
        for (int i = 0; i < string.length(); i++) {
          if (isCharMatch(ignoreCase, string.charAt(i), (char) n)) { return get().ruleReturn(false, false); }
        }
        return get().ruleReturn(true, true);
      }
      return get().ruleReturn(false, false);
    });
  }

  public PegLegRule<V> eof() { return this::eofRule; }

  public PegLegRule<V> eol() { return this::eolRule; }

  /**
   * Core PEG rule: "Sequence". Matches a list of sub rules in order. All must match,
   * or none do. Probably the most important rule.
   * @param objs rules/string literals/char literals/execs
   * @return sequence rule
   */
  public ParentRule seqOf(Object... objs) {
    List<Step> steps = asSteps(objs);
    PegLegRule<V> rule;
    if (steps.size() == 1 && steps.get(0).isRule()) {
      rule = steps.get(0).asRule();
    } else {
      rule = () -> {
        get().pushFrame("seqOf()");
        SingleNode<V> values = get().saveValues();
        int cnt = get().frameDepth();
        for (Step r : steps) {
          if (r.isRule()) {
            RuleReturn<V> ret = r.asRule().rule();
            if (!ret.matched()) {
              get().rollback(cnt, values);
              return get().ruleReturn(false, false);
            }
          } else {
            if (!r.asExec().exec()) {
              get().rollback(cnt, values);
              return get().ruleReturn(false, false);
            }
          }
        }
        get().trimFramesTo(cnt);
        return get().ruleReturn(true, true);
      };
    }
    return new ParentRule(rule);
  }

  /**
   * Core PEG rule "Choice", here called firstOf. Tries each sub rule in turn, return first match.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule firstOf(Object... objs) {
    List<Step> steps = asSteps(objs);
    PegLegRule<V> rule;
    if (steps.size() == 1 && steps.get(0).isRule()) {
      rule = steps.get(0).asRule();
    } else {
      rule = () -> {
        get().pushFrame("firstOf()");
        SingleNode<V> values = get().saveValues();
        int cnt = get().frameDepth();
        for (Step r : steps) {
          if (r.isRule()) {
            get().pushFrame();
            RuleReturn<V> ret = r.asRule().rule();
            if (ret.matched()) {
              get().trimFramesTo(cnt);
              return get().ruleReturn(true, true);
            }
            get().resetToLastFrame();
          } else {
            if (!r.asExec().exec()) {
              get().rollback(cnt, values);
              return get().ruleReturn(false, false);
            }
          }
        }
        get().rollback(cnt, values);
        return get().ruleReturn(false, false);
      };
    }
    return new ParentRule(rule);
  }

  /**
   * Core PEG Rule, "Test" returns true if sequence of rules matches, but does not consume input.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule testOf(Object... objs) {
    PegLegRule<V> rule = seqOf(objs);
    return new ParentRule(() -> get().ruleReturn(innerTest("testOf()", rule).matched(), false));
  }

  /**
   * Core PEG rule: "TestNot". Matches in set of sub rules does not match, does not consume input.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule testNotOf(Object... objs) {
    PegLegRule<V> rule = seqOf(objs);
    return new ParentRule(() -> get().ruleReturn(!innerTest("testNotOf()", rule).matched(), false));
  }

  /**
   * Rule with matches only if matches set of rules between min and max times. Greedy, but stops at max.
   * @param min min times to match
   * @param max max times to match
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule timesOf(int min, int max, Object... objs) {
    if (min < 0 || max < 0 || max < min) { throw error("illegal min/max for timesof [" + min + "/" + max + "]"); }
    return new ParentRule(() -> innerTimesOf("timesOf(" + min + "-" + max + ")", min, max, objs));
  }

  /**
   * Rule with matches only if matches set of rules exactly many times.
   * @param many times to match
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule timesOf(int many, Object... objs) {
    if (many <= 0) { throw error("illegal many  timesOf [" + many + "]"); }
    return new ParentRule(() -> innerTimesOf("timesOf(" + many + ")", many, many, objs));
  }

  /**
   * Core PEG rule "ZeroOrMore", matches the given set of rules, greedily, zero or more times.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule zeroPlusOf(Object... objs) {
    return new ParentRule(() -> innerTimesOf("zeroPlusOf()", 0, Integer.MAX_VALUE, objs));
  }

  /**
   * Core PEG rule "OneOrMore", matches the given set of rules, greedily, one or more times.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule onePlusOf(Object... objs) {
    return new ParentRule(() -> innerTimesOf("onePlusOf()", 1, Integer.MAX_VALUE, objs));
  }

  /**
   * Core PEG rule "Optional", matches the given set of rules, greedily, zero or one times.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule optOf(Object... objs) {
    return new ParentRule(() -> innerTimesOf("optOf()", 0, 1, objs));
  }

  /**
   * Execs a blob of code, stops parsing if returns false.
   * @param del delegate
   * @return Exec
   */
  public Exec testExec(Exec del) { return del; }

  /**
   * Execs a blob of code, always continues parsing.
   */
  public Exec ex(Runnable del) {
    return () -> {
      del.run();
      return true;
    };
  }

  /**
   * Rule with matches any character.
   * @return rule
   */
  public PegLegRule<V> anyChar() {
    return () -> {
      get().pushFrame("anyChar()");
      int n = get().nextChar();
      return get().ruleReturn(n >= 0, n >= 0);
    };
  }

  /**
   * Always fails to match, consumes nothing
   * @return rule
   */
  public PegLegRule<V> nothing() {
    return () -> {
      get().pushFrame("nothing()");
      return get().ruleReturn(false, false);
    };
  }

  /**
   * Returns true, matches nothing, consumes nothing.
   * @return rule
   */
  public PegLegRule<V> empty() {
    return () -> {
      get().pushFrame("empty()");
      return get().ruleReturn(true, true);
    };
  }

  /**
   * Value stack.
   * @return value stack
   */
  public Values<V> values() { return values; }

  /**
   * A Parsing entry point.
   * @param rule rule to start with
   * @return rule return value
   */
  public RuleReturn<V> parse(PegLegRule<V> rule) { return rule.rule(); }

  public SourcePosition getFarthestSuccessfulPos() { return farthestSuccessfulPos; }

  public String getFailureMessage() {
    if (!getLastReturn().matched()) {
      return String.format("Parsing failure at line %d, position %d. Unrecognized input starts: [%s]", farthestSuccessfulPos.line,
        farthestSuccessfulPos.linePos, source.substring(farthestSuccessfulPos.srcPos));
    }
    return "Not a failure";
  }

  /**
   * name a rule
   */
  public PegLegRule<V> named(String name, PegLegRule<V> del) {
    return new ParentRule(() -> {
      pushFrame(name);
      RuleReturn<V> ret = del.rule();
      ruleReturn(ret.matched(), ret.consumed);
      return ret;
    });
  }
}
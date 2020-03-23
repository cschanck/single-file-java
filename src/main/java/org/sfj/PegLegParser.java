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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * <p>This class implements a complete PEG parser, a la https://en.wikipedia.org/wiki/Parsing_expression_grammar </p>
 * <p>I have long been a big fan of the Parboiled parser framework ( https://github.com/sirthias/parboiled/wiki ),
 * especially for quick and dirty things. But I also always disliked the proxying/byte code manipulation
 * in it. A looong while ago I looked at building one myself with just anon classes, around Java 6 timeframe, but
 * it was super clunky. For a while now I have wanted to take another swing at it using lambdas; it seemed like a way
 * to do all of what Parboiled (and Rats!, etc) did without needing anything too exotic. </p>
 * <p>Turns out, yup, works pretty well. An approach like this will never be the quickest parser to run,
 * I mean, there is no packrat processing, no memoization, etc. So it is not a speed demon. But
 * the intention was to make it super expressive and a speed demon to write parsers in.</p>
 * <p>For Object... rules, you can either use a String literal, a char literal, one
 * of the built in defined rules, an Exec lambda, or a Runnable.</p>
 * @param <V> Value stack type.
 * @author cschanck
 */
public class PegLegParser<V> implements Supplier<PegLegParser.Context<V>> {
  private Source source;
  private Context<V> context;
  private String whiteSpace = " \t\r\n";

  public static class PegLegException extends RuntimeException {
    public PegLegException(String s) { super(s); }
  }

  /**
   * Random holder class for intra rule parser data manipulation.
   * @param <V>
   */
  static class Ref<V> {
    private final Supplier<V> init;
    private LinkedList<V> stack = new LinkedList<>();

    public Ref(V value) { this.init = () -> value; }

    public Ref(Supplier<V> init) { this.init = init == null ? () -> null : init; }

    public V get() { return stack.peekFirst(); }

    public void set(V value) { stack.set(0, value); }

    @Override
    public String toString() { return "Ref{" + get() + '}'; }

    void enterRef() {
      stack.push(init.get());
    }

    void exitRef() {
      stack.pop();
    }

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

    PegLegRule<V> refs(Ref<?>... refs) {
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

  private static class SourceState {
    int line = 1;
    int linePos = 0;
    int bufferPos = 0;

    public SourceState dup() {
      SourceState ret = new SourceState();
      ret.line = line;
      ret.linePos = linePos;
      ret.bufferPos = bufferPos;
      return ret;
    }
  }

  private static class Source {
    private final CharSequence src;
    private SourceState state;

    public Source(CharSequence src, String lineSep) {
      this.state = new SourceState();
      if (lineSep.equals("\n")) { // linux, all is good.
        this.src = src;
      } else { // grind eol's down to newlines.
        StringBuilder sb = new StringBuilder(src.length());
        boolean first = true;
        for (String s : src.toString().split(lineSep)) {
          if (first) {first = false;} else {sb.append('\n');}
          sb.append(s);
        }
        this.src = sb;
      }
    }

    public boolean atEnd() { return state.bufferPos >= src.length(); }

    public SourceState getState() { return state.dup(); }

    public boolean peekOneOf(String chars) {
      if (state.bufferPos < src.length()) {
        return chars.indexOf(src.charAt(state.bufferPos)) >= 0;
      }
      return false;
    }

    public void setState(SourceState state) { this.state = state; }

    public int nextChar() {
      if (state.bufferPos < src.length()) {
        int ret = src.charAt(state.bufferPos++);
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
  }

  /*
   * Single linked list node for stack.
   */
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
      for (SingleNode<V> n = top; n != null; n = n.down) {
        ret.add(n.value);
      }
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
      for (int i = 0; i <= pos; i++) {
        hold.push(pop());
      }
      V ret = hold.pop();
      while (!hold.isEmpty()) {
        push(hold.pop());
      }
      return ret;
    }

    SingleNode<V> save() { return top; }

    void restore(SingleNode<V> prior) { top = prior; }

    @Override
    public String toString() { return "Values:" + allValues(); }
  }

  /**
   * Context object for a rule invocation.
   * @param <V> value stack type
   */
  public static class Context<V> {
    private final Source source;
    private LinkedList<SourceState> frame = new LinkedList<>();
    private Values<V> values = new Values<>();
    private RuleReturn<V> lastReturn = null;
    private RuleReturn<V> lastSuccessfulReturn = null;

    public Context(Source source) { this.source = source; }

    int frameDepth() { return frame.size(); }

    public boolean peekOneOf(String chars) { return source.peekOneOf(chars); }

    void tossTopFrame() { frame.pop(); }

    SingleNode<V> saveValues() { return values.save(); }

    void restoreValues(SingleNode<V> point) { values.restore(point); }

    /**
     * Value stack.
     * @return value stack
     */
    public Values<V> values() { return values; }

    void pushFrame() { frame.push(source.getState()); }

    void trimFramesTo(int size) {
      while (frame.size() > size) {
        frame.pop();
      }
    }

    void resetToLastFrame() {
      SourceState state = frame.pop();
      source.setState(state);
    }

    int nextChar() { return source.nextChar(); }

    void rollback(int frameCount, SingleNode<V> oldTop) {
      trimFramesTo(frameCount);
      restoreValues(oldTop);
    }

    /**
     * Where we are in the parse trail. Useful for debugging.
     * @param raw whether you just want a stack trace, or a massaged version.
     * @return list of stack points
     */
    public List<String> parseTrail(boolean raw) {
      Pattern pat = Pattern.compile("^.*[$](.+)[$][^$]*$");
      StackTraceElement[] trace = Thread.currentThread().getStackTrace();
      List<String> ret;
      if (!raw && stream(trace).anyMatch(t -> pat.matcher(t.getMethodName()).matches())) {
        ret = stream(trace).map((e) -> {
          Matcher m = pat.matcher(e.getMethodName());
          if (m.matches()) {
            return (e.getClassName().equals(PegLegParser.class.getName())) ? m.group(1) : e.getClassName() +
                                                                                          "." +
                                                                                          m.group(1);
          }
          return null;
        }).filter(Objects::nonNull).collect(toList());
      } else {
        ret = stream(trace).map(ste -> ste.getClassName() + "." + ste.getMethodName()).collect(toList());
      }
      Collections.reverse(ret);
      return ret;
    }

    /**
     * The last rule's matched literal.
     * @return last match literal
     */
    public Optional<String> match() {
      if (lastReturn != null && lastReturn.matched()) {
        return Optional.of(source.substring(lastReturn.matchPos, lastReturn.matchLen));
      }
      return Optional.empty();
    }

    public Optional<String> getLastMatch() {
      if (lastSuccessfulReturn != null && lastSuccessfulReturn.matched()) {
        return Optional.of(source.substring(lastSuccessfulReturn.matchPos, lastSuccessfulReturn.matchLen));
      }
      return Optional.empty();
    }

    /**
     * Last rule return.
     * @return rule return
     */
    public RuleReturn<V> getLastReturn() { return lastReturn; }

    public RuleReturn<V> getLastSuccessfulReturn() { return lastSuccessfulReturn; }

    RuleReturn<V> ruleReturn(boolean matched, boolean consumed) {
      RuleReturn<V> ret = lastReturn = new RuleReturn<>(this, matched, consumed);
      if (matched) {
        lastSuccessfulReturn = ret;
      }
      return ret;
    }
  }

  /**
   * Rule return.
   * @param <V> value type
   */
  public static class RuleReturn<V> {
    private final Context<V> ctx;
    private final boolean matched;
    private final boolean consumed;
    private int matchLine;
    private int matchLineOffset;
    private int matchPos = -1;
    private int matchLen = 0;

    private RuleReturn(Context<V> ctx, boolean matched, boolean consumed) {
      this.ctx = ctx;
      this.matched = matched;
      this.consumed = consumed;
      if (matched) {
        SourceState prev = ctx.frame.get(0);
        matchPos = prev.bufferPos;
        matchLen = ctx.source.state.bufferPos - matchPos;
        matchLine = ctx.source.state.line;
        matchLineOffset = ctx.source.state.linePos;
      }
      if (consumed) {
        ctx.tossTopFrame();
      } else {
        ctx.resetToLastFrame();
      }
    }

    /**
     * Line of match, first line is 1.
     * @return line
     */
    public int matchLine() { return matchLine; }

    /**
     * Line offset of match, 1st char on line is 0.
     * @return offset
     */
    public int matchLineOffset() { return matchLineOffset; }

    /**
     * Char offset into input where match occurred.
     * @return line offset
     */
    public int matchPos() { return matchPos; }

    /**
     * Length of match.
     * @return match len
     */
    public int matchLen() { return matchLen; }

    /**
     * Did we match.
     * @return true if matched
     */
    public boolean matched() { return matched; }

    /**
     * Did the rule consume chars.
     * @return true if consumed
     */
    public boolean consumed() { return consumed; }

    /**
     * Context. Only valid for current match, or last match of a parsing run.
     * @return context.
     */
    public Context<V> ctx() { return ctx; }

    @Override
    public String toString() {
      return String.format("RuleReturn match=%s(%s) @ %d for %d (line %d, nextPos=%d)", matched, consumed, matchPos, matchLen, matchLine, matchLineOffset);
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

  private String lineSep = System.lineSeparator();

  public PegLegParser() {
  }

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
      } else if (thing instanceof Runnable) {
        this.exec = exec((Runnable) thing);
      } else {
        throw new RuntimeException("Expected String/char/PegLegRule/Exec/Runnable; found: " + thing);
      }
    }

    public Exec asExec() { return this.exec; }

    boolean isRule() { return rule != null; }

    PegLegRule<V> asRule() { return rule; }
  }

  private RuleReturn<V> eofRule() {
    get().pushFrame();
    return get().ruleReturn(source.atEnd(), false);
  }

  private RuleReturn<V> eolRule() {
    return ch('\n').rule();
  }

  private RuleReturn<V> wsRule() {
    get().pushFrame();
    consumeWS();
    return get().ruleReturn(true, true);
  }

  private void consumeWS() {
    for (; ; get().nextChar()) {
      if (!get().peekOneOf(whiteSpace)) {
        return;
      }
    }
  }

  private static boolean isCharMatch(boolean iCase, char ch1, char ch2) {
    return (iCase) ? Character.toUpperCase(ch1) == Character.toUpperCase(ch2) : (ch1 == ch2);
  }

  private RuleReturn<V> innerTest(PegLegRule<V> rule) {
    SingleNode<V> values = get().saveValues();
    get().pushFrame();
    int cnt = get().frameDepth();
    RuleReturn<V> ret = rule.rule();
    get().rollback(cnt, values);
    return ret;
  }

  @SuppressWarnings("unchecked")
  private List<Step> asSteps(Object... objs) {
    return stream(objs).map(o -> (o instanceof PegLegParser.Step) ? (Step) o : new Step(o)).collect(toList());
  }

  private RuleReturn<V> innerRangeTimesOf(int min, int max, Object... objs) {
    PegLegRule<V> rule = seqOf(objs);
    SingleNode<V> values = get().saveValues();
    get().pushFrame();
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
    if (matchCount >= min && matchCount <= max) {
      return get().ruleReturn(true, true);
    }
    get().restoreValues(values);
    return get().ruleReturn(false, false);
  }

  /**
   * Set the whitespace characters.
   * @param whiteSpace char string containing whitespace chars.
   */
  public void setWhiteSpace(String whiteSpace) {
    this.whiteSpace = whiteSpace;
  }

  public void setLineSeperator(String sep) {
    this.lineSep = sep;
  }

  /**
   * Get the whitespace characters.
   * @return current whitespace characters
   */
  public String getWhiteSpace() { return whiteSpace; }

  /**
   * Reset parser for given input.
   * @param input string input
   * @return this parser.
   */
  public PegLegParser<V> using(CharSequence input) {
    this.source = new Source(input, lineSep);
    this.context = new Context<>(source);
    context.pushFrame();
    return this;
  }

  /**
   * Get context object at the moment.
   * @return context object
   */
  public Context<V> get() { return context; }

  PegLegException error(String message) {
    return new PegLegException("[" + source.getState().line + ":" + source.getState().linePos + "] " + message);
  }

  /**
   * Generate a terminal rule for a specific character.
   * @param ch char
   * @return rule
   */
  public CharTerminal<V> ch(char ch) { return str(Character.toString(ch)); }

  /**
   * Generate a terminal rule for a range of characters.
   * @param from lower bound
   * @param to upper bound.
   * @return rule
   */
  public CharTerminal<V> charRange(char from, char to) {
    if ((int) from > (int) to) {
      throw error("Char range is illegal: [" + from + " to " + to + "]");
    }
    return new CharTerminal<>((ignoreCase) -> {
      get().pushFrame();
      char ch = (char) get().nextChar();
      for (int i = from; i <= (int) to; i++) {
        if (isCharMatch(ignoreCase, ch, (char) i)) {
          return get().ruleReturn(true, true);
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
    return new CharTerminal<>((ignoreCase) -> {
      get().pushFrame();
      for (int i = 0; i < string.length(); i++) {
        if (!isCharMatch(ignoreCase, string.charAt(i), (char) get().nextChar())) {
          return get().ruleReturn(false, false);
        }
      }
      return get().ruleReturn(true, true);
    });
  }

  /**
   * Generates a rule for the char sequence, allowing any amount of preceding/following whitespace.
   * @param string char sequence
   * @return rule
   */
  public CharTerminal<V> ws(CharSequence string) {
    return new CharTerminal<>((ignoreCase) -> {
      get().pushFrame();
      consumeWS();
      for (int i = 0; i < string.length(); i++) {
        if (!isCharMatch(ignoreCase, string.charAt(i), (char) get().nextChar())) {
          return get().ruleReturn(false, false);
        }
      }
      consumeWS();
      return get().ruleReturn(true, true);
    });
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
      get().pushFrame();
      char targ = (char) get().nextChar();
      for (int i = 0; i < string.length(); i++) {
        if (isCharMatch(ignoreCase, string.charAt(i), targ)) {
          return get().ruleReturn(true, true);
        }
      }
      return get().ruleReturn(false, false);
    });
  }

  /**
   * Rule that matches any character not contained in the char sequence specified.
   * @param string char sequence not to match
   * @return rule
   */
  public CharTerminal<V> noneOf(CharSequence string) {
    return new CharTerminal<>((ignoreCase) -> {
      get().pushFrame();
      char targ = (char) get().nextChar();
      for (int i = 0; i < string.length(); i++) {
        if (isCharMatch(ignoreCase, string.charAt(i), targ)) {
          return get().ruleReturn(false, false);
        }
      }
      return get().ruleReturn(true, true);
    });
  }

  /**
   * Rule that matches end of input
   * @return rule
   */
  public PegLegRule<V> eof() { return this::eofRule; }

  /**
   * Rule that matches end of line, possibly multichar end of line.
   * @return rule
   */
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
        get().pushFrame();
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
   * Core PEG rule "Choice", here called firstOf. Tries each sub rule in turn, return whe one matches.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule firstOf(Object... objs) {
    List<Step> steps = asSteps(objs);
    return new ParentRule(() -> {
      get().pushFrame();
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
    });
  }

  /**
   * Core PEG Rule, "Test" returns true if sequence of rules matches, but does not consume input.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule testOf(Object... objs) {
    PegLegRule<V> rule = seqOf(objs);
    return new ParentRule(() -> get().ruleReturn(innerTest(rule).matched(), false));
  }

  /**
   * Core PEG rule: "TestNot". Matches in set of sub rules does not match, does not consume input.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule testNotOf(Object... objs) {
    PegLegRule<V> rule = seqOf(objs);
    return new ParentRule(() -> get().ruleReturn(!innerTest(rule).matched(), false));
  }

  /**
   * Rule with matches only if matches set of rules between min and max times. Greedy, but stops at max.
   * @param min min times to match
   * @param max max times to match
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule timesOf(int min, int max, Object... objs) {
    if (min < 0 || max < 0 || max < min) {
      throw error("illegal min/max for timesof [" + min + "/" + max + "]");
    }
    return new ParentRule(() -> innerRangeTimesOf(min, max, objs));
  }

  /**
   * Rule with matches only if matches set of rules exactly many times.
   * @param many times to match
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule timesOf(int many, Object... objs) {
    if (many <= 0) {
      throw error("illegal many  timesOf [" + many + "]");
    }
    return new ParentRule(() -> innerRangeTimesOf(many, many, objs));
  }

  /**
   * Core PEG rule "ZeroOrMore", matches the given set of rules, greedily, zero or more times.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule zeroPlusOf(Object... objs) {
    return new ParentRule(() -> innerRangeTimesOf(0, Integer.MAX_VALUE, objs));
  }

  /**
   * Core PEG rule "OneOrMore", matches the given set of rules, greedily, one or more times.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule onePlusOf(Object... objs) {
    return new ParentRule(() -> innerRangeTimesOf(1, Integer.MAX_VALUE, objs));
  }

  /**
   * Core PEG rule "Optional", matches the given set of rules, greedily, zero or one times.
   * @param objs rules/string literals/char literals/execs
   * @return rule
   */
  public ParentRule optOf(Object... objs) {
    return new ParentRule(() -> innerRangeTimesOf(0, 1, objs));
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
  public Exec exec(Runnable del) {
    return () -> {
      del.run();
      return true;
    };
  }

  /**
   * Really tiny variant.
   * @param del delegate
   * @return Exec
   */
  public Exec e(Runnable del) { return exec(del); }

  /**
   * Rule with matches any character.
   * @return rule
   */
  public PegLegRule<V> anyChar() {
    return () -> {
      get().pushFrame();
      get().nextChar();
      return get().ruleReturn(true, true);
    };
  }

  /**
   * Always fails to match, consumes nothing
   * @return rule
   */
  public PegLegRule<V> nothing() {
    return () -> {
      get().pushFrame();
      return get().ruleReturn(false, false);
    };
  }

  /**
   * Returns true, matches nothing, consumes nothing.
   * @return rule
   */
  public PegLegRule<V> empty() {
    return () -> {
      get().pushFrame();
      return get().ruleReturn(true, true);
    };
  }

  /**
   * Value stack.
   * @return value stack
   */
  public Values<V> values() { return get().values(); }

  /**
   * The last rule's matched literal. Shorthand for the Context method.
   * @return last match literal
   */
  public Optional<String> match() { return get().match(); }

  /**
   * Parsing entry point. Does not disturb the underlying source,
   * so can be called repeatedly.
   * @param rule rule to start with
   * @return rule return value
   */
  public RuleReturn<V> parse(PegLegRule<V> rule) {
    this.context = new Context<>(source);
    context.pushFrame();
    return rule.rule();
  }
}
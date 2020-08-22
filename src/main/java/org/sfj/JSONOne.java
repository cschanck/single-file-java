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

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <p>Single class containing classes for representing JSON objects
 * programmatically. Includes pretty print and parser. NOT like the world
 * needs another JSON package. But... 1) it was an interesting exercise
 * 2) perhaps someone needs something to adapt from, etc.
 * <p>Very vanilla. Null, Boolean, String, Number, Array and Map objects.
 * <p>Parser (well, the scanner actually) is inefficient for large objects;
 * it expects a single String object for input. So there is that. Would be
 * not too tough to remedy, but you'd need pushback/lookahead etc.
 * <p>Should handle backslash escaping properly, \uabcd unicode, etc.
 * <p>Also, in this day and age of awesome parser frameworks, it was
 * really fun to write a parser by hand. I used: http://www.craftinginterpreters.com/
 * as a refresher for how to do old school scanning/parsing. I swear I have an
 * actual book around somewhere, but I could not find it, and I did not feel
 * like digging through Knuth this time.
 * @author cschanck
 */
public class JSONOne {

  /**
   * JSON value types
   */
  public enum Type {
    NUMBER,
    STRING,
    BOOLEAN,
    MAP,
    ARRAY,
    NULL
  }

  /**
   * A JSON object. Asking for the value as the worng type results in a
   * ClassCastException.
   */
  public interface JObject extends Serializable {
    default JArray arrayValue() {
      throw new ClassCastException();
    }

    default boolean boolValue() {
      throw new ClassCastException();
    }

    JSONOne.Type getType();

    default JMap mapValue() {
      throw new ClassCastException();
    }

    default boolean nullValue() {
      return false;
    }

    default Number numberValue() {
      throw new ClassCastException();
    }

    Object pojoValue();

    default String stringValue() {
      throw new ClassCastException();
    }

    void print(Writer w, int indent, boolean compact) throws IOException;

    default String print(boolean compact) throws IOException { return print(0, compact); }

    default String print() throws IOException { return print(0, true); }

    default String print(int indent, boolean compact) throws IOException {
      StringWriter sw = new StringWriter();
      print(sw, indent, compact);
      return sw.toString();
    }
  }

  private static abstract class AbstractJSONObject implements JObject {
    private Type type;
    private Object obj;

    public AbstractJSONObject() {
    }

    public AbstractJSONObject(Type type, Object obj) {
      this.type = type;
      this.obj = obj;
    }

    @Override
    public Type getType() {
      return type;
    }

    @Override
    public Object pojoValue() {
      return obj;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      AbstractJSONObject object = (AbstractJSONObject) o;
      return Objects.equals(obj, object.obj) && type == object.type;
    }

    @Override
    public int hashCode() {
      return Objects.hash(obj, type);
    }

    @Override
    public String toString() {
      return obj.toString();
    }

    static String indent(int indention) {
      char[] c = new char[indention * 2];
      Arrays.fill(c, ' ');
      return new String(c);
    }

    public void print(Writer w, int indent, boolean compact) throws IOException {
      w.append(obj.toString());
    }

  }

  /**
   * Number object. Represents a Number in JSON. Underlying Value
   * POJO wil be up converted to either Long or Double, always.
   */
  public static class JNumber extends AbstractJSONObject {
    private boolean isFixed;

    public JNumber() {
    }

    public JNumber(Number obj) {
      super(Type.NUMBER, obj instanceof Float ? obj.doubleValue() : obj instanceof Double ? obj : obj.longValue());
      this.isFixed = !(obj instanceof Double);
    }

    public boolean isFixed() {
      return isFixed;
    }

    @Override
    public Number numberValue() {
      return (Number) pojoValue();
    }
  }

  /**
   * Holds a string value.
   */
  public static class JString extends AbstractJSONObject {
    public JString() {
    }

    public JString(CharSequence obj) {
      super(Type.STRING, obj.toString());
      Objects.requireNonNull(obj);
    }

    @Override
    public String stringValue() {
      return (String) pojoValue();
    }

    @Override
    public void print(Writer w, int indent, boolean compact) throws IOException {
      w.append('"');
      w.append(escapeString(stringValue()));
      w.append('"');
    }
  }

  /**
   * Boolean value
   */
  public static class JBoolean extends AbstractJSONObject {
    public JBoolean() {
    }

    public JBoolean(Boolean obj) {
      super(Type.BOOLEAN, obj);
    }

    @Override
    public boolean boolValue() {
      return (Boolean) pojoValue();
    }

    @Override
    public void print(Writer w, int indent, boolean compact) throws IOException {
      w.append((boolValue() ? "true" : "false"));
    }
  }

  /**
   * Null value.
   */
  public static class JNull extends AbstractJSONObject {
    public JNull() {
      super(Type.NULL, null);
    }

    @Override
    public boolean nullValue() {
      return true;
    }

    @Override
    public void print(Writer w, int indent, boolean compact) throws IOException {
      w.append("null");
    }

    @Override
    public String toString() {
      return "null";
    }
  }

  /**
   * JSON Array class. List of {@link JSONOne.JObject}s.
   */
  public static class JArray extends AbstractList<JObject> implements JObject {
    private final CopyOnWriteArrayList<JObject> list = new CopyOnWriteArrayList<>();

    public JArray() {
    }

    @Override
    public JObject get(int index) {
      return list.get(index);
    }

    @Override
    public JArray arrayValue() {
      return this;
    }

    @Override
    public Type getType() {
      return Type.ARRAY;
    }

    @Override
    public Object pojoValue() {
      return list;
    }

    @Override
    public int size() {
      return list.size();
    }

    @Override
    public JObject set(int index, JObject element) {
      return list.set(index, element);
    }

    @Override
    public void add(int index, JObject element) {
      list.add(index, element);
    }

    @Override
    public JObject remove(int index) {
      return list.remove(index);
    }

    public JObject setNumber(int index, Number val) {
      return list.set(index, new JNumber(val));
    }

    public void addNumber(int index, Number val) {
      list.add(index, new JNumber(val));
    }

    public void addNumber(Number val) {
      list.add(new JNumber(val));
    }

    public JObject setString(int index, String val) {
      return list.set(index, new JString(val));
    }

    public void addString(int index, String val) {
      list.add(index, new JString(val));
    }

    public void addString(String val) {
      list.add(new JString(val));
    }

    public JObject setBoolean(int index, boolean val) {
      return list.set(index, new JBoolean(val));
    }

    public void addBoolean(int index, boolean val) {
      list.add(index, new JBoolean(val));
    }

    public void addBoolean(boolean val) {
      list.add(new JBoolean(val));
    }

    public JObject setNull(int index) {
      return list.set(index, new JNull());
    }

    public void addNull(int index) {
      list.add(index, new JNull());
    }

    public void addNull() {
      list.add(new JNull());
    }

    @Override
    public void print(Writer w, int indent, boolean compact) throws IOException {
      String ind1 = AbstractJSONObject.indent(indent);
      String ind2 = AbstractJSONObject.indent(indent + 1);
      w.append('[');

      boolean first = true;
      for (JObject obj : this) {
        if (first) {
          first = false;
        } else {
          w.append(',');
        }
        if (!compact) {
          w.append(System.lineSeparator());
          w.append(ind2);
        }
        obj.print(w, indent + 1, compact);
      }
      if (!compact) {
        w.append(System.lineSeparator());
        w.append(ind1);
      }
      w.append(']');
    }
  }

  /**
   * JSON Map value. String to {@link JSONOne.JObject}.
   */
  public static class JMap extends AbstractMap<String, JObject> implements JObject {
    private final ConcurrentHashMap<String, JObject> map = new ConcurrentHashMap<>();

    public JMap() {
    }

    @Override
    public JMap mapValue() {
      return this;
    }

    @Override
    public Set<Entry<String, JObject>> entrySet() {
      return map.entrySet();
    }

    @Override
    public Type getType() {
      return Type.MAP;
    }

    @Override
    public Object pojoValue() {
      return map;
    }

    @Override
    public boolean containsValue(Object value) {
      return map.containsValue(value);
    }

    @Override
    public boolean containsKey(Object key) {
      return map.containsKey(key);
    }

    @Override
    public JObject get(Object key) {
      return map.get(key);
    }

    @Override
    public JObject put(String key, JObject value) {
      return map.put(key, value);
    }

    @Override
    public JObject remove(Object key) {
      return map.remove(key);
    }

    @Override
    public void clear() {
      map.clear();
    }

    @Override
    public Set<String> keySet() {
      return map.keySet();
    }

    private Object getTyped(String key, Type t, Object defVal) {
      JObject p = get(key);
      if (p != null) {
        if (p.getType().equals(t)) {
          switch (t) {
            case MAP:
            case ARRAY:
              return p;
            default:
              return p.pojoValue();
          }
        }
      }
      return defVal;
    }

    public Boolean getBoolean(String key, Boolean defValue) {
      return (Boolean) getTyped(key, Type.BOOLEAN, defValue);
    }

    public Number getNumber(String key, Number defValue) {
      return (Number) getTyped(key, Type.NUMBER, defValue);
    }

    public String getString(String key, String defValue) {
      return (String) getTyped(key, Type.STRING, defValue);
    }

    public JArray getArray(String key, JArray defValue) {
      return (JArray) getTyped(key, Type.ARRAY, defValue);
    }

    public JArray getMap(String key, JMap defValue) {
      return (JArray) getTyped(key, Type.MAP, defValue);
    }

    public boolean isNull(String key, boolean defValue) {
      JObject p = map.get(key);
      if (p.getType().equals(Type.NULL)) {
        return true;
      }
      return defValue;
    }

    public JMap putBoolean(String key, boolean value) {
      map.put(key, new JBoolean(value));
      return this;
    }

    public JMap putNumber(String key, Number val) {
      map.put(key, new JNumber(val));
      return this;
    }

    public JMap putString(String key, String value) {
      map.put(key, new JString(value));
      return this;
    }

    public JMap putNull(String key) {
      map.put(key, new JNull());
      return this;
    }

    public JMap putMap(String key, JMap value) {
      map.put(key, value);
      return this;
    }

    public JMap putArray(String key, JArray value) {
      map.put(key, value);
      return this;
    }

    @Override
    public void print(Writer w, int indent, boolean compact) throws IOException {
      String ind = AbstractJSONObject.indent(indent);
      w.append('{');
      boolean first = true;
      String ind2 = AbstractJSONObject.indent(indent + 1);
      for (Entry<String, JObject> obj : this.entrySet()) {
        if (first) {
          first = false;
        } else {
          w.append(',');
        }
        if (!compact) {
          w.append(System.lineSeparator());
          w.append(ind2);
        }
        w.append('"');
        w.append(obj.getKey());
        w.append('"');
        w.append(compact ? ":" : " : ");
        obj.getValue().print(w, indent + 1, compact);
      }
      if (!compact) {
        w.append(System.lineSeparator());
        w.append(ind);
      }
      w.append('}');
    }

  }

  enum TokenType {
    LEFT_CURLY,
    RIGHT_CURLY,
    LEFT_BRACKET,
    RIGHT_BACKET,
    COMMA,
    NUMBER,
    STRING,
    NULL,
    EOF,
    TRUE,
    FALSE,
    COLON,
  }

  static class Token {
    final TokenType type;
    final String lexeme;
    final Object literal;
    final int line;

    Token(TokenType type, String lexeme, Object literal, int line) {
      this.type = type;
      this.lexeme = lexeme;
      this.literal = literal;
      this.line = line;
    }

    public String toString() {
      return type + " " + lexeme + " " + literal;
    }
  }

  /**
   * Scanner for parsing.
   */
  static class Scanner {

    private final String input;
    private int current = 0;
    private int start = 0;
    private int line = 1;
    private final LinkedList<Token> pushBack = new LinkedList<>();

    Scanner(String input) {
      this.input = input;
    }

    ParseException error(int line, String message) {
      return report(line, "", message);
    }

    ParseException report(int line, String where, String message) {
      return new ParseException("[line " + line + "] Error" + where + ": " + message, line);
    }

    public Token nextOrFail(TokenType target) throws ParseException {
      Token tok = nextToken();
      if (tok.type.equals(target)) {
        return tok;
      }
      throw error(line, "mismatch type; looking for " + target);
    }

    void pushback(Token t) {
      pushBack.addFirst(t);
    }

    char peek() {
      if (isAtEnd()) {
        return '\0';
      }
      return input.charAt(current);
    }

    private boolean isAtEnd() {
      return current >= input.length();
    }

    char advance() {
      if (isAtEnd()) {
        return '\0';
      }
      return input.charAt(current++);
    }

    private Token token(TokenType type) {
      return token(type, null);
    }

    private Token token(TokenType type, Object literal) {
      String text = input.substring(start, current);
      start = current;
      return new Token(type, text, literal, line);
    }

    public Token nextToken() throws ParseException {
      if (!pushBack.isEmpty()) {
        return pushBack.removeFirst();
      }

      while (!isAtEnd()) {
        char ch = advance();
        switch (ch) {
          case '\n':
            line++;
            start++;
            break;
          case ' ':
          case '\r':
          case '\t':
            start++;
            break;
          case ':':
            return token(TokenType.COLON);
          case '{':
            return token(TokenType.LEFT_CURLY);
          case '}':
            return token(TokenType.RIGHT_CURLY);
          case '[':
            return token(TokenType.LEFT_BRACKET);
          case ']':
            return token(TokenType.RIGHT_BACKET);
          case '"':
            return stringToken();
          case '-':
          case '+':
          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            return numberToken(ch);
          case 'n':
            return advanceMatching("ull", false, TokenType.NULL);
          case 't':
            return advanceMatching("rue", false, TokenType.TRUE);
          case 'f':
            return advanceMatching("alse", false, TokenType.FALSE);
          case ',':
            return token(TokenType.COMMA);
        }
      }
      return token(TokenType.EOF);
    }

    private Token numberToken(char ch) {
      StringBuilder ret = new StringBuilder();
      integer(ch, ret);
      String fract = fraction();
      if (fract.length() > 0) {
        ret.append(fract);
        digits(ret);
      }
      exponent(ret);
      return token(TokenType.NUMBER, ret.toString());
    }

    private void exponent(StringBuilder ret) {
      if (Character.toUpperCase(peek()) == 'E') {
        ret.append(advance());
        if (peek() == '-') {
          ret.append(advance());
        } else if (peek() == '+') {
          ret.append(advance());
        }
        digits(ret);
      }
    }

    private String integer(char first, StringBuilder ret) {
      ret.append(first);
      digits(ret);
      return ret.toString();
    }

    private String fraction() {
      if (peek() == '.') {
        advance();
        return ".";
      }
      return "";
    }

    private String digits(StringBuilder ret) {
      for (char p = peek(); Character.isDigit(p); p = peek()) {
        ret.append(advance());
      }
      return ret.toString();
    }

    private Token advanceMatching(String next, boolean caseMatters, TokenType type) throws ParseException {
      if (!caseMatters) {
        next = next.toLowerCase();
      }
      for (int i = 0; i < next.length(); i++) {
        char c = advance();
        if (!caseMatters) {
          c = Character.toLowerCase(c);
        }
        if (next.charAt(i) != c) {
          throw error(line, "expected [" + next + "]");
        }
      }
      return token(type);
    }

    private Token stringToken() {
      StringBuilder lit = new StringBuilder();
      for (char p = advance(); p != '"'; p = advance()) {
        if (p == '\\') {
          escape(lit);
        } else {
          lit.append(p);
        }
      }
      return token(TokenType.STRING, lit.toString());
    }

    private void escape(StringBuilder lit) {
      char p = advance();
      switch (p) {
        case 'u':
          String utf = ((("" + advance()) + advance()) + advance()) + advance();
          int value = Integer.parseInt(utf, 16);
          lit.append(Character.toChars(value));
          break;
        case 'n':
          lit.append('\n');
          break;
        case 'r':
          lit.append('\r');
          break;
        case 'b':
          lit.append('\b');
          break;
        case 't':
          lit.append('\t');
          break;
        case 'f':
          lit.append('\f');
          break;
        default:
          lit.append(p);
          break;
      }
    }
  }

  /**
   * Parser, parses reasonably standard JSON.
   */
  public static class Parser {
    private final Scanner scanner;
    private final NumberFormat nFormat = NumberFormat.getInstance();

    public Parser(String input) {
      scanner = new Scanner(input);
    }

    private ParseException error(Token token, String message) {
      if (token.type == TokenType.EOF) {
        return scanner.report(token.line, " at end", message);
      }
      return scanner.report(token.line, " at '" + token.lexeme + "'", message);
    }

    public JObject singleObject() throws ParseException {
      Token p = scanner.nextToken();
      switch (p.type) {
        case LEFT_BRACKET:
          return array();
        case LEFT_CURLY:
          return map();
        case FALSE:
          return new JBoolean(false);
        case TRUE:
          return new JBoolean(true);
        case NULL:
          return new JNull();
        case STRING:
          return new JString((String) p.literal);
        case NUMBER:
          return new JNumber(nFormat.parse(p.lexeme));
        case EOF:
          return null;
        default:
          throw error(p, "Unexpected token");
      }
    }

    private JMap map() throws ParseException {
      JMap ret = new JMap();
      listOfMapEntries(ret);
      return ret;
    }

    private Token peekToken() throws ParseException {
      Token p = scanner.nextToken();
      if (p != null) {
        scanner.pushback(p);
      }
      return p;
    }

    private void listOfMapEntries(JMap ret) throws ParseException {
      if (peekToken().type.equals(TokenType.RIGHT_CURLY)) {
        scanner.nextToken();
        return;
      }
      for (; ; ) {
        String key = (String) scanner.nextOrFail(TokenType.STRING).literal;
        scanner.nextOrFail(TokenType.COLON);
        JObject obj = singleObject();
        ret.put(key, obj);
        Token next = scanner.nextToken();
        switch (next.type) {
          case COMMA:
            if (peekToken().type.equals(TokenType.RIGHT_CURLY)) {
              scanner.nextToken();
              return;
            }
            break;
          case RIGHT_CURLY:
            return;
          default:
            throw error(next, "expected ',' or '}'");
        }
      }
    }

    private JArray array() throws ParseException {
      JArray ret = new JArray();
      listOfArrayEntries(ret);
      return ret;
    }

    private void listOfArrayEntries(JArray ret) throws ParseException {
      if (peekToken().type.equals(TokenType.RIGHT_BACKET)) {
        scanner.nextToken();
        return;
      }
      for (; ; ) {
        JObject obj = singleObject();
        ret.add(obj);
        Token next = scanner.nextToken();
        switch (next.type) {
          case COMMA:
            if (peekToken().type.equals(TokenType.RIGHT_BACKET)) {
              scanner.nextToken();
              return;
            }
            break;
          case RIGHT_BACKET:
            return;
          default:
            throw error(next, "expected ',' or ']'");
        }
      }
    }
  }

  public static CharSequence unescapeString(CharSequence seq) {
    StringBuilder w = new StringBuilder();
    final int len = seq.length();
    for (int pos = 0; pos < len; pos++) {
      char ch = seq.charAt(pos);
      if (ch == '\\') {
        ch = seq.charAt(++pos);
        switch (ch) {
          case 'u':
            String
              utf =
              Character.toString(seq.charAt(++pos)) + seq.charAt(++pos) + seq.charAt(++pos) + seq.charAt(++pos);
            int value = Integer.parseInt(utf, 16);
            w.append(Character.toChars(value));
            break;
          case 'n':
            w.append('\n');
            break;
          case 'r':
            w.append('\r');
            break;
          case 'b':
            w.append('\b');
            break;
          case 't':
            w.append('\t');
            break;
          case 'f':
            w.append('\f');
            break;
          default:
            w.append(ch);
            break;
        }
      } else {
        w.append(ch);
      }
    }
    return w;
  }

  public static CharSequence escapeString(CharSequence seq) {
    StringBuilder w = new StringBuilder();
    final int len = seq.length();
    for (int i = 0; i < len; i++) {
      char ch = seq.charAt(i);
      switch (ch) {
        case '"':
          w.append("\\\"");
          break;
        case '\\':
          w.append("\\\\");
          break;
        case '\b':
          w.append("\\b");
          break;
        case '\f':
          w.append("\\f");
          break;
        case '\n':
          w.append("\\n");
          break;
        case '\r':
          w.append("\\r");
          break;
        case '\t':
          w.append("\\t");
          break;
        default:
          if ((ch <= '\u001F') || (ch >= '\u007F' && ch <= '\u009F') || (ch >= '\u2000' && ch <= '\u20FF')) {
            String ss = Integer.toHexString(ch);
            w.append("\\u");
            for (int k = 0; k < 4 - ss.length(); k++) {
              w.append('0');
            }
            w.append(ss.toUpperCase());
          } else {
            w.append(ch);
          }
      }
    }
    return w;
  }
}



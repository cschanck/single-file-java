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

import java.io.Serializable;
import java.text.ParseException;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class OneJSON {

  public enum Type {
    NUMBER,
    STRING,
    BOOLEAN,
    MAP,
    ARRAY,
    NULL
  }

  public interface JSONObject extends Serializable {
    default JSONArray arrayValue() {
      throw new ClassCastException();
    }

    default boolean boolValue() {
      throw new ClassCastException();
    }

    OneJSON.Type getType();

    default JSONMap mapValue() {
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
  }

  public static abstract class AbstractJSONObject implements JSONObject {
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
      boolean ret = Objects.equals(obj, object.obj) && type == object.type;
      return ret;
    }

    @Override
    public int hashCode() {
      return Objects.hash(obj, type);
    }

    @Override
    public String toString() {
      return "JSONObject{" + "type=" + type + ", obj=" + obj + '}';
    }
  }

  public static class NumberObject extends AbstractJSONObject {
    private boolean isFixed;

    public NumberObject() {
    }

    public NumberObject(Number obj) {
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

  public static class StringObject extends AbstractJSONObject {
    public StringObject() {
    }

    public StringObject(String obj) {
      super(Type.STRING, obj);
    }

    @Override
    public String stringValue() {
      return (String) pojoValue();
    }
  }

  public static class BooleanObject extends AbstractJSONObject {
    public BooleanObject() {
    }

    public BooleanObject(Boolean obj) {
      super(Type.BOOLEAN, obj);
    }

    @Override
    public boolean boolValue() {
      return (Boolean) pojoValue();
    }
  }

  public static class NullObject extends AbstractJSONObject {
    public NullObject() {
      super(Type.NULL, null);
    }

    @Override
    public boolean nullValue() {
      return true;
    }
  }

  public static class JSONArray extends AbstractList<JSONObject> implements JSONObject {
    private CopyOnWriteArrayList<JSONObject> list = new CopyOnWriteArrayList<>();

    @Override
    public JSONObject get(int index) {
      return list.get(index);
    }

    @Override
    public JSONArray arrayValue() {
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
    public JSONObject set(int index, JSONObject element) {
      return list.set(index, element);
    }

    @Override
    public void add(int index, JSONObject element) {
      list.add(index, element);
    }

    @Override
    public JSONObject remove(int index) {
      return list.remove(index);
    }

    public JSONObject setNumber(int index, Number val) {
      return list.set(index, new NumberObject(val));
    }

    public void addNumber(int index, Number val) {
      list.add(index, new NumberObject(val));
    }

    public void addNumber(Number val) {
      list.add(new NumberObject(val));
    }

    public JSONObject setString(int index, String val) {
      return list.set(index, new StringObject(val));
    }

    public void addString(int index, String val) {
      list.add(index, new StringObject(val));
    }

    public void addString(String val) {
      list.add(new StringObject(val));
    }

    public JSONObject setBoolean(int index, boolean val) {
      return list.set(index, new BooleanObject(val));
    }

    public void addBoolean(int index, boolean val) {
      list.add(index, new BooleanObject(val));
    }

    public void addBoolean(boolean val) {
      list.add(new BooleanObject(val));
    }

    public JSONObject setNull(int index) {
      return list.set(index, new NullObject());
    }

    public void addNull(int index) {
      list.add(index, new NullObject());
    }

    public void addNull() {
      list.add(new NullObject());
    }
  }

  public static class JSONMap extends AbstractMap<String, JSONObject> implements JSONObject {
    private ConcurrentHashMap<String, JSONObject> map = new ConcurrentHashMap<>();

    public JSONMap() {
    }

    @Override
    public JSONMap mapValue() {
      return this;
    }

    @Override
    public Set<Entry<String, JSONObject>> entrySet() {
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
    public JSONObject get(Object key) {
      return map.get(key);
    }

    @Override
    public JSONObject put(String key, JSONObject value) {
      return map.put(key, value);
    }

    @Override
    public JSONObject remove(Object key) {
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
      JSONObject p = get(key);
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

    public JSONArray getArray(String key, JSONArray defValue) {
      return (JSONArray) getTyped(key, Type.ARRAY, defValue);
    }

    public JSONArray getMap(String key, JSONMap defValue) {
      return (JSONArray) getTyped(key, Type.MAP, defValue);
    }

    public boolean isNull(String key, boolean defValue) {
      JSONObject p = map.get(key);
      if (p.getType().equals(Type.NULL)) {
        return true;
      }
      return defValue;
    }

    public JSONObject putBoolean(String key, boolean value) {
      return map.put(key, new BooleanObject(value));
    }

    public JSONObject putNumber(String key, Number val) {
      return map.put(key, new NumberObject(val));
    }

    public JSONObject putString(String key, String value) {
      return map.put(key, new StringObject(value));
    }

    public JSONObject putNull(String key) {
      return map.put(key, new NullObject());
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

  private static class Scanner {

    private final String input;
    private int current = 0;
    private int start = 0;
    private int line = 0;
    private LinkedList<Token> pushBack = new LinkedList<>();

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

    char peek(int offset) {
      if (isAtEnd(offset)) {
        return '\0';
      }
      return input.charAt(current + offset);
    }

    private boolean isAtEnd(int offset) {
      return current + offset >= input.length();
    }

    char advance() {
      if (isAtEnd(0)) {
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

      for (; !isAtEnd(0); ) {
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
      if (Character.toUpperCase(peek(0)) == 'E') {
        ret.append(advance());
        if (peek(0) == '-') {
          ret.append(advance());
        } else if (peek(0) == '+') {
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
      if (peek(0) == '.') {
        advance();
        return ".";
      }
      return "";
    }

    private String digits(StringBuilder ret) {
      for (char p = peek(0); Character.isDigit(p); p = peek(0)) {
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
      for (char p = advance(); p != '"'; p = advance()) {
        switch (p) {
          case '\\':
            advance();
          default:
            break;
        }
      }
      return token(TokenType.STRING, input.substring(start + 1, current - 1));
    }
  }

  static class Parser {
    private final Scanner scanner;

    public Parser(String input) {
      scanner = new Scanner(input);
    }

    private ParseException error(Token token, String message) {
      if (token.type == TokenType.EOF) {
        return scanner.report(token.line, " at end", message);
      }
      return scanner.report(token.line, " at '" + token.lexeme + "'", message);
    }

    public JSONObject singleObject() throws ParseException {
      for (Token p = scanner.nextToken(); !p.type.equals(TokenType.EOF); p = scanner.nextToken()) {
        switch (p.type) {
          case LEFT_BRACKET:
            return array();
          case LEFT_CURLY:
            return map();
          case FALSE:
            return new BooleanObject(false);
          case TRUE:
            return new BooleanObject(true);
          case NULL:
            return new NullObject();
          case STRING:
            return new StringObject((String) p.literal);
          case NUMBER: {
            try {
              return new NumberObject(Long.parseLong(p.lexeme));
            } catch (Throwable e) {
              return new NumberObject(Double.parseDouble(p.lexeme));
            }
          }
          case EOF:
            return null;
          default:
            throw error(p, "Unexpected token");
        }
      }
      return null;
    }

    private JSONMap map() throws ParseException {
      JSONMap ret = new JSONMap();
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

    private void listOfMapEntries(JSONMap ret) throws ParseException {
      if (peekToken().type.equals(TokenType.RIGHT_CURLY)) {
        return;
      }
      for (; ; ) {
        String key = (String) scanner.nextOrFail(TokenType.STRING).literal;
        scanner.nextOrFail(TokenType.COLON);
        JSONObject obj = singleObject();
        ret.put(key, obj);
        Token next = scanner.nextToken();
        switch (next.type) {
          case COMMA:
            if (peekToken().type.equals(TokenType.RIGHT_BACKET)) {
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

    private JSONArray array() throws ParseException {
      JSONArray ret = new JSONArray();
      listOfArrayEntries(ret);
      return ret;
    }

    private void listOfArrayEntries(JSONArray ret) throws ParseException {
      if (peekToken().type.equals(TokenType.RIGHT_CURLY)) {
        return;
      }
      for (; ; ) {
        JSONObject obj = singleObject();
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
}

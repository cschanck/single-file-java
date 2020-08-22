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
package org.sfj.exemplars;

import org.sfj.JSONOne;
import org.sfj.PegLegParser;

import java.text.NumberFormat;
import java.text.ParseException;

/**
 * JSON parser, leaves a single object on top of the stack, using
 * the JSONOne package.
 */
public class JsonPegParser extends PegLegParser<JSONOne.JObject> {
  public PegLegRule<JSONOne.JObject> json() {
    return named("json", seqOf(firstOf(jsonObject(), jsonArray())));
  }

  PegLegRule<JSONOne.JObject> jsonObject() {
    Ref<JSONOne.JMap> map = new Ref<>(JSONOne.JMap::new);
    return named("jsonObject",
      () -> seqOf(ws('{'), optOf(seqOf(pair(), ex(addPair(map)), zeroPlusOf(',', pair(), ex(addPair(map))), optOf(ws(',')))),
        ws('}'), ex(() -> values().push(map.get()))).refs(map).rule());
  }

  private Runnable addPair(Ref<JSONOne.JMap> map) {
    return () -> {
      JSONOne.JObject val = values().pop();
      JSONOne.JString key = (JSONOne.JString) values().pop();
      map.get().put(key.stringValue(), val);
    };
  }

  PegLegRule<JSONOne.JObject> pair() {
    return named("pair", seqOf(jsonString(), ws(':'), value()));
  }

  PegLegRule<JSONOne.JObject> value() {
    return named("value", firstOf(jsonString(), // pushed below
      seqOf(jsonNumber(), ex(this::matchAsNumber)), jsonObject(), // pushed below
      jsonArray(), // pushed below
      seqOf(ws("true"), ex(() -> values().push(new JSONOne.JBoolean(true)))),
      seqOf(ws("false"), ex(() -> values().push(new JSONOne.JBoolean(false)))),
      seqOf(ws("null"), ex(() -> values().push(new JSONOne.JNull())))));
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
    return named("jsonString", seqOf(ws(), "\"", zeroPlusOf(chars()),
      ex(() -> values().push(new JSONOne.JString(JSONOne.unescapeString(get().match().orElse(""))))), "\"", ws()));
  }

  PegLegRule<JSONOne.JObject> jsonNumber() {
    return named("jsonNumber", seqOf(integer(), optOf(frac(), optOf(exp())), ws()));
  }

  public PegLegRule<JSONOne.JObject> jsonArray() {
    Ref<JSONOne.JArray> arr = new Ref<>(JSONOne.JArray::new);
    return named("jsonArray", () -> seqOf(ws('['),
      optOf(value(), ex(() -> arr.get().add(values().pop())), zeroPlusOf(ws(','), value(), ex(() -> arr.get().add(values().pop()))),
        optOf(ws(','))), ws(']'), ex(() -> values().push(arr.get()))).refs(arr).rule());
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

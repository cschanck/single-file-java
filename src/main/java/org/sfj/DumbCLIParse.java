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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>This is in honor of a past boss of mine, a crackerjack dev, who struggled
 * (one time! but sadly on a projected screen... :-) ) with regular expressions.
 * Here we have a tiny but useful command line arg parser based entirely on regular
 * expressions. This one is for you, Louis.
 *
 * <p>The way you use this is by first call {@link #args} with the main() args.
 * Just gives you a list back, but feels nice for examples.
 *
 * <p>There are two ways to use it. You can use the per arg parsing methods,
 * {@link #scanForArgWithParm(List, String)} or {@link #scanForFlag(List, String, boolean)}
 * passing the arg list in. Each of the scan methods will scan the entire list,
 * removing the <b>first</b> match and returning appropriately.
 *
 * <p>When you are done scanning for args, the remainder args are whatever is in
 * the list.
 *
 * <p>Alternatively, you can pass the list to {link {@link #scanForAllFlags(List)} and
 * then {@link #scanForAllParamArgs(List)} in turn, and each will return a list of
 * matching flag or string args.
 *
 * <p>Personally, I find the incremental ones easier and more natural to code
 * against, but some may prefer the "all" variants. See unit test for sample usage.
 *
 * <p>No one will mistake it for JCommander or any of the other full up CLI parsing
 * packages, but very sufficient for rapid prototyping.
 *
 * @author cschanck
 **/
public final class DumbCLIParse {
  private DumbCLIParse() {
  }

  /**
   * Just turn main() args into a mutable list. Nothing exotic.
   *
   * @param args array of args
   * @return args as mutable list.
   */
  public static List<String> args(String[] args) {
    return new LinkedList<>(Arrays.asList(args));
  }

  private static String argWithParamPattern(String argname) {
    return "--?" + argname + "=(.+)$";
  }

  private static String argWithParamPattern() {
    return "--?([^=]+)=(.+)$";
  }

  private static String flagPattern(String argname) {
    return "--?((not)-(-)?)?" + argname + "$";
  }

  private static String flagPattern() {
    return "--?((not)-(-)?)?([^=]+)$";
  }

  private static Matcher match(Pattern pattern, String s) {
    Matcher match = pattern.matcher(s.trim());
    if (match.matches()) {
      return match;
    }
    return null;
  }

  /**
   * Scan for the first argument with a param name that matches the argname.
   * Scanning for arg "foo" looks for either of "--foo=thing" or "-foo=thing".
   * Returns the arg as a string, further parsing is your problem. Note that if
   * you want a default value, since it returns an optional, you can just do
   * ret.orElse("default value")
   *
   * @param remaining list of remaining args to parse from
   * @param argname name of arg, case sensitive
   * @return Optional string value if it matched.
   */
  public static Optional<String> scanForArgWithParm(List<String> remaining, String argname) {
    Pattern pattern = Pattern.compile(argWithParamPattern(argname));
    return new ArrayList<>(remaining).stream().map(s -> {
      Matcher ret = match(pattern, s);
      if (ret != null) {
        remaining.remove(s);
      }
      return ret;
    }).filter(Objects::nonNull).limit(1).reduce((first, second) -> second).map((m) -> m.group(1));
  }

  /**
   * Scan for the first argument with a param name that matches the argname.
   * Scanning for arg "foo" looks for any of "--foo", "--not--foo", "--not-foo",
   * "-foo", "-not--foo", "-not-foo".
   *
   * @param remaining list of remaining args to scan through
   * @param argname argname to scan for, case sensitive
   * @param foundVal value to use if it is found
   * @return If any of the variants without "not" are found,
   * the specific foundVal will be returned. If any of the "not" variants are found,
   * or if the flag is not seen, !foundVal will be returned.
   */
  public static boolean scanForFlag(List<String> remaining, String argname, boolean foundVal) {
    // match --not--arg, or single dash versions
    // case sensitive.
    Pattern pattern = Pattern.compile(flagPattern(argname));
    Optional<Matcher> got = new ArrayList<>(remaining).stream().map((s) -> {
      Matcher ret = match(pattern, s);
      if (ret != null) {
        remaining.remove(s);
      }
      return ret;
    }).filter(Objects::nonNull).limit(1).reduce((first, second) -> second);

    if (got.isPresent()) {
      String not = got.get().group(2);
      if (not == null) {
        return foundVal;
      } else {
        return !foundVal;
      }
    }
    return !foundVal;
  }

  static class FlagArg {
    private final String arg;
    private final boolean isNot;

    FlagArg(String arg, boolean isNot) {
      this.arg = arg;
      this.isNot = isNot;
    }

    public String getArg() {
      return arg;
    }

    public boolean isNot() {
      return isNot;
    }

    @Override
    public String toString() {
      return "FlagArg{" + (isNot ? "not " : "") + arg + '}';
    }
  }

  static class StringArg {
    private final String arg;
    private final String param;

    StringArg(String arg, String param) {
      this.arg = arg;
      this.param = param;
    }

    public String getArg() {
      return arg;
    }

    public String getParam() {
      return param;
    }

    @Override
    public String toString() {
      return "StringArg{" + arg + '=' + param + '}';
    }
  }

  public static List<FlagArg> scanForAllFlags(List<String> all) {
    LinkedList<FlagArg> found = new LinkedList<>();
    Pattern patt = Pattern.compile(flagPattern());
    new ArrayList<>(all).forEach((s) -> {
      Matcher m = match(patt, s.trim());
      if (m != null) {
        String not = m.group(2);
        String arg = m.group(4);
        found.add(new FlagArg(arg, not != null));
        all.remove(s);
      }
    });
    return found;
  }

  public static List<StringArg> scanForAllParamArgs(List<String> all) {
    LinkedList<StringArg> found = new LinkedList<>();
    Pattern patt = Pattern.compile(argWithParamPattern());
    new ArrayList<>(all).forEach((s) -> {
      Matcher m = match(patt, s.trim());
      if (m != null) {
        String arg = m.group(1);
        String parm = m.group(2);
        found.add(new StringArg(arg, parm));
        all.remove(s);
      }
    });
    return found;
  }
}

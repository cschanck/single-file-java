package org.sfj;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.sfj.DumbCLIParse.FlagArg;
import static org.sfj.DumbCLIParse.args;
import static org.sfj.DumbCLIParse.scanForAllFlags;
import static org.sfj.DumbCLIParse.scanForAllParamArgs;
import static org.sfj.DumbCLIParse.scanForArgWithParm;
import static org.sfj.DumbCLIParse.scanForFlag;

public class DumbCLIParseTest {
  @Test
  public void testWithArg() {
    String[] arr = new String[] { "--foo=10", "left", "over", "-foo=me", "-boy=pinochio", "-happy=gilmore", };
    List<String> args = args(arr);
    Optional<String> foo1 = scanForArgWithParm(args, "foo");
    Optional<String> foo2 = scanForArgWithParm(args, "foo");
    Optional<String> foo3 = scanForArgWithParm(args, "foo");
    Optional<String> boy = scanForArgWithParm(args, "boy");
    Optional<String> happy = scanForArgWithParm(args, "happy");
    Optional<String> nope = scanForArgWithParm(args, "nope");

    assertThat(foo1.isPresent(), is(true));
    assertThat(foo1.get(), is("10"));

    assertThat(foo2.isPresent(), is(true));
    assertThat(foo2.get(), is("me"));

    assertThat(foo3.isPresent(), is(false));

    assertThat(boy.isPresent(), is(true));
    assertThat(boy.get(), is("pinochio"));

    assertThat(happy.isPresent(), is(true));
    assertThat(happy.get(), is("gilmore"));

    assertThat(nope.isPresent(), is(false));

    assertThat(args.size(), is(2));
    assertThat(args.contains("left"), is(true));
    assertThat(args.contains("over"), is(true));
  }

  @Test
  public void testFlags() {
    String[] arr = new String[] { "-left", "-not-over", "-foo", "extra" };
    List<String> args = args(arr);
    boolean over1 = scanForFlag(args, "over", true);
    boolean foo1 = scanForFlag(args, "foo", true);
    boolean left1 = scanForFlag(args, "left", true);
    boolean blrg1 = scanForFlag(args, "blrg", true);
    boolean blrg2 = scanForFlag(args, "blrg", false);

    assertThat(foo1, is(true));
    assertThat(left1, is(true));
    assertThat(over1, is(false));
    assertThat(blrg1, is(false));
    assertThat(blrg2, is(true));

  }

  @Test
  public void testAllFlags() {
    String[] arr = new String[] { "-left", "-not-over", "-foo", "--bar", "--not-bar", "extra" };
    List<String> args = args(arr);
    List<FlagArg> flags = scanForAllFlags(args);

    assertThat(flags.size(), is(5));
    assertThat(flags.stream()
                 .filter(fa -> fa.getArg().equals("left") && fa.isNot() == false)
                 .count(), is(1L));
    assertThat(flags.stream().filter(fa -> fa.getArg().equals("over") && fa.isNot() == true).count(), is(1L));
    assertThat(flags.stream().filter(fa -> fa.getArg().equals("foo") && fa.isNot() == false).count(), is(1L));
    assertThat(flags.stream().filter(fa -> fa.getArg().equals("bar") && fa.isNot() == false).count(), is(1L));
    assertThat(flags.stream().filter(fa -> fa.getArg().equals("bar") && fa.isNot() == true).count(), is(1L));
    assertThat(args.size(), is(1));
  }

  @Test
  public void testAllParms() {
    String[] arr = new String[] { "--foo=10", "left", "over", "-foo=me", "-boy=pinochio", "-happy=gilmore", };
    List<String> args = args(arr);
    List<DumbCLIParse.StringArg> parms = scanForAllParamArgs(args);

    assertThat(parms.size(), is(4));
    assertThat(parms.stream()
                 .filter(fa -> fa.getArg().equals("foo") && fa.getParam().equals("10"))
                 .count(), is(1L));
    assertThat(parms.stream()
                 .filter(fa -> fa.getArg().equals("foo") && fa.getParam().equals("me"))
                 .count(), is(1L));
    assertThat(parms.stream()
                 .filter(fa -> fa.getArg().equals("boy") && fa.getParam().equals("pinochio"))
                 .count(), is(1L));
    assertThat(parms.stream()
                 .filter(fa -> fa.getArg().equals("happy") && fa.getParam().equals("gilmore"))
                 .count(), is(1L));
    assertThat(args.size(), is(2));
  }

  @Test
  public void exampleIncremental() {
    String[]
      arr =
      new String[] { "--name=tester",
        "left",
        "over",
        "-threads=10",
        "-verbose",
        "--not-persistent", };

    List<String> args = args(arr);
    boolean isVerbose = scanForFlag(args, "verbose", true);
    boolean isPersistent = scanForFlag(args, "persistent", true);
    boolean isNotThere = scanForFlag(args, "not-there", true);
    int threads = Integer.parseInt(scanForArgWithParm(args, "threads").orElse("1"));
    Optional<String> name = scanForArgWithParm(args, "name");

    assertThat(isVerbose, is(true));
    assertThat(isPersistent, is(false));
    assertThat(isNotThere, is(false));
    assertThat(threads, is(10));
    assertThat(name.isPresent(), is(true));
    assertThat(name.get(), is("tester"));

    assertThat(args.get(0), is("left"));
    assertThat(args.get(1), is("over"));
  }


  @Test
  public void exampleFull() {
    String[]
      arr =
      new String[] { "--name=tester",
        "left",
        "over",
        "-threads=10",
        "-verbose",
        "--not-persistent", };

    List<String> args = args(arr);
    List<FlagArg> flags = scanForAllFlags(args);
    List<DumbCLIParse.StringArg> parms = scanForAllParamArgs(args);

    assertThat(flags.size(), is(2));
    assertThat(parms.size(),is(2));

    assertThat(flags.stream().filter(fa -> fa.getArg().equals("verbose") && fa.isNot() == false).count(), is(1L));
    assertThat(flags.stream().filter(fa -> fa.getArg().equals("persistent") && fa.isNot() == true).count(), is(1L));

    assertThat(parms.stream()
                 .filter(fa -> fa.getArg().equals("threads") && fa.getParam().equals("10"))
                 .count(), is(1L));
    assertThat(parms.stream()
                 .filter(fa -> fa.getArg().equals("name") && fa.getParam().equals("tester"))
                 .count(), is(1L));

    assertThat(args.get(0), is("left"));
    assertThat(args.get(1), is("over"));
  }
}
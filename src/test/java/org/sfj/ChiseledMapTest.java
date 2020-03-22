package org.sfj;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.sfj.ChiseledMap.OpenOption.DONT_CARE;
import static org.sfj.ChiseledMap.OpenOption.MUST_BE_NEW;
import static org.sfj.ChiseledMap.OpenOption.MUST_EXIST;

public class ChiseledMapTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void test1KWrites() throws IOException {
    int N = 1000000;
    byte[] b = new byte[1024];
    for (int i = 0; i < b.length; i++) {
      b[i] = (byte) i;
    }
    ChiseledMap<Integer, byte[]> kv = new ChiseledMap<>(tmp.newFile(), DONT_CARE, null, null, null);
    long ns = System.nanoTime();
    for (int i = 0; i < N; i++) {
      kv.ioSet(i, b);
    }
    kv.flush();
    long took = System.nanoTime() - ns;
    long secs = TimeUnit.NANOSECONDS.toSeconds(took);
    long bytes = kv.bytesOnDisk();
    long bytesPerSec = bytes / secs;
    System.out.println(kv.bytesOnDisk() + " --> " + took + "ns @ " + bytesPerSec + " bytes/sec");
    kv.close();
  }

  @Test
  public void testIntsToInts() throws IOException {
    File f = tmp.newFile();
    f.delete();
    ChiseledMap<Integer, Integer> kv = new ChiseledMap<>(f, MUST_BE_NEW, null, null, null);
    for (int i = 0; i < 10; i++) {
      kv.ioSet(i, i * 2);
    }
    assertThat(kv.size(), is(10));
    assertThat(kv.get(11), Matchers.nullValue());
    for (int i = 0; i < 10; i++) {
      assertThat(kv.get(i), is(i * 2));
    }
    kv.close();
    kv = new ChiseledMap<>(f, MUST_EXIST, null, null, null);
    assertThat(kv.size(), is(10));
    assertThat(kv.get(11), Matchers.nullValue());
    for (int i = 0; i < 10; i++) {
      assertThat(kv.get(i), is(i * 2));
    }

    kv.close();
  }

  @Test
  public void testThreadSafe15SecondsX4() throws IOException, InterruptedException {
    int N = Integer.highestOneBit(4);
    int MSECS = 15 * 1000;
    int MANY = 100000;

    ConcurrentHashMap<Integer, String> shadow = new ConcurrentHashMap<>();
    ChiseledMap<Integer, String> kv = new ChiseledMap<>(tmp.newFile(), DONT_CARE, null, null, null);

    Object[] locks = new Object[N * 16];
    for (int i = 0; i < locks.length; i++) {
      locks[i] = new Object();
    }

    AtomicBoolean stop = new AtomicBoolean(false);
    AtomicLong ops = new AtomicLong(0);
    LinkedList<Thread> threads = new LinkedList<>();
    for (int i = 0; i < N; i++) {
      Thread t = new Thread(() -> {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long cnt = 0;
        while (!stop.get()) {
          cnt++;
          int choice = r.nextInt(100);
          int key = r.nextInt(MANY);
          if (choice < 40) {
            // read
            String p1;
            String p2;
            synchronized (locks[key & locks.length - 1]) {
              p1 = kv.get(key);
              p2 = shadow.get(key);
            }
            assertThat(p1, is(p2));
          } else if (choice < 60) {
            // remove
            synchronized (locks[key & locks.length - 1]) {
              shadow.remove(key);
              kv.remove(key);
            }
          } else if (choice < 80) {
            // mutate
            String p = kv.get(key);
            if (p != null) {
              boolean isUpper = Character.isUpperCase(p.charAt(0));
              p = isUpper ? p.toLowerCase() : p.toUpperCase();
              synchronized (locks[key & locks.length - 1]) {
                shadow.put(key, p);
                kv.set(key, p);
              }
            }
          } else {
            // add
            String tmpStr = "anonymousbosch-" + key;
            synchronized (locks[key & locks.length - 1]) {
              shadow.put(key, tmpStr);
              kv.set(key, tmpStr);
            }
          }
        }
        ops.addAndGet(cnt);
      });
      t.setName("test-" + i);
      t.setDaemon(true);
      threads.add(t);
    }

    threads.forEach(t -> t.start());

    Thread.sleep(MSECS);
    stop.set(true);
    threads.forEach(t -> {
      try {
        t.join();
      } catch (InterruptedException e) {
      }
    });
    assertThat(shadow.size(), is(kv.size()));
    shadow.forEach((k, v) -> {
      assertThat(kv.get(k), is(v));
    });

    //    System.out.println(kv.getFile() +
    //                       " Elements: " +
    //                       kv.size() +
    //                       " Bytes: " +
    //                       kv.bytesOnDisk() +
    //                       " EntriesOnDisk: " +
    //                       kv.entriesOnDisk() +
    //                       " Ops: " +
    //                       ops.get());

    File two = tmp.newFile();
    two.delete();
    ChiseledMap<Integer, String> newKV = kv.snapshot(two);
    assertThat(shadow.size(), is(newKV.size()));
    shadow.forEach((k, v) -> {
      assertThat(newKV.get(k), is(v));
    });

    assertThat(newKV.bytesOnDisk(), Matchers.lessThan(kv.bytesOnDisk()));

    //    System.out.println(newKV.getFile() +
    //                       " Elements: " +
    //                       newKV.size() +
    //                       " Bytes: " +
    //                       newKV.bytesOnDisk() +
    //                       " EntriesOnDisk: " +
    //                       newKV.entriesOnDisk());

    newKV.close();
    kv.close();
  }
}
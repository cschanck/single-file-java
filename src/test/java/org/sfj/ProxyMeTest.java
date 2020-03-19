package org.sfj;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ProxyMeTest {
  @Test
  public void testSimple() throws InterruptedException {
    AtomicReference<ProxyMe.Invocation> invoke = new AtomicReference<>(null);

    ProxyMe.Client<Function<Integer, Integer>>
      client =
      new ProxyMe.Client<>(Executors.newScheduledThreadPool(1), Function.class, (iv) -> {
        invoke.set(iv);
      }, 1, TimeUnit.SECONDS);

    Function<Integer, Integer> proxy = client.clientProxy(Thread.currentThread().getContextClassLoader());

    ProxyMe.Server<Function<Integer, Integer>> server = new ProxyMe.Server<>(Function.class, (i) -> i + 1);

    new Thread(() -> {
      Integer plus1 = proxy.apply(10);
      assertThat(plus1, is(11));
    }).start();

    while (invoke.get() == null) {
      ;
    }

    ProxyMe.InvocationReturn<Integer> p = server.invoke(invoke.get());

    client.complete(p);
  }

  @Test
  public void testMap() {

    ProxyMe.Server<ConcurrentHashMap<Integer, Integer>>
      server =
      new ProxyMe.Server<>(Map.class, new ConcurrentHashMap());

    LinkedBlockingQueue<ProxyMe.Invocation> invocations = new LinkedBlockingQueue<>();

    ProxyMe.Client<Map<Integer, Integer>>
      client =
      new ProxyMe.Client<>(Executors.newScheduledThreadPool(1), Map.class, (i) -> {
        invocations.offer(i);
      }, 1, TimeUnit.SECONDS);

    Map<Integer, Integer> mapProxy = client.clientProxy(Thread.currentThread().getContextClassLoader());

    AtomicBoolean live = new AtomicBoolean(true);
    Executors.newCachedThreadPool().submit(() -> {
      for (; live.get(); ) {
        try {
          ProxyMe.Invocation p = invocations.poll(1, TimeUnit.SECONDS);
          if (p != null) {
            client.complete(server.invoke(p));
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    });

    for (int i = 0; i < 10; i++) {
      mapProxy.put(i, i * 10);
    }
    for (int i = 0; i < 10; i++) {
      assertThat(mapProxy.get(i), is(10 * i));
    }
    assertThat(mapProxy.size(), is(10));

    live.set(false);
  }

  @Test
  public void testTimeout() {
    ProxyMe.Client<Map<Integer, Integer>>
      client =
      new ProxyMe.Client<>(Executors.newScheduledThreadPool(1), Map.class, (i) -> { }, 10, TimeUnit.MILLISECONDS);

    Map<Integer, Integer> mapProxy = client.clientProxy(Thread.currentThread().getContextClassLoader());

    try {
      Integer p = mapProxy.get("foo");
      Assert.fail();
    } catch (Exception e) {
      assertThat(e.getCause(), Matchers.instanceOf(TimeoutException.class));
    }
  }

  @Test
  public void testException() {
    AtomicReference<ProxyMe.Client<Closeable>> cRef = new AtomicReference<>(null);

    ProxyMe.Client<Closeable>
      client =
      new ProxyMe.Client<>(Executors.newScheduledThreadPool(1), Closeable.class, (i) -> {
        cRef.get().complete(new ProxyMe.InvocationReturn<>(i.getIId(), null, new IOException()));
      }, 10, TimeUnit.MILLISECONDS);

    cRef.set(client);

    Closeable closeProxy = client.clientProxy(Thread.currentThread().getContextClassLoader());

    try {
      closeProxy.close();
      Assert.fail();
    } catch (IOException e) {
    }
  }
}
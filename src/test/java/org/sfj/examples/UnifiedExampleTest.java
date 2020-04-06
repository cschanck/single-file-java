package org.sfj.examples;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sfj.ChiseledMap;
import org.sfj.DumbCLIParse;
import org.sfj.PojoClientServer;
import org.sfj.ProxyMe;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests 4 of the files together, for fun and profit. CLI arg parsing,
 * ChiseledMap for storage, ProxyMe to proxy ChiseledMap's map interface,
 * over the PojoClientServer network pipe.
 */
public class UnifiedExampleTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  @SuppressWarnings("unchecked")
  public void testCLIMapClientServerProxy() throws IOException {
    // OK, let's plug the first 4 pieces together. Some CLI parsing,
    // a ChiseledMap, accessible over sockets, remoted via a proxy.

    // server ----------------------

    // faux cli parse
    List<String> args = DumbCLIParse.args(new String[] { "-server=1113" });
    int serverPort = Integer.parseInt(DumbCLIParse.scanForArgWithParm(args, "server").orElse("1110"));

    // create perisistent map
    ChiseledMap<Integer, String>
      map =
      new ChiseledMap<>(tmp.newFile(), ChiseledMap.OpenOption.DONT_CARE, null);

    // create server
    ProxyMe.Server<ConcurrentMap<Integer, String>>
      proxyServer =
      new ProxyMe.Server<>(ConcurrentMap.class, map);

    // pool for service threads for connections as they come in
    ExecutorService serverPool = Executors.newCachedThreadPool();

    // setup a server that receives an invocation, invokes it, sends back the result
    PojoClientServer.Server server = new PojoClientServer.Server("Unified", serverPort, (conn) -> {
      serverPool.submit(() -> {
        for (; ; ) {
          // endlessly read/invoke/respond
          ProxyMe.Invocation inv = (ProxyMe.Invocation) conn.receive();
          ProxyMe.InvocationReturn<Object> ret = proxyServer.invoke(inv);
          conn.send(ret);
        }
      });
    });

    // start server
    server.startServer();

    // client ----------------------

    // cli parse
    args = DumbCLIParse.args(new String[] { "--client=1113" });
    int destPort = Integer.parseInt(DumbCLIParse.scanForArgWithParm(args, "client").orElse("1110"));

    // create client bundle
    PojoClientServer.Client clientBundle = new PojoClientServer.Client("unified client");

    // one connection
    PojoClientServer.SingleConnection
      conn =
      clientBundle.createOutgoingClient(new InetSocketAddress("localhost", destPort), 5 * 1000);

    AtomicReference<Consumer<ProxyMe.InvocationReturn<Object>>> outRef = new AtomicReference<>(null);

    // client proxy for usage
    ProxyMe.Client<ConcurrentMap<Integer, String>>
      clientMapProxy =
      new ProxyMe.Client<>(Executors.newScheduledThreadPool(1), ConcurrentMap.class, (inv) -> {
        try {
          // send invocation receive return, process it.
          ProxyMe.InvocationReturn<Object> ir = (ProxyMe.InvocationReturn<Object>) conn.sendAndReceive(inv);
          outRef.get().accept(ir);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }, 1, TimeUnit.SECONDS);

    // for call back usage
    outRef.set(clientMapProxy::complete);

    // do some map stuff
    exercise(clientMapProxy.clientProxy());

    // shut it down
    server.stop();
    clientBundle.closeAll();
  }

  private void exercise(ConcurrentMap<Integer, String> proxy) {
    // things will work, as long as everything is serializable
    // so crud things will work, iteration/keyset etc will not.
    // that's ok though, just an exemplar
    proxy.put(1, "hello");
    assertThat(proxy.get(1), is("hello"));
    for (int i = 0; i < 100; i++) {
      proxy.put(i, "hello " + i);
    }
    for (int i = 0; i < 100; i++) {
      assertThat(proxy.get(i), is("hello " + i));
    }
    assertThat(proxy.size(), is(100));
  }
}

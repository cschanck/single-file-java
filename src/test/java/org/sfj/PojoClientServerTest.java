package org.sfj;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PojoClientServerTest {

  @Test
  public void testOneServer1ClientSimpleSend() throws IOException, InterruptedException {
    AtomicBoolean success = new AtomicBoolean(false);
    ExecutorService pool = Executors.newCachedThreadPool();
    PojoClientServer.Server server = new PojoClientServer.Server("test server", 1111, (cl) -> {
      pool.submit(() -> {
        for (; ; ) {
          Object o = cl.receive();
          if ((int) o == 10) {
            success.set(true);
          }
        }
      });
    });
    server.startServer();

    PojoClientServer.Client client = new PojoClientServer.Client("test client");
    PojoClientServer.SingleConnection
      cl =
      client.createOutgoingClient(new InetSocketAddress("localhost", 1111), 2000);
    cl.send(new Integer(10));
    Thread.sleep(2000);
    client.closeAll();
    server.stop();
    pool.shutdownNow();
    assertThat(success.get(), is(true));
  }

  @Test
  public void testOneServer1ClientSimpleSendRecieve() throws IOException, InterruptedException {
    ExecutorService pool = Executors.newCachedThreadPool();
    PojoClientServer.Server server = new PojoClientServer.Server("test server", 1112, (cl) -> {
      pool.submit(() -> {
        for (; ; ) {
          Object o = cl.receive();
          cl.send((int)o+1);
        }
      });
    });
    server.startServer();
    PojoClientServer.Client client = new PojoClientServer.Client("test server");
    PojoClientServer.SingleConnection
      cl =
      client.createOutgoingClient(new InetSocketAddress("localhost", 1111), 1000);
    Integer got = (Integer) cl.sendAndReceive(new Integer(10));
    client.closeAll();
    server.stop();
    assertThat(got, is(11));
  }
}

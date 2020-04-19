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
    PojoClientServer.Server server = new PojoClientServer.Server("test server", 1111, (cl) -> pool.submit(() -> {
      for (; ; ) {
        Object o = cl.receive();
        if ((int) o == 10) {
          success.set(true);
        }
      }
    }));
    server.startServer();

    PojoClientServer.Client client = new PojoClientServer.Client("test client");
    PojoClientServer.SingleConnection
      cl =
      client.createOutgoingClient(new InetSocketAddress("localhost", 1111), 2000);
    cl.send(10);
    Thread.sleep(2000);
    client.closeAll();
    server.stop();
    pool.shutdownNow();
    assertThat(success.get(), is(true));
  }

  @Test
  public void testOneServer1ClientSimpleSendRecieve() throws IOException {
    ExecutorService pool = Executors.newCachedThreadPool();
    PojoClientServer.Server server = new PojoClientServer.Server("test server", 1112, (cl) -> pool.submit(() -> {
      for (; ; ) {
        Object o = cl.receive();
        cl.send((int)o+1);
      }
    }));
    server.startServer();
    PojoClientServer.Client client = new PojoClientServer.Client("test server");
    PojoClientServer.SingleConnection
      cl =
      client.createOutgoingClient(new InetSocketAddress("localhost", 1112), 1000);
    Integer got = (Integer) cl.sendAndReceive(10);
    client.closeAll();
    server.stop();
    assertThat(got, is(11));
  }
}

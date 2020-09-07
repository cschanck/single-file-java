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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

/**
 * <p>This class provides very simple client/server framework for passing pojos
 * between a client and server, using either standard java serialization, or a user
 * specified encode/decode path.
 *
 * <p>You can either fire a message at a remote, or send a message and ask for
 * a response. That's it. But small building blocks are sometimes enough.
 *
 * <p>See {@link PojoClientServer.Server} and {@link PojoClientServer.Client}
 * for more info
 * @author cschanck
 */
public class PojoClientServer {

  /**
   * Encoder interface. Turn an object into a byte[].
   */
  @FunctionalInterface
  interface Encoder {
    Encoder SERIALIZE = (m) -> {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeUnshared(m);
      oos.flush();
      return baos.toByteArray();
    };

    byte[] encode(Object msg) throws IOException;
  }

  /**
   * Decoder interface. Turn a single byte[] into an object.
   */
  @FunctionalInterface
  interface Decoder {
    Decoder SERIALIZE = (b) -> {
      ByteArrayInputStream bais = new ByteArrayInputStream(b);
      ObjectInputStream ois = new ObjectInputStream(bais);
      try {
        return ois.readObject();
      } catch (ClassNotFoundException e) {
        throw new IOException(e);
      }
    };

    Object decode(byte[] buf) throws IOException;
  }

  /**
   * Single connection class. Used for a managed connection either for outbound
   * (client) connections, or for new inbound connections (server). Id is
   * unique for this Server or Client instance.
   */
  public static class SingleConnection {
    private final int id;
    private final Socket client;
    private final DataInputStream dis;
    private final DataOutputStream dos;
    private final Encoder encoder;
    private final Decoder decoder;
    private final Consumer<SingleConnection> onClose;
    private volatile Throwable lastIgnoredThrowable = null;
    private final StampedLock lock = new StampedLock();

    public SingleConnection(int id,
                            Socket client,
                            Encoder encoder,
                            Decoder decoder,
                            Consumer<SingleConnection> onClose) throws IOException {
      Objects.requireNonNull(client);
      Objects.requireNonNull(encoder);
      Objects.requireNonNull(decoder);
      if (!client.isConnected()) {
        throw new IOException();
      }
      this.id = id;
      this.client = client;
      this.dis = new DataInputStream(client.getInputStream());
      this.dos = new DataOutputStream(client.getOutputStream());
      this.encoder = encoder;
      this.decoder = decoder;
      this.onClose = onClose == null ? (r) -> {} : onClose;
    }

    /**
     * Get the last ignored exception
     * @return throwable, or null
     */
    public Throwable getLastIgnoredThrowable() {
      return lastIgnoredThrowable;
    }

    /**
     * ID for this connection/
     * @return id
     */
    public int getId() {
      return id;
    }

    public boolean isAlive() {
      return client.isConnected();
    }

    public Socket getSocket() {
      return client;
    }

    /**
     * Non failing close().
     */
    public void close() {
      try {
        client.close();
      } catch (IOException e) {
        lastIgnoredThrowable = e;
      }
      try {
        onClose.accept(this);
      } catch (Throwable t) {
        lastIgnoredThrowable = t;
      }
    }

    /**
     * Send a message, don't wait.
     * @param msg message object
     * @throws IOException on send failure
     */
    public void send(Object msg) throws IOException {
      long st = lock.writeLock();
      try {
        sendNoLock(msg);
      } finally {
        lock.unlock(st);
      }
    }

    private void sendNoLock(Object msg) throws IOException {
      try {
        byte[] payload = encoder.encode(msg);
        dos.writeInt(payload.length);
        dos.write(payload);
        dos.flush();
      } catch (IOException e) {
        close();
        throw e;
      }
    }

    /**
     * Send and receive a message; wait until done.
     * @param msg message to send
     * @return object you received as answer
     * @throws IOException on read/write exception
     */
    public Object sendAndReceive(Object msg) throws IOException {
      long st = lock.writeLock();
      try {
        sendNoLock(msg);
        if (client.isConnected()) {
          return receiveNoLock();
        } else {
          close();
          throw new IOException();
        }
      } finally {
        lock.unlock(st);
      }
    }

    /**
     * Just receive a message.
     * @return message object
     * @throws IOException on read exception
     */
    public Object receive() throws IOException {
      long st = lock.writeLock();
      try {
        return receiveNoLock();
      } finally {
        lock.unlock(st);
      }
    }

    private Object receiveNoLock() throws IOException {
      try {
        int len = dis.readInt();
        byte[] b = new byte[len];
        dis.readFully(b);
        return decoder.decode(b);
      } catch (IOException e) {
        close();
        throw e;
      }
    }

    @Override
    public String toString() {
      return "Client{" + "id=" + id + ", client=" + client + '}';
    }
  }

  /**
   * Server. Listens on a port, then allocated client connections
   * as connections are accepted.
   */
  public static class Server {
    private final String name;
    private final int listenPort;
    private final Encoder encoder;
    private final Decoder decoder;
    private final Consumer<SingleConnection> callback;
    private final ExecutorService pool;
    private volatile boolean listening;
    private ServerSocket serverSocket;
    private final AtomicInteger clientIdGen = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, SingleConnection> incomingClients = new ConcurrentHashMap<>();
    private volatile boolean alive = true;
    private volatile Throwable lastIgnoredThrowable = null;

    /**
     * Constructor for a server object. Listens on a port, accepts connections, and allocates
     * single connection for those accepted connections. Invokes a callback to notify the
     * user of those new connections.
     * @param name Name of this server.
     * @param listenPort Port to listen on
     * @param callback Callback for accepting new connections.
     */
    public Server(String name, int listenPort, Consumer<SingleConnection> callback) {
      this(name, listenPort, null, null, callback);
    }

    /**
     * Constructor for a server object. Listens on a port, accepts connections, and allocates
     * single connection for those accepted connections. Invokes a callback to notify the
     * user of those new connections.
     * @param name Name of this server.
     * @param listenPort Port to listen on.
     * @param encoder Encoder
     * @param decoder Decoder
     * @param callback Callback for accepting new connections.
     */
    public Server(String name, int listenPort, Encoder encoder, Decoder decoder, Consumer<SingleConnection> callback) {
      this.name = name;
      this.listenPort = listenPort;
      this.encoder = encoder == null ? Encoder.SERIALIZE : encoder;
      this.decoder = decoder == null ? Decoder.SERIALIZE : decoder;
      this.callback = callback;

      final AtomicInteger idgen = new AtomicInteger();
      this.pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName(name + "-" + idgen.getAndIncrement());
        return t;
      });
    }

    /**
     * Get the name. Useful cosmetics.
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Get the last ignored exception
     * @return throwable, or null
     */
    public Throwable getLastIgnoredThrowable() {
      return lastIgnoredThrowable;
    }

    /**
     * Start the server.
     * @return this server.
     * @throws IOException on failure to sonnect
     */
    public synchronized Server startServer() throws IOException {
      if (listening) {
        return null;
      }
      serverSocket = new ServerSocket(listenPort);
      listening = true;
      pool.submit(() -> {
        while (listening) {
          try {
            Socket c = serverSocket.accept();
            registerIncomingClient(c);
          } catch (Exception e) {
            try {
              serverSocket.close();
            } catch (IOException ex) {
              lastIgnoredThrowable = null;
            }
            break;
          }
        }
        listening = false;
      });
      return this;
    }

    /**
     * Check if we are currently listening.
     * @return true if we are listening
     */
    public boolean isListening() {
      return listening;
    }

    private synchronized void registerIncomingClient(Socket c) {
      int id = clientIdGen.getAndIncrement();
      try {
        SingleConnection cl = new SingleConnection(id, c, encoder, decoder, this::deregisterClient);
        incomingClients.put(cl.getId(), cl);
        callback.accept(cl);
      } catch (IOException e) {
        lastIgnoredThrowable = e;
      }
    }

    /**
     * Return current connections, in no particular order.
     * @return collection of connections
     */
    public Collection<SingleConnection> getConnections() {
      return Collections.unmodifiableCollection(incomingClients.values());
    }

    private void deregisterClient(SingleConnection client) {
      client.close();
      incomingClients.remove(client.getId());
    }

    /**
     * Stop the server, close all connections.
     */
    public synchronized void stop() {
      if (alive) {
        alive = false;
        stopListening();
        pool.shutdownNow();
        incomingClients.values().forEach(cl -> {
          try {
            cl.getSocket().close();
          } catch (IOException e) {
            lastIgnoredThrowable = e;

          }
        });
        incomingClients.clear();
      }
    }

    private void stopListening() {
      if (listening) {
        listening = false;
        try {
          serverSocket.close();
        } catch (Exception e) {
          lastIgnoredThrowable = e;
        }
      }
    }
  }

  /**
   * Bundle of clients from this porcess to a variety of remote endpoints.
   */
  public static class Client {
    private final ConcurrentHashMap<Integer, SingleConnection> outgoingClients = new ConcurrentHashMap<>();
    private final AtomicInteger clientIdGen = new AtomicInteger(0);
    private final String name;
    private final Encoder encoder;
    private final Decoder decoder;
    private volatile boolean alive = true;
    private volatile Throwable lastIgnoredThrowable = null;

    /**
     * Create client bundle.
     * @param name Name
     */
    public Client(String name) {
      this(name, null, null);
    }

    /**
     * Create client bundle.
     * @param name Name.
     * @param encoder Encoder. Will use serialization if null.
     * @param decoder Decoder. Will use serialization if null.
     */
    public Client(String name, Encoder encoder, Decoder decoder) {
      this.name = name;
      this.encoder = encoder == null ? Encoder.SERIALIZE : encoder;
      this.decoder = decoder == null ? Decoder.SERIALIZE : decoder;
    }

    /**
     * Get the name.
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Create a managed outgoing client.
     * @param dest Destination.
     * @param connectTimeout timout for connection in millis
     * @return connection
     * @throws IOException one connection failure
     */
    public SingleConnection createOutgoingClient(InetSocketAddress dest, int connectTimeout) throws IOException {
      Socket sock = new Socket();
      sock.connect(dest, connectTimeout);
      SingleConnection ret = new SingleConnection(clientIdGen.incrementAndGet(), sock, encoder, decoder, this::deregisterClient);
      outgoingClients.put(ret.getId(), ret);
      return ret;
    }

    private void deregisterClient(SingleConnection client) {
      client.close();
      outgoingClients.remove(client.getId());
    }

    /**
     * Close all managed connections associated with this client
     */
    public synchronized void closeAll() {
      if (alive) {
        alive = false;
        outgoingClients.values().forEach(cl -> {
          try {
            cl.getSocket().close();
          } catch (IOException e) {
            lastIgnoredThrowable = e;
          }
        });
        outgoingClients.clear();
      }
    }

    /**
     * Get the last ignored exception
     * @return throwable, or null
     */
    public Throwable getLastIgnoredThrowable() {
      return lastIgnoredThrowable;
    }
  }

  private PojoClientServer() {
  }
}

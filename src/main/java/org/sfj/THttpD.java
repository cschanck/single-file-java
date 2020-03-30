package org.sfj;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class THttpD implements Runnable {

  private static final Logger LOGGER = Logger.getLogger(THttpD.class.getName());
  private static final int CONNECTION_BUFFER_SIZE = 2048;

  private final Selector selector = Selector.open();

  public THttpD(Path root, int port) throws IOException {
    SocketAddress bindAddress = new InetSocketAddress(port);
    LOGGER.log(Level.INFO, "THttpD binding to {0}", bindAddress);
    ServerSocketChannel.open().bind(bindAddress).configureBlocking(false).register(selector, SelectionKey.OP_ACCEPT, (Attachment) key -> {
              SocketChannel incoming = ((ServerSocketChannel) key.channel()).accept();
              if (incoming != null) {
                incoming.configureBlocking(false).register(selector, SelectionKey.OP_READ, new RequestReader(root));
              }
            });
  }

  public void stop() throws IOException {
    try {
      selector.close();
    } finally {
      selector.wakeup();
    }
  }

  public void run() {
    while (selector.isOpen()) {
      try {
        selector.select(100);
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Exception retrieving active keys", e);
      }

      selector.selectedKeys().forEach(k -> {
        try {
          ((Attachment) k.attachment()).process(k);
        } catch (IOException e) {
          try {
            k.channel().close();
          } catch (IOException f) {
            e.addSuppressed(f);
          }
          LOGGER.log(Level.SEVERE, "Exception processing selection key: " + k, e);
        }
      });
    }
  }

  private static class RequestReader implements Attachment {
    private static final Pattern METHOD = Pattern.compile("(?<method>[\\p{ASCII}&&[^\\p{Cntrl}\\t \\Q<>@,;:\"/[]?={}\\E]]+)");
    private static final Pattern REQUEST_URI = Pattern.compile("/+(?<uri>\\S*)"); //needs correcting
    private static final Pattern HTTP_VERSION = Pattern.compile("HTTP/(?<version>\\d+\\.\\d+)");
    private static final Pattern REQUEST_PATTERN = Pattern.compile("^" + METHOD + "[ ]+" + REQUEST_URI + "[ ]+" + HTTP_VERSION + "?$", Pattern.MULTILINE);

    private final ByteBuffer dataBuffer = (ByteBuffer) ByteBuffer.allocateDirect(CONNECTION_BUFFER_SIZE).position(CONNECTION_BUFFER_SIZE);
    private final CharBuffer requestBuffer = CharBuffer.allocate(CONNECTION_BUFFER_SIZE);
    private final CharsetDecoder decoder = US_ASCII.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
    private final StringBuilder request = new StringBuilder();
    private final Path root;

    private RequestReader(Path root) {
      this.root = root;
    }

    @Override
    public void process(SelectionKey key) throws IOException {
      if (((SocketChannel) key.channel()).read(dataBuffer.compact()) > 0) {
        decoder.decode((ByteBuffer) dataBuffer.flip(), (CharBuffer) requestBuffer.clear(), false);
        Matcher matcher = REQUEST_PATTERN.matcher(request.append(requestBuffer.flip()));
        if (matcher.lookingAt()) {
          Path resource = root.resolve(Paths.get(matcher.group("uri")));
          if (resource.startsWith(root) && Files.isRegularFile(resource) && Files.isReadable(resource)) {
            LOGGER.log(Level.INFO, "Serving:\n {0}", request);
            switch (matcher.group("method")) {
              case "GET":
                key.interestOps(SelectionKey.OP_WRITE).attach(new GetResponseWriter(resource));
                break;
              case "HEAD":
                key.interestOps(SelectionKey.OP_WRITE).attach(new ResponseHeaderWriter("HTTP/1.0 200 OK"));
                break;
              default:
                key.interestOps(SelectionKey.OP_WRITE).attach(new ResponseHeaderWriter("HTTP/1.0 501 Not Implemented"));
                break;
            }
          } else {
            key.interestOps(SelectionKey.OP_WRITE).attach(new ResponseHeaderWriter("HTTP/1.0 404 Not Found"));
          }
        }
      }
    }
  }

  private static class ResponseHeaderWriter implements Attachment {

    private final ByteBuffer header;

    public ResponseHeaderWriter(String header) {
      this.header = US_ASCII.encode(header + "\r\n");
    }

    @Override
    public void process(SelectionKey key) throws IOException {
      if (writeHeader(key)) {
        key.channel().close();
      }
    }

    protected boolean writeHeader(SelectionKey key) throws IOException {
      ((SocketChannel) key.channel()).write(header);
      return !header.hasRemaining();
    }
  }

  private static class GetResponseWriter extends ResponseHeaderWriter {

    private final Path resource;

    public GetResponseWriter(Path resource) {
      super("HTTP/1.0 200 OK\r\n");
      this.resource = resource;
    }

    @Override
    public void process(SelectionKey key) throws IOException {
      if (writeHeader(key)) {
        key.attach(new ResponseBodyWriter(FileChannel.open(resource)));
      }
    }
  }

  private static class ResponseBodyWriter implements Attachment {

    private final FileChannel data;

    public ResponseBodyWriter(FileChannel channel) {
      this.data = channel;
    }

    @Override
    public void process(SelectionKey key) throws IOException {
      long written = data.transferTo(data.position(), CONNECTION_BUFFER_SIZE, (WritableByteChannel) key.channel());
      if (data.position(data.position() + written).position() == data.size()) {
        key.channel().close();
      }
    }
  }

  public static void main(String[] args) throws IOException {
    new THttpD(Paths.get(args[0]), 8080).run();
  }

  interface Attachment {
    void process(SelectionKey key) throws IOException;
  }
}

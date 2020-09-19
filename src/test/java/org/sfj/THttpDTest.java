package org.sfj;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class THttpDTest {

  private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
  private static final THttpD DAEMON;
  static {
    try {
      DAEMON = new THttpD(Paths.get("src", "test", "resources"), 8080);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @BeforeClass
  public static void startServer() {
    EXECUTOR.execute(DAEMON);
  }

  @AfterClass
  public static void stopServer() throws IOException {
    try {
      DAEMON.stop();
    } finally {
      EXECUTOR.shutdown();
    }
  }

  @Test
  public void testGetNotFound() throws IOException {
    HttpResponse httpResponse = Request.Get(URI.create("http://localhost:8080/not-existing")).execute().returnResponse();
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(404));
    assertThat(httpResponse.getStatusLine().getReasonPhrase(), is("Not Found"));
  }

  @Test
  public void testHeadNotFound() throws IOException {
    HttpResponse httpResponse = Request.Head(URI.create("http://localhost:8080/not-existing")).execute().returnResponse();
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(404));
    assertThat(httpResponse.getStatusLine().getReasonPhrase(), is("Not Found"));
  }

  @Test
  public void testPutNotSupported() throws IOException {
    HttpResponse httpResponse = Request.Put(URI.create("http://localhost:8080/org/sfj/colors.json")).execute().returnResponse();
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(501));
    assertThat(httpResponse.getStatusLine().getReasonPhrase(), is("Not Implemented"));
  }

  @Test
  public void testGetSucceeds() throws IOException {
    HttpResponse httpResponse = Request.Get(URI.create("http://localhost:8080/org/sfj/colors.json")).execute().returnResponse();
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(200));
    assertThat(httpResponse.getStatusLine().getReasonPhrase(), is("OK"));
  }

  @Test
  public void testHeadSucceeds() throws IOException, InterruptedException {
    HttpResponse httpResponse = Request.Head(URI.create("http://localhost:8080/org/sfj/colors.json")).execute().returnResponse();
    assertThat(httpResponse.getStatusLine().getStatusCode(), is(200));
    assertThat(httpResponse.getStatusLine().getReasonPhrase(), is("OK"));
  }
}

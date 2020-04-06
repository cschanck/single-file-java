package org.sfj;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.hamcrest.MatcherAssert.assertThat;

public class ReplacementDiskSortTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  static class IntElement extends ReplacementDiskSort.Element {
    public IntElement(int i) {
      super(i);
    }

    @Override
    public Integer getData() {
      return (Integer) super.getData();
    }
  }

  private static ReplacementDiskSort.ExternalAppender<IntElement> makeAppender(File f) throws IOException {
    FileOutputStream fos = new FileOutputStream(f, true);
    BufferedOutputStream bos = new BufferedOutputStream(fos, 32 * 1024);
    DataOutputStream dos = new DataOutputStream(bos);
    return new ReplacementDiskSort.ExternalAppender<IntElement>() {
      @Override
      public void append(IntElement elem) throws IOException {
        dos.writeInt(elem.getData());
      }

      @Override
      public void close() {
        try {
          dos.flush();
          dos.close();
        } catch (IOException e) {
        }
      }
    };
  }

  private static ReplacementDiskSort.ExternalIterator<IntElement> makeIter(File file) throws FileNotFoundException {
    return new ReplacementDiskSort.ExternalIterator<IntElement>() {
      FileInputStream fis = new FileInputStream(file);
      BufferedInputStream bis = new BufferedInputStream(fis, 32 * 1024);
      DataInputStream dis = new DataInputStream(bis);

      @Override
      public IntElement next() {
        if (dis != null) {
          try {
            return new IntElement(dis.readInt());
          } catch (IOException e) {
            try {
              dis.close();
            } catch (IOException ex) {
            }
            dis = null;
          }
        }
        return null;
      }
    };
  }

  @Test
  public void testRunCreation() throws IOException {
    File folder = tmp.newFolder();
    folder.mkdirs();
    Random r = new Random(0);
    File src = genIntFile(new File(folder, "source"), r, 10000);
    ReplacementDiskSort<IntElement>
      kd =
      new ReplacementDiskSort<>(ReplacementDiskSortTest::makeIter, ReplacementDiskSortTest::makeAppender,
        Comparator.comparing(IntElement::getData), false);
    kd.workDirectory = folder;
    List<File> runs = kd.makeRuns(src, 1000);
    runs.forEach(f -> verifyOrder(f, (ff) -> makeIter(ff), Comparator.comparing(IntElement::getData)));
  }

  private static <E extends ReplacementDiskSort.Element> void verifyOrder(File f,
                                                                          ReplacementDiskSort.IterMaker<E> iterMaker,
                                                                          Comparator<E> comp) {
    E last = null;
    try {
      ReplacementDiskSort.ExternalIterator<E> iter = iterMaker.make(f);
      for (; ; ) {
        E p = iter.next();
        if (p != null) {
          if (last != null) {
            assertThat(comp.compare(p, last), Matchers.greaterThanOrEqualTo(0));
          }
          last = p;
        } else {
          break;
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static File genIntFile(File dest, Random rand, int many) throws IOException {
    FileChannel output = FileChannel.open(dest.toPath(), APPEND, CREATE_NEW);

    ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
    for (int i = 0; i < many; i++) {
      b.clear();
      b.putInt(0, rand.nextInt(2 * many));
      do {
        output.write(b);
      } while (b.hasRemaining());
    }
    output.close();
    return dest;
  }

  public static class FileChannelInputBuffer {
    private FileChannel fc;
    private ByteBuffer buffer;
    private long lastReadFilePos = 0;
    private long nextReadPos = 0;

    public FileChannelInputBuffer(FileChannel fc, int initSize) throws IOException {
      this.fc = fc;
      loadBufferAt(0, initSize);
    }

    public void close() {
      try {
        fc.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      fc = null;
      buffer = null;
    }

    public FileChannel getFC() {
      return fc;
    }

    public long getNextReadPosition() {
      return nextReadPos;
    }

    public void setNextReadPosition(long nextReadPos) {
      this.nextReadPos = nextReadPos;
    }

    public ByteBuffer read(int len) throws IOException {
      if (nextReadPos >= lastReadFilePos && (nextReadPos + len) < lastReadFilePos + buffer.remaining()) {
        // cool, within this buffer.
        int off = (int) (nextReadPos - lastReadFilePos);
        ByteBuffer ret = buffer.duplicate();
        ret.position(off).limit(off + len);
        nextReadPos = nextReadPos + len;
        return ret.slice();
      }
      if (nextReadPos + len > fc.size()) {
        throw new EOFException();
      }
      int blen = Math.max(buffer.capacity(), len);
      loadBufferAt(nextReadPos, blen);

      int off = (int) (nextReadPos - lastReadFilePos);
      ByteBuffer ret = buffer.duplicate();
      ret.position(off).limit(off + len);
      nextReadPos = nextReadPos + len;
      return ret.slice();
    }

    private void loadBufferAt(long p, int bSize) throws IOException {
      lastReadFilePos = p;
      fc.position(p);
      buffer = ByteBuffer.allocateDirect(bSize);
      int lim = (int) Math.min(fc.size() - lastReadFilePos, buffer.capacity());
      buffer.limit(lim);
      do {
        fc.read(buffer);
      } while (buffer.hasRemaining());
      buffer.clear();
      buffer.limit(lim);
    }
  }

  static class StrElement extends ReplacementDiskSort.Element {

    public StrElement(String data) {
      super(data);
    }

    @Override
    public String getData() {
      return (String) super.getData();
    }
  }

  @Test
  public void testStrings() throws IOException {
    ReplacementDiskSort.AppenderMaker<StrElement> aMaker = f -> {
      final Writer fw = new FileWriter(f);
      final BufferedWriter bw = new BufferedWriter(fw, 1024 * 1024);
      return new ReplacementDiskSort.ExternalAppender<StrElement>() {
        @Override
        public void append(StrElement elem) throws IOException {
          bw.append(elem.getData());
          bw.newLine();
        }

        @Override
        public void close() {
          try {
            bw.flush();
            fw.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };
    };

    ReplacementDiskSort.IterMaker<StrElement> iterMaker = f -> {
      final FileReader fr = new FileReader(f);
      final BufferedReader br = new BufferedReader(fr, 1024 * 1024);
      return () -> {
        String p = br.readLine();
        if (p == null) {
          br.close();
          fr.close();
          return null;
        }
        return new StrElement(p);
      };
    };

    File src = tmp.newFile();
    File dest = tmp.newFile();
    dest.delete();
    Random r = new Random(0);
    System.out.println("Generating...");
    genRandString(src, 20, 60, 100000, r);
    File work = tmp.newFolder();
    ReplacementDiskSort.runOnce(iterMaker, aMaker, Comparator.comparing(StrElement::getData), true, src, 1000, 100,
      dest, work);
    System.out.println("Verifying");
    verifyOrder(dest, (ff) -> iterMaker.make(ff), Comparator.comparing(StrElement::getData));
  }

  public static void genRandString(File dest, int minChars, int maxChars, int many, Random r) throws IOException {
    int range = maxChars - minChars;
    FileWriter fw = new FileWriter(dest);
    BufferedWriter bw = new BufferedWriter(fw);
    char[] cArray = new char[maxChars];
    for (int i = 0; i < many; i++) {
      int len = r.nextInt(range) + minChars;
      for (int p = 0; p < len; p++) {
        cArray[p] = (char) (32 + r.nextInt(80));
      }
      bw.append(new String(cArray), 0, len);
      bw.newLine();
    }
    bw.flush();
    fw.close();
  }
}

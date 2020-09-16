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
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.zip.CRC32;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * <p>Dead simple persistent ordered map. It is thread safe, via coarse synchronization
 * on writes. Reads are unsynchronized, though the underlying FileChannel is shared.
 * Deletes do not reclaim file space; they simply stop referencing the
 * block. Writes are CRC checked on restart, file is truncated to match the valid
 * length.
 *
 * <p>This class is useful for prototyping when you need a persistent store and don't
 * want to bother much. You provide a file, and optionally an encoder and decoder, and
 * you can shove Objects in a sorted map structure that will serve get()s from disk,
 * and save mutations to disk. Basically, every change is written to a log, with crc.
 * Old entries are just left there; no garbage collection is done. So it is not
 * useful for massive stores, or fast changing ones, but for prototyping it's quite useful.
 *
 * <p>Null values are not allowed. An in-memory sorted list keeps keys/disk addresses
 * for lookup. On restart, the entire log file is traversed, rebuilding the in memory
 * picture of keys to locations.
 *
 * <p>The core methods are ioGet(), ioSet(), and ioUnset(); these throw IOExceptions
 * on ... IO exceptions. The Map methods wrap these methods and throw
 * the unchecked RuntimeIOException.
 * @author cschanck
 */
public class ChiseledMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {

  public static final int DIGEST_MASK = 0x7fffffff;

  public static final byte[] HDR = "(-:AnonymousBC:ChiseledMap-)".getBytes(StandardCharsets.US_ASCII);

  /**
   * Open methods.
   */
  public enum OpenOption {
    MUST_BE_NEW, MUST_EXIST, DONT_CARE
  }

  /**
   * For the map methods, IOExceptions are wrapped in this.
   */
  public static class RuntimeIOException extends RuntimeException {
    public RuntimeIOException(Throwable cause) {
      super(cause);
    }
  }

  /**
   * Encode a key/value (here you must handle null values) into a ByteBuffer.
   * @param <KK> key type
   * @param <VV> value type
   */
  @FunctionalInterface
  public interface Encoder<KK, VV> {
    ByteBuffer encode(KK k, VV v) throws IOException;
  }

  /**
   * Decode a byte array into a key/value pair.
   * @param <KK> key type
   * @param <VV> value type
   */
  @FunctionalInterface
  public interface Decoder<KK, VV> {
    Entry<KK, VV> decode(byte[] bArray) throws IOException;
  }

  private final CRC32 digest;
  private final Comparator<K> comp;
  private final FileChannel fc;
  private final ConcurrentSkipListMap<K, Long> map;
  private final ByteBuffer lenBuffer = ByteBuffer.allocate(4);
  private final ByteBuffer digestBuffer = ByteBuffer.allocate(4);
  private final ByteBuffer writeBuffer = ByteBuffer.allocateDirect(1024 * 1024);
  private final File file;
  private long currentWritePos = HDR.length;
  private long nextWritePos = HDR.length;
  private volatile int pendingWrites = 0;
  private final Encoder<K, V> encoder;
  private final Decoder<K, V> decoder;
  private long entriesOnDisk = 0;

  /**
   * Default java serialization. Good enough.
   */
  @SuppressWarnings("rawtypes")
  public static Encoder ENCODE_JAVA_SER = (k, v) -> {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(8 * 1024);
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeUnshared(k);
    oos.writeUnshared(v);
    oos.flush();
    byte[] arr = baos.toByteArray();
    return ByteBuffer.wrap(arr);
  };

  /**
   * Default java deserialization. Good enough.
   */
  @SuppressWarnings( { "rawtypes" })
  public static Decoder DECODE_JAVA_SER = (barr) -> {
    ByteArrayInputStream bais = new ByteArrayInputStream(barr);
    ObjectInputStream is = new ObjectInputStream(bais);
    try {
      Object k = is.readObject();
      Object v = is.readObject();
      return new SimpleImmutableEntry<>(k, v);
    } catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
  };

  public ChiseledMap(File file, OpenOption open, Comparator<K> comp) throws IOException {
    this(file, open, comp, null, null);
  }

  /**
   * Constructor. Create a TinyKVMap.
   * @param file File to use.
   * @param open Open mode
   * @param comp Comparator to use. If null, Comparator.naturalOrder() is used.
   * @param encoder Encoder to use. If null, default Java serializer is used.
   * @param decoder Decoder to use. If null, default Java deserializer is used.
   * @throws IOException on exception
   */
  @SuppressWarnings( { "raw", "unchecked" })
  public ChiseledMap(File file,
                     OpenOption open,
                     Comparator<K> comp,
                     Encoder<K, V> encoder,
                     Decoder<K, V> decoder) throws IOException {
    Objects.requireNonNull(file);
    this.encoder = (encoder == null) ? ENCODE_JAVA_SER : encoder;
    this.decoder = (decoder == null) ? DECODE_JAVA_SER : decoder;
    this.comp = (comp == null) ? (a, b) -> ((Comparable<K>) a).compareTo(b) : comp;
    this.map = new ConcurrentSkipListMap<>(this.comp);
    this.file = file;
    this.digest = new CRC32();
    switch (open) {
      case MUST_BE_NEW:
        this.fc = FileChannel.open(file.toPath(), CREATE_NEW, READ, WRITE);
        break;
      case MUST_EXIST:
        this.fc = FileChannel.open(file.toPath(), READ, WRITE);
        break;
      default:
        this.fc = FileChannel.open(file.toPath(), CREATE, READ, WRITE);
        break;
    }
    if (fc.size() > 0) {
      verifyHeader();
    } else {
      writeHeader();
    }
    rebuild(fc);
  }

  private void verifyHeader() throws IOException {
    ByteBuffer p = ByteBuffer.allocate(HDR.length);
    readFully(0, p);
    p.clear();
    if (p.compareTo(ByteBuffer.wrap(HDR)) != 0) {
      throw new IOException("File Header Mismatch!");
    }
  }

  private void writeHeader() throws IOException {
    // write at the beginning, then leave position alone
    fc.position(0);
    writeFully(ByteBuffer.wrap(HDR), 0);
  }

  private void rebuild(FileChannel fc) throws IOException {
    // scan the entire file, loading each entry where crc matches.
    currentWritePos = HDR.length;
    long[] nextPos = new long[1];
    for (; ; ) {
      try {
        Entry<K, V> got = fetch(currentWritePos, true, nextPos);
        map.put(got.getKey(), currentWritePos);
        currentWritePos = nextPos[0];
        entriesOnDisk++;
      } catch (Exception e) {
        // truncate to end of last known good block.
        nextWritePos = currentWritePos;
        fc.truncate(currentWritePos);
        break;
      }
    }
  }

  private Entry<K, V> fetch(long addr, boolean check, long[] nextPos) throws IOException {
    // core retrieval by address code. First, flush outstanding data
    if (pendingWrites > 0) {
      flushBuffer();
    }
    // read the length
    ByteBuffer tmp = ByteBuffer.allocate(Integer.BYTES);
    readFully(addr, tmp);
    tmp.clear();
    int len = tmp.getInt(0);
    // read data + crc
    byte[] r = new byte[len + Integer.BYTES];
    ByteBuffer wrap = ByteBuffer.wrap(r);
    readFully(addr + Integer.BYTES, wrap);
    wrap.clear();
    // if we are checking...
    if (check) {
      // last 4 bytes are the digest;
      int d = wrap.getInt(wrap.capacity() - Integer.BYTES);
      wrap.limit(wrap.capacity() - Integer.BYTES);
      synchronized (this) {
        // sync here so we can reuse digest.
        digest.reset();
        digest.update(wrap);
        int chk = (int) (digest.getValue() & DIGEST_MASK);
        if (chk != d) {
          throw new IOException();
        }
      }
      wrap.clear();
    }
    if (nextPos != null) {
      // if we have a nextpos array, return the next adddress.
      nextPos[0] = addr + len + Integer.BYTES + Integer.BYTES;
    }
    // decode key and value. Rock on. Note there is a spare 4 bytes at the
    // end. Dirty coding FTW!
    return decoder.decode(r);
  }

  private void readFully(long addr, ByteBuffer tmp) throws IOException {
    do {
      int many = fc.read(tmp, addr);
      if (many <= 0) {
        throw new IOException();
      }
      addr = addr + many;
    } while (tmp.hasRemaining());
  }

  private synchronized void flushBuffer() throws IOException {
    // flush defautl buffer, reset pending count
    flushBuffer(writeBuffer);
    pendingWrites = 0;
  }

  private void flushBuffer(ByteBuffer b) throws IOException {
    // flush arbitrary buffer to disk, update next write pos
    b.flip();
    int toWrite = b.remaining();
    writeFully(b, nextWritePos);
    b.clear();
    nextWritePos = nextWritePos + toWrite;
  }

  private synchronized long append(K key, V v) throws IOException {
    // core append path for all mutations

    // encode to byte buffer, record length
    ByteBuffer payload = encoder.encode(key, v);
    lenBuffer.clear();
    lenBuffer.putInt(0, payload.remaining());

    // create crc checksum
    digest.reset();
    digest.update(payload.slice());
    int d = (int) (digest.getValue() & DIGEST_MASK);
    digestBuffer.clear();
    digestBuffer.putInt(0, d);

    // return the current write pos
    long ret = currentWritePos;
    int fp = Integer.BYTES + Integer.BYTES + payload.remaining();
    write(lenBuffer, payload, digestBuffer, fp);
    this.currentWritePos = currentWritePos + fp;
    entriesOnDisk++;
    return ret;
  }

  private void write(ByteBuffer len, ByteBuffer arr, ByteBuffer d, int fp) throws IOException {
    // write the data into the buffer, flushing if necessary
    if (writeBuffer.capacity() < fp) {
      // can't use the write buffer, flush pending and write it explicitly.
      flushBuffer();
      ByteBuffer tmp = ByteBuffer.allocateDirect(fp);
      tmp.put(len);
      tmp.put(arr);
      tmp.put(d);
      tmp.flip();
      flushBuffer(tmp);
    } else {
      if (writeBuffer.remaining() < fp) {
        // no room at the inn. flush and go.
        flushBuffer();
      }
      // actually append the 3 parts to the buffer, inc the counter.
      writeBuffer.put(len);
      writeBuffer.put(arr);
      writeBuffer.put(d);
      pendingWrites++;
    }
  }

  private void writeFully(ByteBuffer b, long pos) throws IOException {
    while (b.hasRemaining()) {
      int wrote = fc.write(b, pos);
      pos = pos + wrote;
    }
  }

  /**
   * Number of entries actually on the disk, even if invalid.
   * @return number of entries on disk, live and dead.
   */
  public long entriesOnDisk() {
    return entriesOnDisk;
  }

  /**
   * File being used.
   * @return file
   */
  public File getFile() {
    return file;
  }

  /**
   * File footprint on disk.
   * @return file size
   * @throws IOException on exception
   */
  public long bytesOnDisk() throws IOException {
    flushBuffer();
    return fc.size();
  }

  /**
   * Close this TinyKVMap.
   * @throws IOException on exception
   */
  public synchronized void close() throws IOException {
    flushBuffer();
    fc.close();
    map.clear();
  }

  @Override
  public int size() {
    return map.size();
  }

  /**
   * Entry iterator. Necessary slushy with respect to concurrency.
   * @return iterator of entries.
   */
  public Iterable<Entry<K, V>> entries() {
    return () -> new Iterator<Entry<K, V>>() {
      final Iterator<Entry<K, Long>> base = map.entrySet().iterator();

      @Override
      public boolean hasNext() {
        return base.hasNext();
      }

      @Override
      public Entry<K, V> next() {
        Entry<K, Long> n = base.next();
        try {
          return fetch(n.getValue(), false, null);
        } catch (IOException e) {
          throw new RuntimeIOException(e);
        }
      }
    };
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return new AbstractSet<Entry<K, V>>() {
      @Override
      public Iterator<Entry<K, V>> iterator() {
        return entries().iterator();
      }

      @Override
      public int size() {
        return ChiseledMap.this.size();
      }

      @Override
      public boolean add(Entry<K, V> entry) {
        return ChiseledMap.this.putIfAbsent(entry.getKey(), entry.getValue()) == null;
      }

      @Override
      public boolean remove(Object o) {
        return ChiseledMap.this.remove(o) != null;
      }

      @Override
      public void clear() {
        ChiseledMap.this.clear();
      }
    };
  }

  /**
   * Flush any buffered changes to disk, fsync.
   * @throws IOException If you get an IOException, literally no guarantees
   * can be made about the on disk state of the data.
   */
  public void flush() throws IOException {
    flushBuffer();
    fc.force(false);
  }

  /**
   * Retrieve the value associated with a specified key.
   * @param key key value
   * @return value
   * @throws IOException on exception
   */
  public V ioGet(Object key) throws IOException {
    Long addr = map.get(key);
    if (addr != null) {
      return fetch(addr, false, null).getValue();
    }
    return null;
  }

  /**
   * Clear a value.
   * @param key key value
   * @return prior value
   * @throws IOException on exception
   */
  public synchronized V ioUnset(K key) throws IOException {
    V p = ioGet(key);
    if (p != null) {
      append(key, null);
      map.remove(key);
    }
    return p;
  }

  /**
   * Associate a key to a value
   * @param key key value
   * @param v value -- cannot be null.
   * @return true if it replaced a value
   * @throws IOException on exception
   */
  public synchronized boolean ioSet(K key, V v) throws IOException {
    Objects.requireNonNull(v);
    long newAddr = append(key, v);
    return map.put(key, newAddr) != null;
  }

  public synchronized V ioGetSet(K key, V v) throws IOException {
    Objects.requireNonNull(v);
    Long addr = map.get(key);
    V ret = null;
    if (addr != null) {
      ret = fetch(addr, false, null).getValue();
    }
    long newAddr = append(key, v);
    map.put(key, newAddr);
    return ret;
  }

  /**
   * Copy only live entries to another file. Blocks writes to this map
   * while snapshotting.
   * @param f dest file
   * @return new TinyKVMap
   * @throws IOException on exception
   */
  public synchronized ChiseledMap<K, V> snapshot(File f) throws IOException {
    ChiseledMap<K, V> ret = new ChiseledMap<>(f, OpenOption.MUST_BE_NEW, comp, encoder, decoder);
    for (Entry<K, V> ent : entries()) {
      ret.ioSet(ent.getKey(), ent.getValue());
    }
    return ret;
  }

  @Override
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  @Override
  public V get(Object key) {
    try {
      return ioGet(key);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Unchecked verson of ioSet()
   * @param key key value
   * @param value value to associate with key
   * @return true if it replaced a value
   */
  public boolean set(K key, V value) {
    try {
      return ioSet(key, value);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  @Override
  public V put(K key, V value) {
    try {
      return ioGetSet(key, value);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public V remove(Object key) {
    try {
      return ioUnset((K) key);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  @Override
  public synchronized void clear() {
    map.keySet().forEach(this::remove);
  }

  @Override
  public synchronized V putIfAbsent(K key, V value) {
    return super.putIfAbsent(key, value);
  }

  @Override
  public synchronized boolean remove(Object key, Object value) {
    return super.remove(key, value);
  }

  @Override
  public synchronized boolean replace(K key, V oldValue, V newValue) {
    return super.replace(key, oldValue, newValue);
  }

  @Override
  public synchronized V replace(K key, V value) {
    return super.replace(key, value);
  }

  @Override
  public synchronized V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    return super.computeIfAbsent(key, mappingFunction);
  }

  @Override
  public synchronized V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return super.computeIfPresent(key, remappingFunction);
  }

  @Override
  public synchronized V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return super.compute(key, remappingFunction);
  }

  @Override
  public synchronized V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    return super.merge(key, value, remappingFunction);
  }

  @Override
  public String toString() {
    return "ChiseldMap{ file=" + file + " ,size=" + size() + " }";
  }

}

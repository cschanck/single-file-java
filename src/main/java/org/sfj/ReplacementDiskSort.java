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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.singletonList;

/**
 * External merge sort implementation. Mostly as Knuth describes, at least the
 * run generation phase. Uses the replacement selection method to generate runs
 * of sorted elements from an unsorted source N at a time, then merges them M
 * at a time.
 * <p>The input and output is delegated to a pair of interfaces, ExternalIterator,
 * and ExternalAppender. You actually use the class by providing callbacks
 * that make arbitrary iterators and appenders, which are then used read and write
 * your subclass of Element. SO if you have binary ints, fine. Strings, fine. Just
 * provide the appender/iterators to make it happen.
 * @param <E> element subclass
 *
 * @author cschanck
 */
public class ReplacementDiskSort<E extends ReplacementDiskSort.Element> {

  public static class PassInfo {
    private final int pass;
    private final long runTimeMS;
    private final List<File> srcFiles;
    private final List<File> destFiles;
    private final List<Long> runCounts;

    public PassInfo(int pass, List<File> srcFiles, List<File> destFiles, List<Long> destCounts, long runTimeMS) {
      this.pass = pass;
      this.srcFiles = srcFiles;
      this.destFiles = destFiles;
      this.runCounts = destCounts;
      this.runTimeMS = runTimeMS;
    }

    public int getPass() {
      return pass;
    }

    public long getRunTimeMS() {
      return runTimeMS;
    }

    public List<File> getSrcFiles() {
      return srcFiles;
    }

    public List<File> getDestFiles() {
      return destFiles;
    }

    public List<Long> getRunCounts() {
      return runCounts;
    }

    @Override
    public String toString() {
      return "PassInfo{" +
             "pass=" +
             pass +
             ", runTimeMS=" +
             runTimeMS +
             ", srcFiles=" +
             srcFiles +
             ", destFiles=" +
             destFiles +
             ", runCounts=" +
             runCounts +
             '}';
    }
  }

  /**
   * This is the class you need to subclass; you will provide
   * file iterators (readers) and file appenders for your subclass.
   * <p>Store anything as the data object, but you need to render it
   * to and from disk.
   */
  public static class Element {
    private final Object data;
    private int run = 0;

    public Element(Object data) {
      this.data = data;
    }

    public Object getData() {
      return data;
    }

    protected int getRun() {
      return run;
    }

    void setRun(int run) {
      this.run = run;
    }

    @Override
    public String toString() {
      return "Element{" + "data=" + data + ", run=" + run + '}';
    }
  }

  /**
   * An external iterator. Continues to provide data until
   * the next() method return null. Providers should take care to close
   * any underlying resource when returning null.
   * @param <EE> Element subclass
   */
  public interface ExternalIterator<EE extends Element> {
    EE next() throws IOException;
  }

  /**
   * An external appender. Appends Elements until the close
   * methodis called explicitly.
   * @param <EE> Element subclass
   */
  public interface ExternalAppender<EE extends Element> {
    void append(EE elem) throws IOException;

    void close();
  }

  /**
   * Make an iterator for a specific file.
   * @param <EE> Element subclass
   */
  @FunctionalInterface
  public interface IterMaker<EE extends Element> {
    ExternalIterator<EE> make(File f) throws IOException;
  }

  /**
   * Make an appender for a specific file.
   * @param <EE> Element subclass
   */
  @FunctionalInterface
  public interface AppenderMaker<EE extends Element> {
    ExternalAppender<EE> make(File f) throws IOException;
  }

  private final AppenderMaker<E> appenderMaker;
  private final Comparator<E> comp;
  private final boolean deleteFiles;
  private final IterMaker<E> iteratorMaker;
  private final AtomicInteger filenameCounter = new AtomicInteger(0);
  protected File workDirectory;
  private final List<PassInfo> runPassInfo = new ArrayList<>();
  private PrintStream verbose = System.out;

  /**
   * Constructor.
   * @param iteratorMaker maker for iterator.
   * @param appenderMaker maker for appender
   * @param comp Comparator for your subclass of Element
   * @param deleteFiles delete the intermediate files as you go
   */
  public ReplacementDiskSort(IterMaker<E> iteratorMaker,
                             AppenderMaker<E> appenderMaker,
                             Comparator<E> comp,
                             boolean deleteFiles) {

    this.iteratorMaker = iteratorMaker;
    this.appenderMaker = appenderMaker;
    this.comp = (c1, c2) -> {
      int ret = Integer.compare(c1.getRun(), c2.getRun());
      if (ret == 0) { return comp.compare(c1, c2); }
      return ret;
    };
    this.deleteFiles = deleteFiles;
  }

  /**
   * Single method to do a single run.
   * @param iteratorMaker maker for iterator.
   * @param appenderMaker maker for appender
   * @param comp Comparator for your subclass of Element
   * @param deleteFiles delete the intermediate files as you go
   * @param src source file
   * @param maxElementsForRuns max number of elements to have in memory for
   * generating initial run files. This controls how much memory the sort takes,
   * as it includes maxElementsForRuns element overhead, plus 1X the buffering needed
   * for an appender, and 1X the buffering needed for an iterator.
   * @param maxElementsForMerges max number of elements to have in memory for
   * merge passes. This controls how much memory the sort takes,
   * as it includes maxElementsForRuns element overhead, plus 1X the buffering needed
   * for an iterator, and maxElementsForMerges X the buffering needed for an iterator.
   * So the merge pass, depending on the buffering you do in your appender, can
   * have a bigger footprint if you are not careful.
   * @param dest destination file. Cannot be the same as source file.
   * @param workDir working directory
   * @param <E> Element subclass
   * @return the disk sort object used for the sort
   * @throws IOException on exception
   */
  public static <E extends Element> ReplacementDiskSort<E> runOnce(IterMaker<E> iteratorMaker,
                                                                   AppenderMaker<E> appenderMaker,
                                                                   Comparator<E> comp,
                                                                   boolean deleteFiles,
                                                                   File src,
                                                                   int maxElementsForRuns,
                                                                   int maxElementsForMerges,
                                                                   File dest,
                                                                   File workDir) throws IOException {
    ReplacementDiskSort<E> ret = new ReplacementDiskSort<>(iteratorMaker, appenderMaker, comp, deleteFiles);
    ret.run(src, maxElementsForRuns, maxElementsForMerges, dest, workDir);
    return ret;
  }

  public ReplacementDiskSort<E> setVerbose(PrintStream verbose) {
    this.verbose = verbose;
    return this;
  }

  private void verbose(String fmt, Object... args) {
    if (verbose != null) {
      verbose.println(String.format(fmt, args));
    }
  }

  public synchronized void run(File src,
                               int maxElementsForRuns,
                               int maxElementsForMerges,
                               File dest,
                               File workingDirectory) throws IOException {
    if (!src.exists() || !src.canRead()) {
      throw new IOException("Can't read source file: [" + src + "]");
    }
    if (dest.exists()) {
      throw new IOException("Can't write to dest file: [" + dest + "]");
    }
    if (!workingDirectory.exists() || !workingDirectory.canWrite() || !workingDirectory.isDirectory()) {
      throw new IOException(
        "Can't write to working directory/does not exist/not directory: [" + workingDirectory + "]");
    }
    this.workDirectory = workingDirectory;

    List<File> current = makeRuns(src, maxElementsForRuns);
    List<File> next = new ArrayList<>();

    int pass = 1;

    while (current.size() > 1) {
      while (!current.isEmpty()) {
        List<File> subFiles;
        if (current.size() < maxElementsForMerges) {
          subFiles = current;
        } else if (current.size() < 2 * maxElementsForMerges) {
          subFiles = current.subList(0, current.size() / 2);
        } else {
          subFiles = current.subList(0, maxElementsForMerges);
        }
        File interim = passFile(pass);
        mergePass(pass++, subFiles, interim);
        subFiles.clear();
        next.add(interim);
      }
      current = next;
      next = new ArrayList<>();
    }

    Files.move(current.get(0).toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
    verbose("Sort complete. %d items. Elapsed time: %dms ", runPassInfo.get(runPassInfo.size() - 1).runCounts.get(0),
      runPassInfo.stream().mapToLong(rp -> rp.runTimeMS).sum());
  }

  private File passFile(int pass) {
    return new File(workDirectory, "pass-" + pass + "-" + filenameCounter.getAndIncrement());
  }

  protected List<File> makeRuns(File src, int maxElementsForRuns) throws IOException {
    verbose("Pass 0: Generating Runs...");
    PriorityQueue<E> q = new PriorityQueue<>(maxElementsForRuns, this.comp);
    long msStart = System.currentTimeMillis();

    ExternalIterator<E> elements = iteratorMaker.make(src);

    ArrayList<File> files = new ArrayList<>();

    // fill the queue first. all pass 0.
    for (int i = 0; i < maxElementsForRuns; i++) {
      E p = elements.next();
      if (p != null) {
        q.add(p);
      } else {
        break;
      }
    }

    int currentRun = 0;
    File f = passFile(0);
    ExternalAppender<E> output = makeAppender(f);
    files.add(f);
    int count = 0;
    boolean doneReading = false;
    ArrayList<Long> runCounts = new ArrayList<>();

    while (!q.isEmpty()) {
      E val = q.poll();
      // if no more from this run, roll run file
      if (val.getRun() != currentRun) {
        runCounts.add((long) count);
        output.close();
        f = passFile(0);
        ++currentRun;
        output = makeAppender(f);
        files.add(f);
        verbose("Pass 0: Generated run %d with %d elements...", currentRun, count);
        count = 0;
      }
      // write it
      output.append(val);
      count++;
      // if we have another
      if (!doneReading) {
        E newVal = elements.next();
        if (newVal != null) {
          newVal.setRun(currentRun);
          // if it is out of order wrt last written value
          if (comp.compare(newVal, val) < 0) {
            // future run
            newVal.setRun(currentRun + 1);
          }
          // add new one back in.
          q.add(newVal);
        } else {
          doneReading = true;
        }
      }
    }
    verbose("Pass 0: Generated run %d with %d elements...", currentRun, count);
    runCounts.add((long) count);

    output.close();
    long tookMS = System.currentTimeMillis() - msStart;
    this.runPassInfo.add(new PassInfo(0, singletonList(src), new ArrayList<>(files), runCounts, tookMS));
    return files;
  }

  public List<PassInfo> getPassInfo() {
    return Collections.unmodifiableList(this.runPassInfo);
  }

  private ExternalAppender<E> makeAppender(File f) throws IOException {
    if (f.exists()) {
      throw new IOException("File: " + f + " exists; expected it to be missing");
    }
    return appenderMaker.make(f);
  }

  private void writeFully(FileChannel ch, ByteBuffer slice) throws IOException {
    while (slice.hasRemaining()) {
      ch.write(slice);
    }
  }

  private class FileHead {
    private final ExternalIterator<E> iter;
    private E next;

    public FileHead(File f) throws IOException {
      this.iter = iteratorMaker.make(f);
      this.next = iter.next();
    }

    public boolean isDone() {
      return next == null;
    }

    public E pullElement() throws IOException {
      E ret = next;
      if (ret != null) {
        next = iter.next();
      }
      return ret;
    }
  }

  protected File mergePass(int pass, List<File> inputFiles, File dest) throws IOException {
    verbose("Merge pass %d: for %s...", pass, inputFiles);
    long startMS = System.currentTimeMillis();
    PriorityQueue<FileHead> q = new PriorityQueue<>((c1, c2) -> comp.compare(c1.next, c2.next));
    for (File file : inputFiles) {
      FileHead head = new FileHead(file);
      q.add(head);
    }
    long cnt = 0;
    ExternalAppender<E> output = makeAppender(dest);
    while (!q.isEmpty()) {
      FileHead n = q.poll();
      E elem = n.pullElement();
      if (elem != null) {
        cnt++;
        output.append(elem);
      }
      if (!n.isDone()) {
        q.add(n);
      }
    }
    output.close();
    long tookMS = System.currentTimeMillis() - startMS;
    if (deleteFiles) {
      for (File file : inputFiles) {
        file.delete();
      }
    }
    PassInfo pi = new PassInfo(pass, new ArrayList<>(inputFiles), singletonList(dest), singletonList(cnt), tookMS);
    runPassInfo.add(pi);
    verbose("Merge pass %d: completed: %s elements in %dms", pass, pi.runCounts.get(0), pi.runTimeMS);
    return dest;
  }
}

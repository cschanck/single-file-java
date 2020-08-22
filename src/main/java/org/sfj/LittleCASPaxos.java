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

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * <p>This is a super simple Single-Decree Paxos implementation as outlined in this
 * paper https://arxiv.org/abs/1802.07000 . This allows for consensus mutation
 * of an arbitrary set of key/values. In this case, keys are strings. While
 * it is simple, it is pretty cool and does the job if you need to prototype
 * consensus. For an earlier discussion of the same paper's impl, the one I originally
 * did the impl from, see this (and associated) link:
 * http://rystsov.info/2015/09/16/how-paxos-works.html With all due respect the LL,
 * these sources are much more digestible than the original paper.
 *
 * <p>You have to provide impls for the Network and the Storage, as well as the Node
 * specifier. All of the KV, Ballot, and message implementations should be Serializable
 * for simple usage.
 *
 * <p>It's notoriously hard to get Paxos of any flavor right, though SDP is
 * a lot simpler than any of the Multi-Paxos algorithms. And testing is hard.
 * So it is even money there is a bug here and there, more eyes will help.
 * Still, should be close.
 *
 * <p>As a consequence of SDP, you'll note you provide a transform function for
 * the value of key; to "read" a value, you need use the identity function, and
 * run the full 2 stage paxos round, else you lose linearizability. Note that
 * we do none of the optimizations outlined in the paper.
 *
 * <p>In this case, each node is a {@link LittleCASPaxos} object. If you wanted a remote
 * client, then use some protocol to remotely invoke the
 * {@link #paxos(String, Function, int, long, TimeUnit)} method and return the
 * result. Note that in SDP, you can start a round from any node, there are no
 * distinguished nodes.
 *
 * <p>Note also that there is no enumeration/iteration; traversal like this
 * is not a natural fit for CASPaxos, and would need further thought. With
 * a sorted storage, you could do range queries with paxos rounds if you
 * wanted, but thats more than I want to do.
 *
 * <p>A more expansive impl could do lots more, like formalize adding and removing nodes
 * (see the paper for how this proceeds), turning single operations into sequences
 * of mutations, iteration, sloppy reads, etc. Lots of directions to go.
 *
 * <p>It would be interesting to do a single file Raft impl, but that's actually
 * a lot more complicated, at least so it seems, because log-based consensus
 * just seems heavier.
 *
 * @author cschanck
 */
public class LittleCASPaxos {
  /**
   * Node interface. Impl as needed.
   */
  public interface Node {
    int getNodeID();
  }

  /**
   * Immutable ballot for Paxos rounds. In the face of a conflict, increment mightily.
   * For normal ops, increment tiny. Node's override tiny in ordering.
   */
  public static class Ballot implements Serializable, Comparable<Ballot> {
    public static Ballot MIN = new Ballot(0, 0, 0);
    private final int mighty;
    private final int nodeID;
    private final int tiny;

    public Ballot(int mighty, int nodeID, int tiny) {
      this.mighty = mighty;
      this.nodeID = nodeID;
      this.tiny = tiny;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Ballot ballot = (Ballot) o;
      return mighty == ballot.mighty && nodeID == ballot.nodeID && tiny == ballot.tiny;
    }

    @Override
    public int hashCode() {
      return Objects.hash(mighty, nodeID, tiny);
    }

    @Override
    public int compareTo(Ballot o) {
      int ret = Integer.compare(mighty, o.mighty);
      if (ret == 0) {
        ret = Integer.compare(nodeID, o.nodeID);
        if (ret == 0) {
          ret = Integer.compare(tiny, o.tiny);
        }
      }
      return ret;
    }

    public Ballot bigger(Node node) {
      if (node.getNodeID() >= nodeID) {
        return incrementTiny(node);
      }
      return incrementMighty(node);
    }

    public Ballot incrementTiny(Node node) {
      if (tiny == Integer.MAX_VALUE) {
        return incrementMighty(node);
      }
      return new Ballot(mighty, node.getNodeID(), tiny + 1);
    }

    public Ballot incrementMighty(Node node) {
      return new Ballot(mighty + 1, node.getNodeID(), 0);
    }

    @Override
    public String toString() {
      return "Ballot{" + mighty + ":" + nodeID + ":" + tiny + '}';
    }
  }

  /**
   * Key and value with last modification ballot. Used in comm between
   * nodes, as well as storage.
   */
  public static class KV implements Serializable {
    private Ballot ballot;
    private String key;
    private Object val;

    public KV() {
    }

    KV(Ballot ballot, String key, Object val) {
      this.ballot = ballot;
      this.key = key;
      this.val = val;
    }

    public Ballot getBallot() {
      return ballot;
    }

    public String getKey() {
      return key;
    }

    public Object getVal() {
      return val;
    }

    @Override
    public String toString() {
      return "KV{" + "ballot=" + ballot + ", key=" + key + ", val=" + val + '}';
    }
  }

  /**
   * Network interface. Need to count the nodes, and send a blast message out.
   */
  interface Network {

    /**
     * All the nodes in the network.
     *
     * @return all the nodes in the network, regardless as to whether they
     * are up/reachable/etc.
     */
    List<Node> getAllNodes();

    /**
     * Send the prep message to all the nodes. Return when either a) you
     * get the minResponse number of good responses (as indicated by the
     * goodTest predicate), or return as soon as you receive a message with a
     * !goodTest message. Or return when the timeout expires.
     *
     * @param prep prepare message
     * @param minResponse minimum number of good responses for return
     * @param goodTest test as to whether the result is good
     * @param roundTimeout timout
     * @param timeoutUnits timout units
     * @return list of results from various nodes.
     */
    List<RoundStepResult> sendAll(Prepare prep,
                                  int minResponse,
                                  Predicate<RoundStepResult> goodTest,
                                  long roundTimeout,
                                  TimeUnit timeoutUnits);

    /**
     * Send the accept message to all the nodes. Return when either a) you
     * get the minResponse number of good responses (as indicated by the
     * goodTest predicate), or return as soon as you receive a message with a
     * !goodTest message. Or return when the timeout expires.
     *
     * @param accept prepare message
     * @param minResponse minimum number of good responses for return
     * @param goodTest test as to whether the result is good
     * @param roundTimeout timout
     * @param timeoutUnits timout units
     * @return list of results from various nodes.
     */
    List<RoundStepResult> sendAll(Acceptance accept,
                                  int minResponse,
                                  Predicate<RoundStepResult> goodTest,
                                  long roundTimeout,
                                  TimeUnit timeoutUnits);

  }

  /**
   * Durable storage for KV. Also provides locks for a key, gives you the max
   * ballot currently stored, and manages trying to promise for a key. Between
   * the locking and promises, access can be striped. Poll returns null if not
   * stored; get always returns a valid kv with null value if it is not there.
   * Deleted values are represented as keys with null values; storing a null value
   * deletes it. Deletion is a harder problem in CASPaxos and requires extra processing
   * to accomplish. We demur in this impl.
   */
  interface Storage {
    /**
     * Return lock for this key.
     *
     * @param key key
     * @return lock
     */
    Lock lockFor(String key);

    /**
     * Get the max durable ballot for any given key.
     * Intuitively, this the max on disk ballot for all KV's persisted in the store
     * for this node when it started, compared to the max ballot stored since then.
     * <p>So it is incremental maintenance only when the node is running.
     *
     * @return max durable ballot.
     */
    Ballot getMaxBallot();

    /**
     * Poll for a value. (use under lock for this key)
     *
     * @param key key
     * @return null if not existent, or the kv value in storage
     */
    KV poll(String key);

    /**
     * Get the value. (use under lock for this key)
     *
     * @param key key
     * @return return the value, if does not exist, return null valued,
     * min balllot KV
     */
    KV get(String key);

    /**
     * Get the curent promise for this key
     *
     * @param key key
     * @return promise ballot
     */
    Ballot getPromise(String key);

    /**
     * Attempt to set promise for this key (use under lock for this key)
     * Store the promise transiently, but not durably.
     *
     * @param key key
     * @param ballot ballot to promise
     * @return if the ballot is greater than the current promise for this key
     */
    boolean promise(String key, Ballot ballot);

    /**
     * Store this kv. (use under lock for this key)
     *
     * @param kv key value to store
     */
    void store(KV kv);
  }

  /**
   * Result message. True or false, and the value in question.
   */
  public static class RoundStepResult implements Serializable {
    private boolean ok;
    private KV kv;

    public RoundStepResult() {
    }

    public RoundStepResult(boolean ok, KV kv) {
      this.ok = ok;
      this.kv = kv;
    }

    public boolean isOk() {
      return ok;
    }

    public KV getKV() {
      return kv;
    }

    @Override
    public String toString() {
      return "RoundResult{" + "ok=" + ok + ", kv=" + kv + '}';
    }
  }

  /**
   * Proposal message. Ballot and key.
   */
  public static class Prepare implements Serializable {
    private Ballot ballot;
    private String key;

    public Prepare() {
    }

    public Prepare(Ballot ballot, String key) {
      this.ballot = ballot;
      this.key = key;
    }

    public Ballot getBallot() {
      return ballot;
    }

    public String getKey() {
      return key;
    }

    @Override
    public String toString() {
      return "Prepare{" + "ballot=" + ballot + ", key='" + key + '\'' + '}';
    }
  }

  /**
   * Acceptance message. Just the KV to accept.
   */
  public static class Acceptance implements Serializable {
    private KV kv;

    public Acceptance() {
    }

    public Acceptance(KV kv) {
      this.kv = kv;
    }

    public KV getKV() {
      return kv;
    }

    @Override
    public String toString() {
      return "Acceptance{" + "kv=" + kv + '}';
    }
  }

  /**
   * Result codes, blah.
   */
  public enum PaxosResult {
    OK,
    CONFLICT,
    TIMEOUT
  }

  /**
   * Result of a paxos round (prep/accept).
   */
  public static class RoundResult implements Serializable {
    private final PaxosResult result;
    private final int responses;
    private final KV kv;

    public RoundResult(PaxosResult res, KV kv, int responses) {
      this.result = res;
      this.kv = kv;
      this.responses = responses;
    }

    /**
     * Number of nodes that responded.
     *
     * @return count of responding nodes
     */
    public int getResponses() {
      return responses;
    }

    /**
     * Result code
     *
     * @return OK for success, TIMEOUT/CONFLICT on failure
     */
    public PaxosResult getResult() {
      return result;
    }

    /**
     * Key Value object on success, conficting value on conflict.
     *
     * @return key value
     */
    public KV getKV() {
      return kv;
    }

    @Override
    public String toString() {
      return "RoundResult{" + "result=" + result + ", responses=" + responses + ", kv=" + kv + '}';
    }
  }

  private final Network net;
  private final Node me;
  private final Storage storage;
  private volatile Ballot currentBallot;

  /**
   * <p>Create a paxos node. You need to provide a network, which node this is,
   * and a storage component. Generally, you invoke by calling
   * {@link #paxos(String, Function, int, long, TimeUnit)}; this will send prepare
   * messages to each node, and then based on that send accept messages to
   * everyone.
   * <p>The network needs to process Prepare/Accept messages and process them
   * on the local node by calling {@link #processPrepare(Prepare, Consumer)}
   * and {@link #processAcceptance(Acceptance, Consumer)}
   *
   * @param net Network
   * @param me This node
   * @param storage Storage provider.
   */
  public LittleCASPaxos(Network net, Node me, Storage storage) {
    this.net = net;
    this.me = me;
    this.storage = storage;
    this.currentBallot = storage.getMaxBallot().incrementTiny(me);
  }

  /**
   * Call this to process a Prepare message. Response is returned via the
   * Consumer.
   *
   * @param prep Prepare message
   * @param response consumer for response.
   */
  public void processPrepare(Prepare prep, Consumer<RoundStepResult> response) {
    Lock lock = storage.lockFor(prep.key);
    lock.lock();
    try {
      KV is = storage.get(prep.key);
      if (storage.promise(prep.key, prep.ballot)) {
        response.accept(new RoundStepResult(true, is));
      } else {
        response.accept(new RoundStepResult(false, is));
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Call this process an Acceptance message. Result is passed back via the
   * consumer.
   *
   * @param acc Acceptance message
   * @param response Response consumer
   */
  public void processAcceptance(Acceptance acc, Consumer<RoundStepResult> response) {
    Lock lock = storage.lockFor(acc.kv.key);
    lock.lock();
    try {
      if (acc.kv.ballot.compareTo(storage.getPromise(acc.kv.key)) >= 0) {
        // try and promise
        storage.promise(acc.kv.key, acc.kv.ballot);
        // cool, accept the value
        storage.store(acc.kv);
        response.accept(new RoundStepResult(true, acc.kv));
      } else {
        // conflict
        response.accept(new RoundStepResult(false, storage.poll(acc.kv.key)));
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Actual entry point to run a paxos round. Will do prepare and accept steps,
   * for a single key.
   *
   * @param key key in question
   * @param transform transform to apply to current value
   * @param quorum quorum for success
   * @param roundTimeout timeout for each of the stages
   * @param units timout units
   * @return RoundResult with values.
   */
  public synchronized RoundResult paxos(String key,
                                        Function<Object, Object> transform,
                                        int quorum,
                                        long roundTimeout,
                                        TimeUnit units) {
    // new ballot
    currentBallot = currentBallot.incrementTiny(me);
    Ballot next = currentBallot;

    // prepare message
    Prepare prep = new Prepare(next, key);

    // send to everyone, wait for min number of ok responses, within timeout
    List<RoundStepResult> prepResults = net.sendAll(prep, quorum, RoundStepResult::isOk, roundTimeout, units);

    int gCount = (int) prepResults.stream().filter(RoundStepResult::isOk).count();
    // if not enough, lose, fail, conflict.

    if (gCount < quorum) {
      // roll my ballot a lot to have a better shot to overcome the conflict.
      currentBallot = currentBallot.incrementMighty(me);
      return badResult(prepResults);
    }

    // has to be at least 1. Get the max key value, it's the consensus basis.
    KV
      max =
      prepResults.stream()
        .filter(RoundStepResult::isOk)
        .map(RoundStepResult::getKV)
        .max(Comparator.comparing(KV::getBallot))
        .get();

    // apply change transform
    KV newKV = new KV(next, max.key, transform.apply(max.val));

    // acceptance message
    Acceptance acc = new Acceptance(newKV);

    // send to everyone, wait for min number of ok responses, within timeout
    List<RoundStepResult> accResults = net.sendAll(acc, quorum, RoundStepResult::isOk, roundTimeout, units);

    gCount = (int) accResults.stream().filter(RoundStepResult::isOk).count();

    // if below quorum, fail, either timeout or conflict, oh well.
    if (gCount < quorum) {
      // roll my ballot a lot to have a better shot to overcome the conflict.
      currentBallot = currentBallot.incrementMighty(me);
      return badResult(prepResults);
    }

    // cool, it worked, return consensus value
    return new LittleCASPaxos.RoundResult(PaxosResult.OK, newKV, gCount);

  }

  private RoundResult badResult(List<RoundStepResult> results) {
    Optional<RoundStepResult>
      topBad =
      results.stream().filter(rss -> !rss.isOk()).max(Comparator.comparing(o -> o.getKV().getBallot()));
    return topBad.map(result -> new RoundResult(PaxosResult.CONFLICT, result.getKV(), results.size()))
             .orElseGet(() -> new RoundResult(PaxosResult.TIMEOUT, null, results.size()));
  }
}

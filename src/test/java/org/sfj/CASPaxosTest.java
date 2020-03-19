package org.sfj;

import org.junit.Test;
import org.sfj.CASPaxos.Ballot;
import org.sfj.CASPaxos.Node;
import org.sfj.CASPaxos.RoundStepResult;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.sfj.CASPaxos.Acceptance;
import static org.sfj.CASPaxos.KV;
import static org.sfj.CASPaxos.Network;
import static org.sfj.CASPaxos.Prepare;
import static org.sfj.CASPaxos.Storage;

public class CASPaxosTest {
  enum State {
    WORKING,
    TIMEOUT,
    FAIL
  }

  // stupid perfect network via callbacks, with disruption
  class CallbackNetwork implements Network {

    private final Function<Node, CASPaxos> paxoses;
    private final ExecutorService pool;
    private final ScheduledExecutorService sched;
    private List<Node> allNodes;
    private Map<Node, State> prepStates = new HashMap<>();
    private Map<Node, State> accStates = new HashMap<>();

    public CallbackNetwork(Function<Node, CASPaxos> paxoses, Node... nodes) {
      this.paxoses = paxoses;
      this.allNodes = Arrays.asList(nodes);
      this.pool = Executors.newCachedThreadPool();
      this.sched = Executors.newScheduledThreadPool(1);
      setAllWorking();
    }

    void setPrepState(Node n, State state) {
      prepStates.put(n, state);
    }

    void setAccState(Node n, State state) {
      accStates.put(n, state);
    }

    void setAllWorking() {
      allNodes.forEach(n -> prepStates.put(n, State.WORKING));
      allNodes.forEach(n -> accStates.put(n, State.WORKING));
    }

    @Override
    public List<Node> getAllNodes() {
      return allNodes;
    }

    private List<RoundStepResult> sendMsgAll(boolean prepare,
                                             Map<Node, State> states,
                                             Object msg,
                                             int minResponse,
                                             Predicate<RoundStepResult> goodTest,
                                             long roundTimeout,
                                             TimeUnit timeoutUnits) {
      List<RoundStepResult> res = new CopyOnWriteArrayList<>();
      CountDownLatch latch = new CountDownLatch(allNodes.size());
      AtomicInteger goodCount = new AtomicInteger(0);
      for (Node n : allNodes) {
        State state = states.get(n);
        switch (state) {
          case FAIL:
            // return a bad result
            res.add(new RoundStepResult(false, null));
            break;
          case TIMEOUT:
            // do nothing, simulate a timeout
            break;
          case WORKING:
            // do the job
            pool.submit(() -> {
              try {
                CASPaxos p = paxoses.apply(n);
                if (p != null) {
                  Consumer<RoundStepResult> cb = (r) -> {
                    res.add(r);
                    if (goodTest.test(r) && goodCount.incrementAndGet() >= minResponse) {
                      while (latch.getCount() > 0) {
                        latch.countDown();
                      }
                    }
                  };
                  if (prepare) {
                    p.processPrepare((Prepare) msg, cb);
                  } else {
                    p.processAcceptance((Acceptance) msg, cb);
                  }
                }
              } finally {
                latch.countDown();
              }
            });
            sched.schedule(() -> {
              while (latch.getCount() > 0) {
                latch.countDown();
              }
            }, roundTimeout, timeoutUnits);
            break;
        }
      }
      try {
        latch.await(roundTimeout, timeoutUnits);
      } catch (InterruptedException e) {
      }
      return res;
    }

    @Override
    public List<RoundStepResult> sendAll(Prepare prep,
                                         int minResponse,
                                         Predicate<RoundStepResult> goodTest,
                                         long roundTimeout,
                                         TimeUnit timeoutUnits) {
      return sendMsgAll(true, prepStates, prep, minResponse, goodTest, roundTimeout, timeoutUnits);
    }

    @Override
    public List<RoundStepResult> sendAll(Acceptance accept,
                                         int minResponse,
                                         Predicate<RoundStepResult> goodTest,
                                         long roundTimeout,
                                         TimeUnit timeoutUnits) {
      return sendMsgAll(false, accStates, accept, minResponse, goodTest, roundTimeout, timeoutUnits);
    }
  }

  // ephemeral storage, for real it must be durable. for test, whatevs
  class MapStorage implements Storage {
    private Lock oneLock = new ReentrantLock();
    private volatile Ballot promise = Ballot.MIN;
    private ConcurrentHashMap<String, KV> map = new ConcurrentHashMap<>();

    @Override
    public Lock lockFor(String key) {
      return oneLock;
    }

    @Override
    public Ballot getMaxBallot() {
      return map.values().stream().map(KV::getBallot).max(Comparator.naturalOrder()).orElse(Ballot.MIN);
    }

    @Override
    public Ballot getPromise(String key) {
      return promise;
    }

    @Override
    public KV poll(String key) {
      return map.get(key);
    }

    @Override
    public KV get(String key) {
      KV ret = map.get(key);
      if (ret == null) {
        ret = new KV(Ballot.MIN, key, null);
      }
      return ret;
    }

    @Override
    public boolean promise(String key, Ballot ballot) {
      if (ballot.compareTo(promise) > 0) {
        promise = ballot;
        return true;
      }
      return false;
    }

    @Override
    public void store(KV kv) {
      map.put(kv.getKey(), kv);
    }
  }

  // node, obviously
  static class NodeImpl implements Node {
    private int id;

    public NodeImpl(int id) {
      this.id = id;
    }

    @Override
    public int getNodeID() {
      return id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      NodeImpl node = (NodeImpl) o;
      return id == node.id;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }

  @Test
  public void testSimpleWithFailureNodes() {
    // test a Paxos network
    // 5 node network. Perfect immediate delivery/response
    int N = 5;
    final HashMap<Node, CASPaxos> paxosMap = new HashMap<>();
    final Node[] nodes = new Node[N];
    for (int i = 0; i < N; i++) {
      nodes[i] = new NodeImpl(i);
    }
    CallbackNetwork net = new CallbackNetwork((n) -> paxosMap.get(n), nodes);
    for (int i = 0; i < N; i++) {
      MapStorage stor = new MapStorage();
      CASPaxos paxos = new CASPaxos(net, nodes[i], stor);
      paxosMap.put(nodes[i], paxos);
    }

    // introduce some fail
    net.setAccState(nodes[1], State.FAIL);
    net.setPrepState(nodes[3], State.TIMEOUT);

    int quorum = nodes.length / 2 + 1;
    CASPaxos oneNode = paxosMap.values().iterator().next();

    CASPaxos.RoundResult res = oneNode.paxos("test", addOne(), quorum, 1, TimeUnit.SECONDS);
    assertThat(res.getResult(), is(CASPaxos.PaxosResult.OK));
    assertThat(res.getKV().getVal(), is(1));

    res = oneNode.paxos("test", addOne(), quorum, 1, TimeUnit.SECONDS);
    assertThat(res.getResult(), is(CASPaxos.PaxosResult.OK));
    assertThat(res.getKV().getVal(), is(2));

    res = oneNode.paxos("test", ident(), quorum, 1, TimeUnit.SECONDS);
    assertThat(res.getResult(), is(CASPaxos.PaxosResult.OK));
    assertThat(res.getKV().getVal(), is(2));

    res = oneNode.paxos("test", timesTwo(), quorum, 1, TimeUnit.SECONDS);
    assertThat(res.getResult(), is(CASPaxos.PaxosResult.OK));
    assertThat(res.getKV().getVal(), is(4));

    // introduce more fail
    net.setAccState(nodes[0], State.FAIL);
    net.setAccState(nodes[1], State.FAIL);
    net.setAccState(nodes[4], State.FAIL);

    res = oneNode.paxos("test", ident(), quorum, 1, TimeUnit.SECONDS);
    assertThat(res.getResult(), is(CASPaxos.PaxosResult.TIMEOUT));
  }

  private static Function<Object, Object> addOne() {
    return current -> current == null ? 1 : (int) current + 1;
  }

  private static Function<Object, Object> timesTwo() {
    return current -> current == null ? 1 : (int) current * 2;
  }

  private static Function<Object, Object> ident() {
    return (current) -> current;
  }
}
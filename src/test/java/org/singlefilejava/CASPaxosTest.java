package org.singlefilejava;

import org.junit.Test;
import org.singlefilejava.CASPaxos.Ballot;
import org.singlefilejava.CASPaxos.Node;
import org.singlefilejava.CASPaxos.RoundStepResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.singlefilejava.CASPaxos.Acceptance;
import static org.singlefilejava.CASPaxos.KV;
import static org.singlefilejava.CASPaxos.Network;
import static org.singlefilejava.CASPaxos.Prepare;
import static org.singlefilejava.CASPaxos.Storage;

public class CASPaxosTest {

  // stupid perfect network via callbacks
  class CallbackNetwork implements Network {
    private final Function<Node, CASPaxos> paxoses;
    private List<Node> allNodes;

    public CallbackNetwork(Function<Node, CASPaxos> paxoses, Node... nodes) {
      this.paxoses = paxoses;
      this.allNodes = Arrays.asList(nodes);
    }

    @Override
    public List<Node> getAllNodes() {
      return allNodes;
    }

    @Override
    public List<RoundStepResult> sendAll(Prepare prep,
                                         int minResponse,
                                         Predicate<RoundStepResult> goodTest,
                                         long roundTimeout,
                                         TimeUnit timeoutUnits) {
      List<RoundStepResult> res = new ArrayList<>();
      for (Node allNode : allNodes) {
        CASPaxos p = paxoses.apply(allNode);
        if (p != null) {
          p.processPrepare(prep, (r) -> res.add(r));
        }
      }
      return res;
    }

    @Override
    public List<RoundStepResult> sendAll(Acceptance accept,
                                         int minResponse,
                                         Predicate<RoundStepResult> goodTest,
                                         long roundTimeout,
                                         TimeUnit timeoutUnits) {
      List<RoundStepResult> res = new ArrayList<>();
      for (Node allNode : allNodes) {
        CASPaxos p = paxoses.apply(allNode);
        if (p != null) {
          p.processAcceptance(accept, (r) -> res.add(r));
        }
      }
      return res;
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
    public void promise(String key, Ballot ballot) {
      promise = ballot;
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
  public void testWith3() {
    // test a 3 node Paxos network
    int N = 3;
    final HashMap<Node, CASPaxos> paxosMap = new HashMap<>();
    final Node[] nodes = new Node[N];
    for (int i = 0; i < N; i++) {
      nodes[i] = new NodeImpl(i);
    }
    for (int i = 0; i < N; i++) {
      CallbackNetwork net = new CallbackNetwork((n) -> {
        return paxosMap.get(n);
      }, nodes);
      MapStorage stor = new MapStorage();
      CASPaxos paxos = new CASPaxos(net, nodes[i], stor);
      paxosMap.put(nodes[i], paxos);
    }

    CASPaxos oneNode = paxosMap.values().iterator().next();
    CASPaxos.RoundResult res = oneNode.paxos("test", (current) -> 10, 3 / 2 + 1, 100, TimeUnit.DAYS);
    System.out.println(res);
    res = oneNode.paxos("test", (current) -> current, 3 / 2 + 1, 100, TimeUnit.DAYS);
    System.out.println(res);
  }
}
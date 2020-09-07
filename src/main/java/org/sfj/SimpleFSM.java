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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * So, this is a simple single threaded Finite State Machine driver class.
 * <p>Classically, State S and Event E yield some action, then a transition to State S'.
 * <p>In this impl, the Events are defined in terms of an enum, and the States are
 * created by name via the {@link #state(String)} call; they are managed singletons.
 * For each state you define transition handling for any interesting event types via
 * the State.transition(). You do not have to define every transition; invalid transitions
 * will through {@link TransitionException}.
 * <p>Instead of defining the outcome state when you define the transition, the transition
 * callback is responsible for returning the next state, or null for no change.
 * <p>{@link #begin(State)} sets the intitial state and primes the SM for execution.
 * Then {@link #fire(Enum, Object...)} is called as events are received. Beware, you cannot
 * call fire() from inside a callback; the lock is not reentrant.
 * @param <E> Event enum
 */
public class SimpleFSM<E extends Enum<E>> {

  /**
   * Callback interface for consumption of a event in a state, with payload.
   * @param <EE> event enum
   */
  @FunctionalInterface
  public interface Transition<EE extends Enum<EE>> {
    State<EE> transition(State<EE> currentState, EE event, Object... args) throws TransitionException;
  }

  /**
   * Any exception which occurs in transition processing.
   */
  public static class TransitionException extends Exception {
    public TransitionException() {
    }

    public TransitionException(Throwable cause) {
      super(cause);
    }

    public TransitionException(String message) {
      super(message);
    }
  }

  /**
   * State class. Unique by name.
   * @param <EE> enum for events
   */
  public static class State<EE extends Enum<EE>> implements Comparable<State<EE>> {
    private final String name;
    private final Transition<EE>[] dispatch;
    private final BooleanSupplier frozen;

    State(String name, Transition<EE>[] dispatch, BooleanSupplier frozen) {
      this.name = name;
      this.dispatch = dispatch;
      this.frozen = frozen;
    }

    @Override
    public int compareTo(State<EE> o) {
      return name.compareTo(o.name);
    }

    public State<EE> transition(EE event, Transition<EE> thunk) {
      if (frozen.getAsBoolean()) {
        throw new IllegalStateException("FSM is already frozen, can't define new transition.");
      }
      if (dispatch[event.ordinal()] != null) {
        throw new IllegalStateException("State transition already defined!");
      }
      dispatch[event.ordinal()] = thunk;
      return this;
    }

    @Override
    public String toString() {
      return "State{" + '\'' + name + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) { return true; }
      if (o == null || getClass() != o.getClass()) { return false; }

      State<?> state = (State<?>) o;

      return name.equals(state.name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

  private final StampedLock transitionLock = new StampedLock();
  private final String name;
  private final Class<E> eventClass;
  private final Map<String, State<E>> states = new ConcurrentHashMap<>();
  private final E[] events;
  private volatile State<E> currentState;
  private volatile boolean froze;
  private final AtomicLong transitionAttemptCount = new AtomicLong(0);
  private final AtomicLong transitionNoopCount = new AtomicLong(0);
  private final AtomicLong transitionFailureCount = new AtomicLong(0);
  private E failEvent;
  private Transition<E> onException;
  private final ExecutorService asyncDeliver = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "FSM Callback");
    t.setDaemon(true);
    return t;
  });

  /**
   * Create simple finite state machine driver.
   * @param name name
   * @param eventClass events
   */
  public SimpleFSM(String name, Class<E> eventClass) {
    this.name = name;
    this.eventClass = eventClass;
    this.events = eventClass.getEnumConstants();
  }

  /**
   * Name of this FSM
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Event class.
   * @return event class
   */
  public Class<E> getEventClass() {
    return eventClass;
  }

  /**
   * This is useful in that you can queue an event for later delivery from inside the
   * state machine, as this will deliver the event/args from a seperate thread.
   * @param event event
   * @param args payload
   */
  public void fireAsynchronously(E event, Object... args) {
    asyncDeliver.submit(() -> fire(event, args));
  }

  /**
   * Fire an event. Call the transition callback for the current state, for the given
   * event, with the specified args.
   * @param event event
   * @param args payload
   * @return resultant state
   * @throws TransitionException on any error
   */
  public State<E> fire(E event, Object... args) throws TransitionException {
    checkFroze();
    if (currentState == null) {
      throw new TransitionException("Current state null");
    }
    Transition<E> cb = currentState.dispatch[event.ordinal()];
    if (cb == null) {
      throw new TransitionException("No transition defined for State: " + currentState + " X Event: " + event);
    }
    try {
      transitionAttemptCount.incrementAndGet();
      long stamp = transitionLock.writeLock();
      try {
        State<E> nextState = cb.transition(currentState, event, args);
        if (nextState != null) {
          currentState = nextState;
        } else {
          transitionNoopCount.incrementAndGet();
        }
      } finally {
        transitionLock.unlock(stamp);
      }
      return currentState;
    } catch (TransitionException te) {
      return processFailure(te);
    } catch (Throwable e) {
      return processFailure(new TransitionException(e));
    }
  }

  private State<E> processFailure(TransitionException te) throws TransitionException {
    transitionFailureCount.incrementAndGet();
    if (failEvent == null) {
      throw te;
    }
    State<E> nextState = onException.transition(currentState, failEvent, te);
    if (nextState != null) {
      currentState = nextState;
    }
    return currentState;
  }

  /**
   * Event/transition to call on transition exception. On failure processing
   * a special transition step is called, but no stats are kept. If the exc eption
   * transition throws an exception, it will be thrown to the user as well.
   * @param failEvent event to deliver on transition exception
   * @param onException transition to call
   * @return this fsm
   */
  public SimpleFSM<E> onException(E failEvent, Transition<E> onException) {
    if (failEvent == null) {
      this.failEvent = failEvent;
      this.onException = null;
    } else {
      this.failEvent = failEvent;
      this.onException = Objects.requireNonNull(onException);
    }
    return this;
  }

  /**
   * Prime this FSM for processing; after this, no more transitions can be specified.
   * @param s start state
   */
  public void begin(State<E> s) {
    checkNotFroze();
    this.currentState = s;
    this.froze = true;
  }

  /**
   * Get the current state.
   * @return current state.
   */
  public State<E> getCurrentState() {
    return currentState;
  }

  /**
   * Define a new {@link State} by name, or retrieve the state if it exists.
   * @param name name
   * @return state
   */
  @SuppressWarnings("unchecked")
  public State<E> state(String name) {
    checkNotFroze();
    State<E> ret = states.get(name);
    if (ret == null) {
      Transition<E>[] arr = new Transition[eventClass.getEnumConstants().length];
      ret = new State<>(name, arr, this::isFrozen);
      states.put(name, ret);
    }
    return ret;
  }

  /**
   * Is this FSM frozen and accepting events?
   * @return true if frozen
   */
  public boolean isFrozen() {
    return froze;
  }

  private void checkNotFroze() {
    if (froze) { throw new IllegalStateException("FSM is frozen."); }
  }

  private void checkFroze() {
    if (!froze) { throw new IllegalStateException("FSM is not frozen."); }
  }

  /**
   * Useful dump of FSM definitions.
   * @return multiline string
   */
  public String dumpFSM() {
    StringWriter writer = new StringWriter();
    PrintWriter pw = new PrintWriter(writer);

    List<State<E>> statesInOrder = states.values().stream().sorted().collect(Collectors.toList());
    int stateWidth = statesInOrder.stream().mapToInt(s -> s.name.length()).max().orElse(1);
    int maxEventWidth = Arrays.stream(eventClass.getEnumConstants()).mapToInt(e -> e.name().length()).max().orElse("N/A".length());
    pw.println("SimpleFSM: " + name + " EventClass: " + eventClass.getCanonicalName());
    pw.println();
    pw.print("   " + String.format("%-" + (stateWidth + "State: ".length()) + "s ", ""));
    for (E e : events) {
      pw.print(center(e.name(), maxEventWidth) + " ");
    }
    pw.println();
    statesInOrder.forEach(st -> {
      pw.print("   " + String.format("State: %" + stateWidth + "s ", st.name));
      for (E e : events) {
        pw.print(center(st.dispatch[e.ordinal()] != null ? "X" : "", maxEventWidth) + " ");
      }
      pw.println();
    });
    pw.flush();
    return writer.toString();
  }

  private static String center(String text, int total) {
    int skip = (total - text.length()) / 2;
    int rem = total - skip - text.length();
    if (rem > 0 && skip > 0) {
      return String.format("%" + skip + "s%s%" + rem + "s", "", text, "");
    } else if (rem > 0) {
      return String.format("%s%" + rem + "s", text, "");
    } else if (skip > 0) {
      return String.format("%" + skip + "s%s", "", text);
    }
    return text;
  }

  /**
   * Shutdown this FSM. Does not take lock; clears all records states,
   * sets currentState to null.
   */
  public void shutdown() {
    currentState = null;
    states.clear();
  }

  /**
   * Number of times a transition occurred without changing state.
   * @return count
   */
  public long getTransitionNoopCount() {
    return transitionNoopCount.get();
  }

  /**
   * Number of times a transition was attempted.
   * @return count
   */
  public long getTransitionAttemptCount() {
    return transitionAttemptCount.get();
  }

  /**
   * Number of times a transition resulted in an exception
   * @return count
   */
  public long getTransitionFailureCount() {
    return transitionFailureCount.get();
  }
}

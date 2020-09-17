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

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.sfj.SimpleFSMTest.TrafficLightEvents.GTIMEREXPIRED;
import static org.sfj.SimpleFSMTest.TrafficLightEvents.RTIMEREXPIRED;
import static org.sfj.SimpleFSMTest.TrafficLightEvents.YTIMEREXPIRED;
import static org.sfj.SimpleFSMTest.TurnStileEvents.COIN;
import static org.sfj.SimpleFSMTest.TurnStileEvents.PUSH;

public class SimpleFSMTest {
  enum TurnStileEvents {COIN, PUSH}

  @Test
  public void testTurnStile() throws SimpleFSM.TransitionException {
    // simple turnstile from wikipedia FSM page
    AtomicInteger pushFails = new AtomicInteger(0);
    AtomicInteger pushSuccesses = new AtomicInteger(0);
    AtomicInteger coinWastes = new AtomicInteger(0);
    AtomicInteger coinSuccesses = new AtomicInteger(0);

    SimpleFSM<TurnStileEvents> fsm = new SimpleFSM<>("Turn Stile", TurnStileEvents.class);
    SimpleFSM.State<TurnStileEvents> locked = fsm.state("locked");
    SimpleFSM.State<TurnStileEvents> unlocked = fsm.state("unlocked");

    locked.transition(COIN, (st, e, args) -> {
      coinSuccesses.incrementAndGet();
      return unlocked;
    });

    locked.transition(PUSH, (st, e, args) -> {
      pushFails.incrementAndGet();
      return locked;
    });

    unlocked.transition(COIN, (st, e, args) -> {
      coinWastes.incrementAndGet();
      return unlocked;
    });

    unlocked.transition(PUSH, (st, e, args) -> {
      pushSuccesses.incrementAndGet();
      return locked;
    });

    System.out.println(fsm.dumpFSM());
    fsm.begin(locked);

    assertThat(fsm.getCurrentState(), is(locked));
    fsm.fire(COIN);
    assertThat(fsm.getCurrentState(), is(unlocked));
    fsm.fire(PUSH);
    assertThat(fsm.getCurrentState(), is(locked));
    fsm.fire(PUSH);
    assertThat(fsm.getCurrentState(), is(locked));
    fsm.fire(COIN);
    assertThat(fsm.getCurrentState(), is(unlocked));
    fsm.fire(COIN);
    assertThat(fsm.getCurrentState(), is(unlocked));
    fsm.fire(PUSH);
    assertThat(fsm.getCurrentState(), is(locked));
    assertThat(coinSuccesses.get(), is(2));
    assertThat(coinWastes.get(), is(1));
    assertThat(pushFails.get(), is(1));
    assertThat(pushSuccesses.get(), is(2));
  }

  enum TrafficLightEvents {GTIMEREXPIRED, YTIMEREXPIRED, RTIMEREXPIRED}

  @Test
  public void testTrafficLight() throws SimpleFSM.TransitionException {
    // Simple 3 way american traffic light
    SimpleFSM<TrafficLightEvents> fsm = new SimpleFSM<>("Traffic Light", TrafficLightEvents.class);
    SimpleFSM.State<TrafficLightEvents> red = fsm.state("red");
    SimpleFSM.State<TrafficLightEvents> yellow = fsm.state("yellow");
    SimpleFSM.State<TrafficLightEvents> green = fsm.state("green");

    green.transition(GTIMEREXPIRED, (st, e, args) -> yellow);
    yellow.transition(YTIMEREXPIRED, (st, e, args) -> red);
    red.transition(RTIMEREXPIRED, (st, e, args) -> green);

    System.out.println(fsm.dumpFSM());

    fsm.begin(red); // safety first

    // legal cases:
    assertThat(fsm.getCurrentState(), is(red));
    fsm.fire(RTIMEREXPIRED);
    assertThat(fsm.getCurrentState(), is(green));
    fsm.fire(GTIMEREXPIRED);
    assertThat(fsm.getCurrentState(), is(yellow));
    fsm.fire(YTIMEREXPIRED);
    assertThat(fsm.getCurrentState(), is(red));

    try {
      fsm.fire(GTIMEREXPIRED);
      Assert.fail();
    } catch (SimpleFSM.TransitionException t) {
    }
    assertThat(fsm.getCurrentState(), is(red));

  }
}

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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.lang.reflect.Proxy.newProxyInstance;

/**
 * This is a class providing a simple framework for Proxying an
 * interface with user specified invocation middle. Useful to
 * remote proxy something, as in a poor man's RPC.
 * @author cschanck
 */
public class ProxyMe {
  private ProxyMe() {
  }

  /**
   * Invocation class, just bundles up an invocation id, a method name,
   * and a set of args.Suitable to be passed from a client site
   * through to a remote server site.
   */
  public static class Invocation implements Serializable {
    private long iid;
    private String methodName;
    private Object[] args;

    public Invocation() {
    }

    Invocation(long iid, String name, Object[] args) {
      this.iid = iid;
      methodName = name;
      this.args = args;
    }

    public long getIId() {
      return iid;
    }

    public Object[] getArgs() {
      return args;
    }

    public String getMethodName() {
      return methodName;
    }

    @Override
    public String toString() {
      return "Invocation{" +
             "iid=" +
             iid +
             ", methodName='" +
             methodName +
             '\'' +
             ", args=" +
             Arrays.toString(args) +
             '}';
    }
  }

  /**
   * Return result of an invocation. Suitable to be returned from
   * a server site back to the client. Used to complete an outstanding proxy
   * invocation.
   * @param <R> return type
   */
  public static class InvocationReturn<R> implements Serializable {
    private long iid;
    private R returnValue;
    private Throwable throwable;

    public InvocationReturn() {
    }

    InvocationReturn(long iid, R value, Throwable throwable) {
      this.iid = iid;
      returnValue = value;
      this.throwable = throwable;
    }

    public long getIid() {
      return iid;
    }

    public R getReturnValue() {
      return returnValue;
    }

    public Throwable getThrowable() {
      return throwable;
    }

    @Override
    public String toString() {
      return "InvocationReturn{" + "iid=" + iid + ", returnValue=" + returnValue + ", throwable=" + throwable + '}';
    }
  }

  /**
   * Client side proxy site. Proxies a given interface, manages invocations,
   * passes invocations to designated consumer, manages timeouts.
   * @param <T> client type
   */
  public static class Client<T> {
    private final ScheduledExecutorService timeouts;
    private final Class<? super T> proxyClass;
    private final Consumer<Invocation> outbound;
    private final long timeout;
    private final TimeUnit units;
    private final AtomicLong idGen = new AtomicLong(0);
    private final ConcurrentHashMap<Long, CompletableFuture<?>> pending = new ConcurrentHashMap<>();

    /**
     * Create client proxy site.
     * @param timeouts Pool for timeouts.
     * @param proxyClass Interface to proxy
     * @param outbound Outbound callback
     * @param timeout Timeout duration
     * @param units Timeout units
     */
    public Client(ScheduledExecutorService timeouts,
                  Class<? super T> proxyClass,
                  Consumer<Invocation> outbound,
                  long timeout,
                  TimeUnit units) {
      this.timeouts = timeouts;
      this.proxyClass = proxyClass;
      this.outbound = outbound;
      this.timeout = timeout;
      this.units = units;
    }

    public Class<? super T> getProxyClass() {
      return proxyClass;
    }

    public ScheduledExecutorService getTimeoutPool() {
      return timeouts;
    }

    /**
     * This actually hands you back the proxy class. Multiple calls here
     * create separate proxies, but they all go the same place. Uses the
     * current thread context classloader.
     * @return proxy
     */
    public T clientProxy() {
      return clientProxy(Thread.currentThread().getContextClassLoader());
    }

    /**
     * This actually hands you back the proxy class. Multiple calls here
     * create separate proxies, but they all go the same place.
     * @param loader ClassLoader to use.
     * @return proxy
     */
    @SuppressWarnings( { "unchecked", "raw" })
    public T clientProxy(ClassLoader loader) {
      return (T) newProxyInstance(loader, new Class[] { proxyClass }, (proxy, method, args) -> {
        Invocation iv = new Invocation(idGen.incrementAndGet(), method.getName(), args);
        CompletableFuture<?> fut = new CompletableFuture<>();
        pending.put(iv.iid, fut);
        outbound.accept(iv);
        if (timeout > 0) {
          timeouts.schedule(() -> {fut.completeExceptionally(new TimeoutException());}, timeout, units);
        }
        try {
          return fut.get();
        } catch (ExecutionException e) {
          throw e.getCause();
        } finally {
          pending.remove(iv.iid);
        }
      });
    }

    /**
     * Use to complete the client site invocation with an invocation
     * return.
     * @param ir Invocation return
     * @param <R> return object type
     */
    @SuppressWarnings("unchecked")
    public <R> void complete(InvocationReturn<R> ir) {
      CompletableFuture<R> fut = (CompletableFuture<R>) pending.remove(ir.iid);
      if (fut != null) {
        if (ir.throwable != null) {
          fut.completeExceptionally(ir.throwable);
        } else {
          fut.complete(ir.returnValue);
        }
      }
    }
  }

  /**
   * Server site for proxies.
   * @param <T> server type
   */
  public static class Server<T> {
    private final Class<? super T> proxy;
    private final T instance;
    private final HashMap<String, Method> methods;

    /**
     * Create a server side path to invoke a particular invocation
     * on an instance of the proxy class.
     * @param proxy Proxy class
     * @param instance Instance of proxy class.
     */
    public Server(Class<? super T> proxy, T instance) {
      this.proxy = proxy;
      this.instance = instance;
      methods = new HashMap<>();
      for (Class<?> clz : proxy.getInterfaces()) {
        for (Method method : clz.getDeclaredMethods()) {
          methods.put(method.getName(), method);
        }
      }
      for (Method method : proxy.getDeclaredMethods()) {
        methods.put(method.getName(), method);
      }
    }

    public Class<? super T> getProxy() {
      return proxy;
    }

    public T getInstance() {
      return instance;
    }

    /**
     * Invoke the Invocation on the instance object, returning the
     * InvocationReturn object.
     * @param invocation Invocation
     * @param <R> Return type
     * @return InvocationReturn
     */
    @SuppressWarnings("unchecked")
    public <R> InvocationReturn<R> invoke(Invocation invocation) {
      Method m = methods.get(invocation.methodName);
      R ret = null;
      Throwable throwable = null;
      try {
        ret = (R) m.invoke(instance, invocation.args);
      } catch (Throwable e) {
        throwable = e;
      }
      return new InvocationReturn<>(invocation.iid, ret, throwable);
    }
  }
}

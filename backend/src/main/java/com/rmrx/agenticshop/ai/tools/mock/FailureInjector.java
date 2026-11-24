package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.ToolErrorCode;
import com.rmrx.agenticshop.ai.tools.port.PortException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Decorates any port interface with {@code app.ai.failure-injection}: extra latency on every call, then per call
 * (using the caller's world's seeded random, so a fresh world replays the same sequence) UNAVAILABLE with
 * errorRate, a short sleep and TIMEOUT with timeoutRate, or a malformed result with malformedRate. A malformed
 * result is the real one with its first nullable field (an id, a page's item list, a write's state) nulled, which
 * the tools' own validation rejects.
 */
public final class FailureInjector implements InvocationHandler {

    static final Duration TIMEOUT_SLEEP = Duration.ofMillis(1500);

    private final Object target;
    private final AiProperties.FailureInjection cfg;
    private final MockWorlds worlds;

    private FailureInjector(Object target, AiProperties.FailureInjection cfg, MockWorlds worlds) {
        this.target = target;
        this.cfg = cfg;
        this.worlds = worlds;
    }

    /** The target itself when injection is off, otherwise a proxy around it. */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<T> port, T target, AiProperties.FailureInjection cfg, MockWorlds worlds) {
        if (!cfg.active()) return target;
        return (T) Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[]{port},
                new FailureInjector(target, cfg, worlds));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class || args == null || !(args[0] instanceof AgentContext ctx)) {
            return call(method, args);
        }
        if (!cfg.extraLatency().isZero()) Thread.sleep(cfg.extraLatency());
        double roll = worlds.worldFor(ctx).nextRandom();
        double edge = cfg.errorRate();
        if (roll < edge) {
            throw new PortException(ToolErrorCode.UNAVAILABLE, "upstream service unavailable (injected)");
        }
        edge += cfg.timeoutRate();
        if (roll < edge) {
            Thread.sleep(TIMEOUT_SLEEP);
            throw new PortException(ToolErrorCode.TIMEOUT, "upstream service timed out (injected)");
        }
        edge += cfg.malformedRate();
        Object result = call(method, args);
        return roll < edge ? corrupt(result) : result;
    }

    private Object call(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** The value with its first non-primitive record field nulled; a list with its first element corrupted. */
    static Object corrupt(Object value) {
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return null;
            List<Object> copy = new ArrayList<>(list);
            copy.set(0, corrupt(copy.getFirst()));
            return copy;
        }
        if (value == null || !value.getClass().isRecord()) return null;
        try {
            RecordComponent[] components = value.getClass().getRecordComponents();
            Object[] values = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            boolean nulled = false;
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                values[i] = components[i].getAccessor().invoke(value);
                if (!nulled && !types[i].isPrimitive()) {
                    values[i] = null;
                    nulled = true;
                }
            }
            return value.getClass().getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}

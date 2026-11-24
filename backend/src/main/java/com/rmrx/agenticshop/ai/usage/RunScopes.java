package com.rmrx.agenticshop.ai.usage;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Binds a {@link RunScope} for the duration of a block, using a {@link ScopedValue}. Model and embedding calls made
 * inside the block are billed to that scope.
 *
 * <p>A scoped value is not inherited by tasks submitted to an executor: bind the scope inside the task (e.g. inside
 * the agent turn running on {@code aiExecutor}), not around the submit. Streaming callbacks run on other threads;
 * the chat listener captures the scope at request time, so they need no binding.
 */
public final class RunScopes {

    private static final ScopedValue<RunScope> CURRENT = ScopedValue.newInstance();

    private RunScopes() {
    }

    public static Optional<RunScope> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    public static void run(RunScope scope, Runnable block) {
        ScopedValue.where(CURRENT, scope).run(block);
    }

    public static <T> T get(RunScope scope, Supplier<T> block) {
        return ScopedValue.where(CURRENT, scope).call(block::get);
    }

    /** Like {@link #get} for blocks that throw checked exceptions. */
    public static <T, X extends Throwable> T call(RunScope scope, ScopedValue.CallableOp<T, X> block) throws X {
        return ScopedValue.where(CURRENT, scope).call(block);
    }
}

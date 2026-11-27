package com.rmrx.agenticshop.ai.model;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedByInterruptException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Which model errors are worth another attempt, and how long to wait before it. Transient: rate limits, timeouts,
 * server errors and network failures. Never retried: bad requests, authentication, a missing model, content filters,
 * and an interrupted thread (a stopped run).
 */
@Component
public class ModelRetryPolicy {

    private static final Set<Integer> TRANSIENT_STATUS = Set.of(408, 409, 425, 429, 500, 502, 503, 504);

    private final AiModelProperties.Retry retry;

    public ModelRetryPolicy(AiModelProperties props) {
        this.retry = props.retry();
    }

    public int maxRetries() {
        return Math.max(0, retry.maxRetries());
    }

    public boolean retryable(Throwable error) {
        if (Thread.currentThread().isInterrupted()) return false;
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (interrupted(t) || t instanceof NonRetriableException) return false;
            if (t instanceof HttpException http) return TRANSIENT_STATUS.contains(http.statusCode());
            if (t instanceof RetriableException || t instanceof IOException) return true;
        }
        return false;
    }

    /** The wait before retry number {@code retry} (1-based): initial × 2^(retry-1), capped, ±20% jitter. */
    public Duration backoff(int retryNumber) {
        long base = retry.initialBackoff().toMillis() << Math.min(retryNumber - 1, 20);
        long capped = Math.min(base, retry.maxBackoff().toMillis());
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return Duration.ofMillis(Math.max(0, Math.round(capped * jitter)));
    }

    private static boolean interrupted(Throwable t) {
        return t instanceof InterruptedException || t instanceof ClosedByInterruptException
                || (t instanceof InterruptedIOException && !(t instanceof SocketTimeoutException));
    }
}

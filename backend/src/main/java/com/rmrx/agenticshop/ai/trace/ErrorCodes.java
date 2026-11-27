package com.rmrx.agenticshop.ai.trace;

import com.rmrx.agenticshop.ai.contract.tool.Texts;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.LangChain4jException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Short codes and readable messages for errors written into traces. */
public final class ErrorCodes {

    private static final int MESSAGE_LIMIT = 1000;
    private static final String GRAPH_RUNNER_EXCEPTION = "org.bsc.langgraph4j.GraphRunnerException";

    private ErrorCodes() {
    }

    /**
     * The most telling code in the cause chain: a coded exception's own code, {@code HTTP_<status>}, a specific
     * langchain4j exception (e.g. {@code RateLimitException}), a network error (e.g. {@code ConnectException}), else
     * the class of the error itself (wrappers skipped).
     */
    public static String of(Throwable error) {
        if (error == null) return "UNKNOWN";
        Throwable langchain4j = null;
        Throwable io = null;
        for (Throwable t = error; t != null; t = cause(t)) {
            if (t instanceof Coded coded) return coded.errorCode();
            if (t instanceof HttpException http) return "HTTP_" + http.statusCode();
            if (langchain4j == null && t instanceof LangChain4jException && t.getClass() != LangChain4jException.class) {
                langchain4j = t;
            }
            if (io == null && t instanceof IOException) io = t;
        }
        Throwable best = langchain4j != null ? langchain4j : io != null ? io : unwrap(error);
        return best.getClass().getSimpleName();
    }

    /** The messages of the cause chain without wrappers, outermost first ("A: x <- B: y"), cut to a readable size. */
    public static String message(Throwable error) {
        if (error == null) return null;
        List<String> parts = new ArrayList<>();
        for (Throwable t = unwrap(error); t != null && parts.size() < 5; t = cause(t)) {
            if (isWrapper(t)) continue;
            parts.add(t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
        return Texts.cut(String.join(" <- ", parts), MESSAGE_LIMIT);
    }

    /** The first exception of the given type in the cause chain. */
    public static <T extends Throwable> Optional<T> find(Throwable error, Class<T> type) {
        for (Throwable t = error; t != null; t = cause(t)) {
            if (type.isInstance(t)) return Optional.of(type.cast(t));
        }
        return Optional.empty();
    }

    /** The error without the async and graph wrappers around it. */
    static Throwable unwrap(Throwable error) {
        Throwable t = error;
        while (isWrapper(t) && cause(t) != null) t = cause(t);
        return t;
    }

    /** Exceptions that only carry another one: async wrappers, the graph runner's, a bare RuntimeException(cause). */
    private static boolean isWrapper(Throwable t) {
        return t instanceof CompletionException || t instanceof ExecutionException
                || GRAPH_RUNNER_EXCEPTION.equals(t.getClass().getName())
                || (t.getClass() == RuntimeException.class && t.getCause() != null);
    }

    private static Throwable cause(Throwable t) {
        return t.getCause() == t ? null : t.getCause();
    }

    /** An exception that knows its own trace code. */
    public interface Coded {
        String errorCode();
    }
}

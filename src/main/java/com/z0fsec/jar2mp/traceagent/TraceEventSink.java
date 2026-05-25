package com.z0fsec.jar2mp.traceagent;

import com.z0fsec.jar2mp.core.RuntimeTraceEvent;
import com.z0fsec.jar2mp.core.RuntimeTraceWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class TraceEventSink implements AutoCloseable {
    private static final AtomicReference<TraceEventSink> CURRENT = new AtomicReference<>(disabled());
    private static final ThreadLocal<Integer> SUPPRESSION_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return Integer.valueOf(0);
        }
    };

    private final Path traceFile;
    private final RuntimeTraceWriter writer;
    private final Object lock = new Object();
    private volatile boolean closed;

    private TraceEventSink(Path traceFile) {
        this.traceFile = traceFile;
        this.writer = new RuntimeTraceWriter();
    }

    public static TraceEventSink disabled() {
        return new TraceEventSink(null);
    }

    public static TraceEventSink open(String traceFile) {
        if (traceFile == null || traceFile.trim().isEmpty()) {
            return disabled();
        }
        return new TraceEventSink(Paths.get(traceFile));
    }

    public static void install(TraceEventSink sink) {
        CURRENT.set(sink == null ? disabled() : sink);
    }

    public static TraceEventSink current() {
        return CURRENT.get();
    }

    public boolean isEnabled() {
        return traceFile != null && !closed;
    }

    public static void record(String kind, String owner, String target, String value) {
        TraceEventSink sink = CURRENT.get();
        if (sink != null && !isSuppressed()) {
            sink.write(kind, owner, target, value);
        }
    }

    public void write(String kind, String owner, String target, String value) {
        if (closed || traceFile == null) {
            return;
        }
        RuntimeTraceEvent event = new RuntimeTraceEvent(
                kind,
                owner,
                target,
                value,
                Thread.currentThread().getName(),
                captureStack());
        synchronized (lock) {
            if (closed) {
                return;
            }
            try {
                withSuppression(new TraceWriteTask() {
                    @Override
                    public void run() throws IOException {
                        writer.append(traceFile, event);
                    }
                });
            } catch (IOException e) {
                closed = true;
            }
        }
    }

    private List<String> captureStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        List<String> frames = new ArrayList<>();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.startsWith("com.z0fsec.jar2mp.traceagent.")
                    || className.startsWith("net.bytebuddy.")) {
                continue;
            }
            frames.add(className + "." + element.getMethodName() + ":" + element.getLineNumber());
            if (frames.size() >= 12) {
                break;
            }
        }
        return frames;
    }

    private static boolean isSuppressed() {
        return SUPPRESSION_DEPTH.get().intValue() > 0;
    }

    private static void withSuppression(TraceWriteTask task) throws IOException {
        SUPPRESSION_DEPTH.set(Integer.valueOf(SUPPRESSION_DEPTH.get().intValue() + 1));
        try {
            task.run();
        } finally {
            int depth = SUPPRESSION_DEPTH.get().intValue() - 1;
            if (depth <= 0) {
                SUPPRESSION_DEPTH.remove();
            } else {
                SUPPRESSION_DEPTH.set(Integer.valueOf(depth));
            }
        }
    }

    private interface TraceWriteTask {
        void run() throws IOException;
    }

    @Override
    public void close() {
        closed = true;
        CURRENT.compareAndSet(this, disabled());
    }
}

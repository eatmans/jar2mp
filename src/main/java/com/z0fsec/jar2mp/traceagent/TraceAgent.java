package com.z0fsec.jar2mp.traceagent;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.jar.JarFile;

public class TraceAgent {

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        try {
            File agentJar = resolveAgentJar();
            if (agentJar != null && agentJar.isFile()) {
                instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(agentJar));
            }
        } catch (Exception ignored) {
        }

        TraceEventSink sink = TraceEventSink.open(resolveTraceFile(agentArgs));
        TraceEventSink.install(sink);
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook(sink), "jar2mp-trace-shutdown"));

        new TraceTransformer(sink).install(instrumentation);
    }

    public static final class ShutdownHook implements Runnable {
        private final TraceEventSink sink;

        public ShutdownHook(TraceEventSink sink) {
            this.sink = sink;
        }

        @Override
        public void run() {
            if (sink != null) {
                sink.close();
            }
        }
    }

    private static String resolveTraceFile(String agentArgs) {
        String traceFile = System.getProperty("jar2mp.traceFile");
        if (traceFile != null && !traceFile.trim().isEmpty()) {
            return traceFile.trim();
        }
        if (agentArgs != null && agentArgs.startsWith("traceFile=")) {
            return agentArgs.substring("traceFile=".length()).trim();
        }
        return "jar2mp-trace.jsonl";
    }

    private static File resolveAgentJar() throws URISyntaxException, IOException {
        CodeSource codeSource = TraceAgent.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return null;
        }
        return new File(codeSource.getLocation().toURI());
    }
}

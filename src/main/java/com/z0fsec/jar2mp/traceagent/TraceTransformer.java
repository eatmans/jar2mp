package com.z0fsec.jar2mp.traceagent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class TraceTransformer {
    private static final ElementMatcher.Junction<TypeDescription> APPLICATION_TYPES =
            not(nameStartsWith("java."))
                    .and(not(nameStartsWith("javax.")))
                    .and(not(nameStartsWith("sun.")))
                    .and(not(nameStartsWith("jdk.")))
                    .and(not(nameStartsWith("com.sun.")))
                    .and(not(nameStartsWith("net.bytebuddy.")))
                    .and(not(nameStartsWith("com.z0fsec.jar2mp.traceagent.")));

    private static final AtomicReference<Method> FOR_NAME_3 = new AtomicReference<Method>();
    private static final AtomicReference<Method> GET_METHOD = new AtomicReference<Method>();
    private static final AtomicReference<Method> GET_DECLARED_METHOD = new AtomicReference<Method>();
    private static final AtomicReference<Method> GET_FIELD = new AtomicReference<Method>();
    private static final AtomicReference<Method> GET_DECLARED_FIELD = new AtomicReference<Method>();
    private static final AtomicReference<Method> METHOD_INVOKE = new AtomicReference<Method>();
    private static final AtomicReference<Method> CLASS_GET_RESOURCE = new AtomicReference<Method>();
    private static final AtomicReference<Method> CLASS_GET_RESOURCE_AS_STREAM = new AtomicReference<Method>();
    private static final AtomicReference<Method> LOADER_GET_RESOURCE = new AtomicReference<Method>();
    private static final AtomicReference<Method> LOADER_GET_RESOURCE_AS_STREAM = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILE_INPUT_STREAM_STRING = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILE_INPUT_STREAM_FILE = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILE_INPUT_STREAM_DESCRIPTOR = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILE_OUTPUT_STREAM_STRING = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILE_OUTPUT_STREAM_FILE = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_NEW_INPUT_STREAM = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_NEW_OUTPUT_STREAM = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_NEW_BUFFERED_READER = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_NEW_BUFFERED_READER_CS = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_READ_ALL_LINES = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_READ_ALL_LINES_CS = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_LINES = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_LINES_CS = new AtomicReference<Method>();
    private static final AtomicReference<Method> SOCKET_STRING_INT = new AtomicReference<Method>();
    private static final AtomicReference<Method> SOCKET_ADDRESS_INT = new AtomicReference<Method>();
    private static final AtomicReference<Method> HTTP_CONNECT = new AtomicReference<Method>();
    private static final AtomicReference<Method> HTTP_GET_INPUT_STREAM = new AtomicReference<Method>();
    private static final AtomicReference<Method> HTTP_GET_OUTPUT_STREAM = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILE_READER_STRING = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILE_READER_FILE = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILE_WRITER_STRING = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILE_WRITER_FILE = new AtomicReference<Method>();
    private static final AtomicReference<Method> RANDOM_ACCESS_FILE_STRING = new AtomicReference<Method>();
    private static final AtomicReference<Method> RANDOM_ACCESS_FILE_FILE = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_READ_ALL_BYTES = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_WRITE = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_COPY_PATH_PATH = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_COPY_IN_PATH = new AtomicReference<Method>();
    private static final AtomicReference<Method> FILES_COPY_PATH_OUT = new AtomicReference<Method>();
    private static final AtomicReference<Method> FOR_NAME = new AtomicReference<Method>();

    private final TraceEventSink sink;
    private final List<String> includePrefixes;

    public TraceTransformer(TraceEventSink sink) {
        this(sink, Collections.<String>emptyList());
    }

    public TraceTransformer(TraceEventSink sink, List<String> includePrefixes) {
        this.sink = sink;
        this.includePrefixes = normalizeIncludes(includePrefixes);
    }

    public void install(Instrumentation instrumentation) {
        if (instrumentation == null || sink == null || !sink.isEnabled()) {
            return;
        }

        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .disableClassFormatChanges()
                .type(traceableTypes())
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder,
                                                            TypeDescription typeDescription,
                                                            ClassLoader classLoader,
                                                            net.bytebuddy.utility.JavaModule module,
                                                            ProtectionDomain protectionDomain) {
                        return builder
                                .visit(reflectionSubstitution())
                                .visit(resourceSubstitution())
                                .visit(fileSubstitution())
                                .visit(socketSubstitution());
                    }
                })
                .installOn(instrumentation);
    }

    private ElementMatcher<TypeDescription> traceableTypes() {
        return new ElementMatcher<TypeDescription>() {
            @Override
            public boolean matches(TypeDescription target) {
                return target != null && shouldTraceType(target.getName(), includePrefixes);
            }
        };
    }

    static boolean shouldTraceType(String className, List<String> includePrefixes) {
        if (isExcludedType(className)) {
            return false;
        }
        List<String> normalizedIncludes = normalizeIncludes(includePrefixes);
        if (normalizedIncludes.isEmpty()) {
            return true;
        }
        for (String includePrefix : normalizedIncludes) {
            if (className.startsWith(includePrefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExcludedType(String className) {
        return className == null
                || className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("sun.")
                || className.startsWith("jdk.")
                || className.startsWith("com.sun.")
                || className.startsWith("net.bytebuddy.")
                || className.startsWith("com.z0fsec.jar2mp.traceagent.");
    }

    private static List<String> normalizeIncludes(List<String> includePrefixes) {
        if (includePrefixes == null || includePrefixes.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>();
        for (String includePrefix : includePrefixes) {
            if (includePrefix != null) {
                String trimmed = includePrefix.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized.isEmpty() ? Collections.<String>emptyList() : Collections.unmodifiableList(normalized);
    }

    private AsmVisitorWrapper reflectionSubstitution() {
        return MemberSubstitution.strict()
                .failIfNoMatch(false)
                .method(named("forName").and(isDeclaredBy(Class.class)).and(takesArguments(String.class)))
                .replaceWith(method(FOR_NAME, TraceHooks.class, "forName", String.class))
                .method(named("forName").and(isDeclaredBy(Class.class)).and(takesArguments(String.class, boolean.class, ClassLoader.class)))
                .replaceWith(method(FOR_NAME_3, TraceHooks.class, "forName", String.class, boolean.class, ClassLoader.class))
                .method(named("getMethod").and(isDeclaredBy(Class.class)).and(takesArguments(String.class, Class[].class)))
                .replaceWith(method(GET_METHOD, TraceHooks.class, "getMethod", Class.class, String.class, Class[].class))
                .method(named("getDeclaredMethod").and(isDeclaredBy(Class.class)).and(takesArguments(String.class, Class[].class)))
                .replaceWith(method(GET_DECLARED_METHOD, TraceHooks.class, "getDeclaredMethod", Class.class, String.class, Class[].class))
                .method(named("getField").and(isDeclaredBy(Class.class)).and(takesArguments(String.class)))
                .replaceWith(method(GET_FIELD, TraceHooks.class, "getField", Class.class, String.class))
                .method(named("getDeclaredField").and(isDeclaredBy(Class.class)).and(takesArguments(String.class)))
                .replaceWith(method(GET_DECLARED_FIELD, TraceHooks.class, "getDeclaredField", Class.class, String.class))
                .method(named("invoke").and(isDeclaredBy(Method.class)).and(takesArguments(Object.class, Object[].class)))
                .replaceWith(method(METHOD_INVOKE, TraceHooks.class, "invoke", Method.class, Object.class, Object[].class))
                .on(any());
    }

    private AsmVisitorWrapper resourceSubstitution() {
        return MemberSubstitution.strict()
                .failIfNoMatch(false)
                .method(named("getResource").and(isDeclaredBy(Class.class)).and(takesArguments(String.class)))
                .replaceWith(method(CLASS_GET_RESOURCE, TraceHooks.class, "getResource", Class.class, String.class))
                .method(named("getResourceAsStream").and(isDeclaredBy(Class.class)).and(takesArguments(String.class)))
                .replaceWith(method(CLASS_GET_RESOURCE_AS_STREAM, TraceHooks.class, "getResourceAsStream", Class.class, String.class))
                .method(named("getResource").and(isDeclaredBy(ClassLoader.class)).and(takesArguments(String.class)))
                .replaceWith(method(LOADER_GET_RESOURCE, TraceHooks.class, "getResource", ClassLoader.class, String.class))
                .method(named("getResourceAsStream").and(isDeclaredBy(ClassLoader.class)).and(takesArguments(String.class)))
                .replaceWith(method(LOADER_GET_RESOURCE_AS_STREAM, TraceHooks.class, "getResourceAsStream", ClassLoader.class, String.class))
                .on(any());
    }

    private AsmVisitorWrapper fileSubstitution() {
        return MemberSubstitution.strict()
                .failIfNoMatch(false)
                .constructor(isDeclaredBy(FileInputStream.class).and(takesArguments(String.class)))
                .replaceWith(method(FILE_INPUT_STREAM_STRING, TraceHooks.class, "newFileInputStream", String.class))
                .constructor(isDeclaredBy(FileInputStream.class).and(takesArguments(File.class)))
                .replaceWith(method(FILE_INPUT_STREAM_FILE, TraceHooks.class, "newFileInputStream", File.class))
                .constructor(isDeclaredBy(FileInputStream.class).and(takesArguments(java.io.FileDescriptor.class)))
                .replaceWith(method(FILE_INPUT_STREAM_DESCRIPTOR, TraceHooks.class, "newFileInputStream", java.io.FileDescriptor.class))
                .constructor(isDeclaredBy(FileOutputStream.class).and(takesArguments(String.class)))
                .replaceWith(method(FILE_OUTPUT_STREAM_STRING, TraceHooks.class, "newFileOutputStream", String.class))
                .constructor(isDeclaredBy(FileOutputStream.class).and(takesArguments(File.class)))
                .replaceWith(method(FILE_OUTPUT_STREAM_FILE, TraceHooks.class, "newFileOutputStream", File.class))
                .method(named("newInputStream").and(isDeclaredBy(Files.class)).and(takesArguments(2))
                        .and(takesArgument(0, Path.class))
                        .and(takesArgument(1, OpenOption[].class)))
                .replaceWith(method(FILES_NEW_INPUT_STREAM, TraceHooks.class, "newInputStream", Path.class, OpenOption[].class))
                .method(named("newOutputStream").and(isDeclaredBy(Files.class)).and(takesArguments(2))
                        .and(takesArgument(0, Path.class))
                        .and(takesArgument(1, OpenOption[].class)))
                .replaceWith(method(FILES_NEW_OUTPUT_STREAM, TraceHooks.class, "newOutputStream", Path.class, OpenOption[].class))
                .method(named("newBufferedReader").and(isDeclaredBy(Files.class)).and(takesArguments(new Class<?>[]{Path.class})))
                .replaceWith(method(FILES_NEW_BUFFERED_READER, TraceHooks.class, "newBufferedReader", Path.class))
                .method(named("newBufferedReader").and(isDeclaredBy(Files.class)).and(takesArguments(2))
                        .and(takesArgument(0, Path.class))
                        .and(takesArgument(1, java.nio.charset.Charset.class)))
                .replaceWith(method(FILES_NEW_BUFFERED_READER_CS, TraceHooks.class, "newBufferedReader", Path.class, java.nio.charset.Charset.class))
                .method(named("readAllLines").and(isDeclaredBy(Files.class)).and(takesArguments(new Class<?>[]{Path.class})))
                .replaceWith(method(FILES_READ_ALL_LINES, TraceHooks.class, "readAllLines", Path.class))
                .method(named("readAllLines").and(isDeclaredBy(Files.class)).and(takesArguments(2))
                        .and(takesArgument(0, Path.class))
                        .and(takesArgument(1, java.nio.charset.Charset.class)))
                .replaceWith(method(FILES_READ_ALL_LINES_CS, TraceHooks.class, "readAllLines", Path.class, java.nio.charset.Charset.class))
                .method(named("lines").and(isDeclaredBy(Files.class)).and(takesArguments(new Class<?>[]{Path.class})))
                .replaceWith(method(FILES_LINES, TraceHooks.class, "lines", Path.class))
                .method(named("lines").and(isDeclaredBy(Files.class)).and(takesArguments(2))
                        .and(takesArgument(0, Path.class))
                        .and(takesArgument(1, java.nio.charset.Charset.class)))
                .replaceWith(method(FILES_LINES_CS, TraceHooks.class, "lines", Path.class, java.nio.charset.Charset.class))
                .constructor(isDeclaredBy(FileReader.class).and(takesArguments(String.class)))
                .replaceWith(method(FILE_READER_STRING, TraceHooks.class, "newFileReader", String.class))
                .constructor(isDeclaredBy(FileReader.class).and(takesArguments(File.class)))
                .replaceWith(method(FILE_READER_FILE, TraceHooks.class, "newFileReader", File.class))
                .constructor(isDeclaredBy(FileWriter.class).and(takesArguments(String.class)))
                .replaceWith(method(FILE_WRITER_STRING, TraceHooks.class, "newFileWriter", String.class))
                .constructor(isDeclaredBy(FileWriter.class).and(takesArguments(File.class)))
                .replaceWith(method(FILE_WRITER_FILE, TraceHooks.class, "newFileWriter", File.class))
                .constructor(isDeclaredBy(RandomAccessFile.class).and(takesArguments(String.class, String.class)))
                .replaceWith(method(RANDOM_ACCESS_FILE_STRING, TraceHooks.class, "newRandomAccessFile", String.class, String.class))
                .constructor(isDeclaredBy(RandomAccessFile.class).and(takesArguments(File.class, String.class)))
                .replaceWith(method(RANDOM_ACCESS_FILE_FILE, TraceHooks.class, "newRandomAccessFile", File.class, String.class))
                .method(named("readAllBytes").and(isDeclaredBy(Files.class)).and(takesArguments(Path.class)))
                .replaceWith(method(FILES_READ_ALL_BYTES, TraceHooks.class, "readAllBytes", Path.class))
                .method(named("write").and(isDeclaredBy(Files.class)).and(takesArguments(3))
                        .and(takesArgument(0, Path.class))
                        .and(takesArgument(1, byte[].class))
                        .and(takesArgument(2, OpenOption[].class)))
                .replaceWith(method(FILES_WRITE, TraceHooks.class, "write", Path.class, byte[].class, OpenOption[].class))
                .method(named("copy").and(isDeclaredBy(Files.class)).and(takesArguments(3))
                        .and(takesArgument(0, Path.class))
                        .and(takesArgument(1, Path.class))
                        .and(takesArgument(2, CopyOption[].class)))
                .replaceWith(method(FILES_COPY_PATH_PATH, TraceHooks.class, "copy", Path.class, Path.class, CopyOption[].class))
                .method(named("copy").and(isDeclaredBy(Files.class)).and(takesArguments(3))
                        .and(takesArgument(0, InputStream.class))
                        .and(takesArgument(1, Path.class))
                        .and(takesArgument(2, CopyOption[].class)))
                .replaceWith(method(FILES_COPY_IN_PATH, TraceHooks.class, "copy", InputStream.class, Path.class, CopyOption[].class))
                .method(named("copy").and(isDeclaredBy(Files.class)).and(takesArguments(2))
                        .and(takesArgument(0, Path.class))
                        .and(takesArgument(1, OutputStream.class)))
                .replaceWith(method(FILES_COPY_PATH_OUT, TraceHooks.class, "copy", Path.class, OutputStream.class))
                .on(any());
    }

    private AsmVisitorWrapper socketSubstitution() {
        return MemberSubstitution.strict()
                .failIfNoMatch(false)
                .constructor(isDeclaredBy(Socket.class).and(takesArguments(String.class, int.class)))
                .replaceWith(method(SOCKET_STRING_INT, TraceHooks.class, "newSocket", String.class, int.class))
                .constructor(isDeclaredBy(Socket.class).and(takesArguments(InetAddress.class, int.class)))
                .replaceWith(method(SOCKET_ADDRESS_INT, TraceHooks.class, "newSocket", InetAddress.class, int.class))
                .method(named("connect").and(isDeclaredBy(URLConnection.class)).and(takesArguments(new TypeDescription[0])))
                .replaceWith(method(HTTP_CONNECT, TraceHooks.class, "connect", URLConnection.class))
                .method(named("getInputStream").and(isDeclaredBy(URLConnection.class)).and(takesArguments(new TypeDescription[0])))
                .replaceWith(method(HTTP_GET_INPUT_STREAM, TraceHooks.class, "getInputStream", URLConnection.class))
                .method(named("getOutputStream").and(isDeclaredBy(URLConnection.class)).and(takesArguments(new TypeDescription[0])))
                .replaceWith(method(HTTP_GET_OUTPUT_STREAM, TraceHooks.class, "getOutputStream", URLConnection.class))
                .on(any());
    }

    private static Method method(AtomicReference<Method> cache,
                                 Class<?> type,
                                 String name,
                                 Class<?>... parameterTypes) {
        Method method = cache.get();
        if (method != null) {
            return method;
        }
        try {
            Method resolved = type.getMethod(name, parameterTypes);
            cache.compareAndSet(null, resolved);
            return cache.get();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Missing trace hook method: " + type.getName() + "#" + name, e);
        }
    }
}

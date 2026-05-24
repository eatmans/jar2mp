package com.z0fsec.jar2mp.traceagent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public final class TraceHooks {
    private static final String REFLECTION = "reflection";
    private static final String RESOURCE = "resource";
    private static final String FILE = "file";
    private static final String SOCKET = "socket";

    private TraceHooks() {
    }

    public static Class<?> forName(String name) throws ClassNotFoundException {
        ClassNotFoundException failure = null;
        for (String candidate : classNameCandidates(name)) {
            try {
                Class<?> loaded = Class.forName(candidate);
                record(REFLECTION, "java.lang.Class", "forName", candidate);
                return loaded;
            } catch (ClassNotFoundException e) {
                if (failure == null) {
                    failure = e;
                }
            }
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            if (contextLoader != null) {
                try {
                    Class<?> loaded = Class.forName(candidate, true, contextLoader);
                    record(REFLECTION, "java.lang.Class", "forName", candidate);
                    return loaded;
                } catch (ClassNotFoundException e) {
                    if (failure == null) {
                        failure = e;
                    }
                }
            }
        }
        record(REFLECTION, "java.lang.Class", "forName", name);
        throw failure == null ? new ClassNotFoundException(String.valueOf(name)) : failure;
    }

    public static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        ClassNotFoundException failure = null;
        for (String candidate : classNameCandidates(name)) {
            try {
                Class<?> loaded = Class.forName(candidate, initialize, loader);
                record(REFLECTION, "java.lang.Class", "forName", candidate);
                return loaded;
            } catch (ClassNotFoundException e) {
                if (failure == null) {
                    failure = e;
                }
            }
        }
        record(REFLECTION, "java.lang.Class", "forName", name);
        throw failure == null ? new ClassNotFoundException(String.valueOf(name)) : failure;
    }

    public static Method getMethod(Class<?> type, String name, Class<?>[] parameterTypes) throws NoSuchMethodException {
        record(REFLECTION, owner(type), "getMethod", signature(name, parameterTypes));
        return type.getMethod(name, parameterTypes);
    }

    public static Method getDeclaredMethod(Class<?> type, String name, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        record(REFLECTION, owner(type), "getDeclaredMethod", signature(name, parameterTypes));
        return type.getDeclaredMethod(name, parameterTypes);
    }

    public static Field getField(Class<?> type, String name) throws NoSuchFieldException {
        record(REFLECTION, owner(type), "getField", name);
        return type.getField(name);
    }

    public static Field getDeclaredField(Class<?> type, String name) throws NoSuchFieldException {
        record(REFLECTION, owner(type), "getDeclaredField", name);
        return type.getDeclaredField(name);
    }

    public static Object invoke(Method method, Object target, Object[] args)
            throws IllegalAccessException, InvocationTargetException {
        record(REFLECTION, owner(method), "invoke", signature(method));
        return method.invoke(target, args);
    }

    public static URL getResource(Class<?> type, String name) {
        record(RESOURCE, owner(type), "getResource", name);
        return type.getResource(name);
    }

    public static InputStream getResourceAsStream(Class<?> type, String name) {
        record(RESOURCE, owner(type), "getResourceAsStream", name);
        return type.getResourceAsStream(name);
    }

    public static URL getResource(ClassLoader loader, String name) {
        record(RESOURCE, owner(loader), "getResource", name);
        return loader.getResource(name);
    }

    public static InputStream getResourceAsStream(ClassLoader loader, String name) {
        record(RESOURCE, owner(loader), "getResourceAsStream", name);
        return loader.getResourceAsStream(name);
    }

    public static FileInputStream newFileInputStream(String name) throws FileNotFoundException {
        record(FILE, "java.io.FileInputStream", "new", name);
        return new FileInputStream(name);
    }

    public static FileInputStream newFileInputStream(File file) throws FileNotFoundException {
        record(FILE, "java.io.FileInputStream", "new", fileValue(file));
        return new FileInputStream(file);
    }

    public static FileInputStream newFileInputStream(java.io.FileDescriptor descriptor) {
        record(FILE, "java.io.FileInputStream", "new", String.valueOf(descriptor));
        return new FileInputStream(descriptor);
    }

    public static FileOutputStream newFileOutputStream(String name) throws FileNotFoundException {
        record(FILE, "java.io.FileOutputStream", "new", name);
        return new FileOutputStream(name);
    }

    public static FileOutputStream newFileOutputStream(File file) throws FileNotFoundException {
        record(FILE, "java.io.FileOutputStream", "new", fileValue(file));
        return new FileOutputStream(file);
    }

    public static InputStream newInputStream(Path path, OpenOption[] options) throws IOException {
        record(FILE, "java.nio.file.Files", "newInputStream", pathValue(path));
        return Files.newInputStream(path, options);
    }

    public static OutputStream newOutputStream(Path path, OpenOption[] options) throws IOException {
        record(FILE, "java.nio.file.Files", "newOutputStream", pathValue(path));
        return Files.newOutputStream(path, options);
    }

    public static BufferedReader newBufferedReader(Path path) throws IOException {
        record(FILE, "java.nio.file.Files", "newBufferedReader", pathValue(path));
        return Files.newBufferedReader(path);
    }

    public static BufferedReader newBufferedReader(Path path, Charset charset) throws IOException {
        record(FILE, "java.nio.file.Files", "newBufferedReader", pathValue(path));
        return Files.newBufferedReader(path, charset);
    }

    public static List<String> readAllLines(Path path) throws IOException {
        record(FILE, "java.nio.file.Files", "readAllLines", pathValue(path));
        return Files.readAllLines(path);
    }

    public static List<String> readAllLines(Path path, Charset charset) throws IOException {
        record(FILE, "java.nio.file.Files", "readAllLines", pathValue(path));
        return Files.readAllLines(path, charset);
    }

    public static Stream<String> lines(Path path) throws IOException {
        record(FILE, "java.nio.file.Files", "lines", pathValue(path));
        return Files.lines(path);
    }

    public static Stream<String> lines(Path path, Charset charset) throws IOException {
        record(FILE, "java.nio.file.Files", "lines", pathValue(path));
        return Files.lines(path, charset);
    }

    public static Socket newSocket(String host, int port) throws IOException {
        record(SOCKET, "java.net.Socket", "new", host + ":" + port);
        return new Socket(host, port);
    }

    public static Socket newSocket(InetAddress address, int port) throws IOException {
        record(SOCKET, "java.net.Socket", "new", String.valueOf(address) + ":" + port);
        return new Socket(address, port);
    }

    public static void connect(URLConnection connection) throws IOException {
        record(SOCKET, owner(connection), "connect", connectionValue(connection));
        connection.connect();
    }

    public static InputStream getInputStream(URLConnection connection) throws IOException {
        record(SOCKET, owner(connection), "getInputStream", connectionValue(connection));
        return connection.getInputStream();
    }

    public static OutputStream getOutputStream(URLConnection connection) throws IOException {
        record(SOCKET, owner(connection), "getOutputStream", connectionValue(connection));
        return connection.getOutputStream();
    }

    private static void record(String kind, String owner, String target, String value) {
        TraceEventSink.record(kind, owner, target, value);
    }

    private static String owner(Class<?> type) {
        return type == null ? "unknown" : type.getName();
    }

    private static String owner(Method method) {
        return method == null || method.getDeclaringClass() == null
                ? "unknown"
                : method.getDeclaringClass().getName();
    }

    private static String owner(ClassLoader loader) {
        return loader == null ? "bootstrap" : loader.getClass().getName();
    }

    private static String owner(URLConnection connection) {
        return connection == null ? "java.net.URLConnection" : connection.getClass().getName();
    }

    private static String pathValue(Path path) {
        return path == null ? "" : path.toString();
    }

    private static String fileValue(File file) {
        return file == null ? "" : file.getPath();
    }

    private static String connectionValue(URLConnection connection) {
        return connection == null ? "" : connection.getURL() == null ? "" : connection.getURL().toString();
    }

    private static String normalizeBinaryClassName(String name) {
        if (name == null || name.startsWith("[") || name.indexOf('/') < 0) {
            return name;
        }
        return name.replace('/', '.');
    }

    private static String[] classNameCandidates(String name) {
        String binaryName = normalizeBinaryClassName(name);
        if (binaryName == null || binaryName.equals(name)) {
            return new String[]{name};
        }
        return new String[]{name, binaryName};
    }

    private static String signature(String name, Class<?>[] parameterTypes) {
        return name + Arrays.toString(parameterTypes);
    }

    private static String signature(Method method) {
        if (method == null) {
            return "";
        }
        return signature(method.getName(), method.getParameterTypes());
    }

}

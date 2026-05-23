package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.JarAnalysisResult;
import com.z0fsec.jar2mp.model.ResourceFinding;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ResourceClassifier {

    private static final String BOOT_CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String BOOT_LIB_PREFIX = "BOOT-INF/lib/";
    private static final String WEB_CLASSES_PREFIX = "WEB-INF/classes/";
    private static final String WEB_LIB_PREFIX = "WEB-INF/lib/";
    private static final String SKIPPED = "(skipped)";

    public List<ResourceFinding> classify(JarAnalysisResult result) {
        List<ResourceFinding> findings = new ArrayList<>();
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(result.getResourceFiles());
        paths.addAll(result.getMetaInfFiles());

        for (String path : paths) {
            if (path == null || path.endsWith("/")) {
                continue;
            }
            findings.add(classifyPath(path, result.isWar()));
        }

        return findings;
    }

    private ResourceFinding classifyPath(String originalPath, boolean war) {
        ResourceFinding.Category category = categoryFor(originalPath);
        String targetPath = targetPathFor(originalPath, war);
        String note = noteFor(originalPath, category, targetPath);
        return new ResourceFinding(originalPath, category, targetPath, note);
    }

    private ResourceFinding.Category categoryFor(String path) {
        String strippedPath = stripClasspathResourcePrefix(path);
        String lower = strippedPath.toLowerCase(Locale.ROOT);

        if (isNestedLibrary(path)) {
            return ResourceFinding.Category.NESTED_LIBRARY;
        }
        if ("web-inf/web.xml".equals(lower)) {
            return ResourceFinding.Category.SERVLET_DESCRIPTOR;
        }
        if (lower.startsWith("meta-inf/services/")) {
            return ResourceFinding.Category.SPI;
        }
        if (isConfigResource(lower)) {
            return ResourceFinding.Category.CONFIG;
        }
        if (lower.endsWith("mapper.xml") || lower.endsWith("/mybatis-config.xml")
                || "mybatis-config.xml".equals(lower)) {
            return ResourceFinding.Category.MYBATIS_MAPPER;
        }
        if (lower.startsWith("templates/") || lower.endsWith(".html")
                || lower.endsWith(".htm") || lower.endsWith(".ftl")
                || lower.endsWith(".vm") || lower.endsWith(".jsp")) {
            return ResourceFinding.Category.TEMPLATE;
        }
        if (lower.startsWith("static/") || lower.startsWith("public/")
                || lower.startsWith("assets/") || lower.startsWith("webjars/")
                || isFrontendExtension(lower)) {
            return ResourceFinding.Category.FRONTEND_ASSET;
        }
        if (lower.endsWith(".so") || lower.endsWith(".dll") || lower.endsWith(".dylib")) {
            return ResourceFinding.Category.NATIVE_LIBRARY;
        }
        if (lower.endsWith(".jks") || lower.endsWith(".p12") || lower.endsWith(".pfx")
                || lower.endsWith(".keystore") || lower.endsWith(".crt") || lower.endsWith(".cer")
                || lower.endsWith(".pem")) {
            return ResourceFinding.Category.CERTIFICATE;
        }
        if (lower.startsWith("meta-inf/")) {
            return ResourceFinding.Category.META_INF_RUNTIME;
        }
        return ResourceFinding.Category.OTHER;
    }

    private boolean isConfigResource(String lowerPath) {
        String fileName = fileName(lowerPath);
        return fileName.equals("application.yml")
                || fileName.equals("application.yaml")
                || fileName.equals("application.properties")
                || fileName.equals("bootstrap.yml")
                || fileName.equals("bootstrap.yaml")
                || fileName.equals("bootstrap.properties")
                || fileName.equals("log4j2.xml")
                || fileName.equals("logback.xml")
                || fileName.equals("log4j.properties")
                || fileName.equals("shiro.ini");
    }

    private boolean isFrontendExtension(String lowerPath) {
        return lowerPath.endsWith(".js")
                || lowerPath.endsWith(".css")
                || lowerPath.endsWith(".map")
                || lowerPath.endsWith(".png")
                || lowerPath.endsWith(".jpg")
                || lowerPath.endsWith(".jpeg")
                || lowerPath.endsWith(".gif")
                || lowerPath.endsWith(".svg")
                || lowerPath.endsWith(".ico")
                || lowerPath.endsWith(".woff")
                || lowerPath.endsWith(".woff2")
                || lowerPath.endsWith(".ttf");
    }

    private String targetPathFor(String originalPath, boolean war) {
        if (isNestedLibrary(originalPath) || shouldSkipMetaInfResource(originalPath)) {
            return SKIPPED;
        }

        String outputRelativePath = stripClasspathResourcePrefix(originalPath);
        if (isUnsafeRelativePath(outputRelativePath)) {
            return SKIPPED;
        }

        if (isClasspathResource(originalPath)) {
            return "src/main/resources/" + outputRelativePath;
        }

        String base = war ? "src/main/webapp/" : "src/main/resources/";
        return base + outputRelativePath;
    }

    private String noteFor(String originalPath, ResourceFinding.Category category, String targetPath) {
        if (SKIPPED.equals(targetPath)) {
            if (isNestedLibrary(originalPath)) {
                return "Nested dependency archive is skipped during project generation.";
            }
            if (shouldSkipMetaInfResource(originalPath)) {
                return "Build metadata or signature file is skipped during project generation.";
            }
            return "Unsafe output path is skipped during project generation.";
        }
        if (isClasspathResource(originalPath)) {
            return "Classpath resource; container prefix is stripped.";
        }
        switch (category) {
            case CONFIG:
                return "Configuration resource; verify environment-specific values.";
            case MYBATIS_MAPPER:
                return "MyBatis mapper XML; verify mapper scan paths.";
            case TEMPLATE:
                return "Template resource; verify view resolver paths.";
            case FRONTEND_ASSET:
                return "Static/frontend asset.";
            case SERVLET_DESCRIPTOR:
                return "Servlet descriptor; WAR projects restore it under webapp.";
            case SPI:
                return "Java SPI/runtime metadata; preserve exact path.";
            case NATIVE_LIBRARY:
                return "Native library; verify runtime loading path.";
            case CERTIFICATE:
                return "Certificate or keystore; verify secrets before sharing.";
            case META_INF_RUNTIME:
                return "META-INF runtime resource; preserve exact path.";
            default:
                return "";
        }
    }

    private boolean isClasspathResource(String resourcePath) {
        return resourcePath.startsWith(BOOT_CLASSES_PREFIX) || resourcePath.startsWith(WEB_CLASSES_PREFIX);
    }

    private String stripClasspathResourcePrefix(String resourcePath) {
        if (resourcePath.startsWith(BOOT_CLASSES_PREFIX)) {
            return resourcePath.substring(BOOT_CLASSES_PREFIX.length());
        }
        if (resourcePath.startsWith(WEB_CLASSES_PREFIX)) {
            return resourcePath.substring(WEB_CLASSES_PREFIX.length());
        }
        return resourcePath;
    }

    private boolean isNestedLibrary(String resourcePath) {
        return resourcePath.startsWith(BOOT_LIB_PREFIX) || resourcePath.startsWith(WEB_LIB_PREFIX);
    }

    private boolean shouldSkipMetaInfResource(String path) {
        if (path == null) {
            return false;
        }

        String upperPath = path.toUpperCase(Locale.ROOT);
        if ("META-INF/MANIFEST.MF".equals(upperPath) || upperPath.startsWith("META-INF/MAVEN/")) {
            return true;
        }

        int lastSlash = upperPath.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? upperPath.substring(lastSlash + 1) : upperPath;
        return fileName.endsWith(".SF") || fileName.endsWith(".RSA")
                || fileName.endsWith(".DSA") || fileName.endsWith(".EC");
    }

    private boolean isUnsafeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return true;
        }
        try {
            Path normalized = Paths.get(relativePath.replace('\\', '/')).normalize();
            return normalized.isAbsolute()
                    || normalized.toString().equals("..")
                    || normalized.startsWith("..");
        } catch (InvalidPathException e) {
            return true;
        }
    }

    private String fileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}

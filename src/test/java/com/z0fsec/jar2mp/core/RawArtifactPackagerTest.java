package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.ArtifactFidelityResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawArtifactPackagerTest {

    @TempDir
    Path tempDir;

    @Test
    void preservesOriginalArtifactAsExactByteCopy() throws Exception {
        Path original = createJar("sample-1.0.jar");
        Path outputDir = tempDir.resolve("restored-project");

        Path preserved = new RawArtifactPackager().preserve(original.toFile(), outputDir.toFile()).toPath();

        assertEquals(outputDir.resolve("target/raw-artifact/sample-1.0.jar"), preserved);
        assertTrue(Files.exists(preserved));
        ArtifactFidelityResult result = new ArtifactFidelityComparator()
                .compare(original.toFile(), preserved.toFile());
        assertTrue(result.isExactMatch());
    }

    @Test
    void preservesByteExactReferenceOutsideMavenTarget() throws Exception {
        Path original = createJar("sample-1.0.jar");
        Path outputDir = tempDir.resolve("restored-project");

        Path preserved = new RawArtifactPackager()
                .preserveByteExactReference(original.toFile(), outputDir.toFile())
                .toPath();

        assertEquals(outputDir.resolve(".jar2mp/byte-exact/raw-artifact/sample-1.0.jar"), preserved);
        assertTrue(Files.exists(preserved));
        ArtifactFidelityResult result = new ArtifactFidelityComparator()
                .compare(original.toFile(), preserved.toFile());
        assertTrue(result.isExactMatch());
    }

    private Path createJar(String fileName) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "demo.App");

        Path jar = tempDir.resolve(fileName);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("demo/App.class"));
            out.write(new byte[]{(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe});
            out.closeEntry();
            out.putNextEntry(new JarEntry("config/app.properties"));
            out.write("mode=raw\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }
}

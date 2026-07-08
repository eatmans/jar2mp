package com.z0fsec.jar2mp.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class RawArtifactPackager {

    public File preserve(File originalArtifact, File projectDir) throws IOException {
        requireProjectDir(projectDir);
        return preserveInto(originalArtifact, new File(new File(projectDir, "target"), "raw-artifact"));
    }

    public File preserveByteExactReference(File originalArtifact, File projectDir) throws IOException {
        requireProjectDir(projectDir);
        return preserveInto(originalArtifact, new File(projectDir, ".jar2mp/byte-exact/raw-artifact"));
    }

    private void requireProjectDir(File projectDir) throws IOException {
        if (projectDir == null) {
            throw new IOException("Project output directory is required.");
        }
    }

    private File preserveInto(File originalArtifact, File rawDir) throws IOException {
        if (originalArtifact == null || !originalArtifact.isFile()) {
            throw new IOException("Original artifact not found: " + describe(originalArtifact));
        }
        if (rawDir == null) {
            throw new IOException("Raw artifact directory is required.");
        }

        Files.createDirectories(rawDir.toPath());

        File preserved = new File(rawDir, originalArtifact.getName());
        Files.copy(originalArtifact.toPath(), preserved.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return preserved;
    }

    private String describe(File file) {
        return file == null ? "(null)" : file.getAbsolutePath();
    }
}

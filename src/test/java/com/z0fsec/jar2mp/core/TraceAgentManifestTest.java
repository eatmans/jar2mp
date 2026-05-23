package com.z0fsec.jar2mp.core;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceAgentManifestTest {

    @Test
    void traceAgentJarHasPremainClass() throws Exception {
        File targetDir = new File("target");
        File[] agentJars = targetDir.listFiles((dir, name) -> name.endsWith(".jar") && name.contains("agent"));
        assertTrue(agentJars != null && agentJars.length > 0, "Expected an agent jar under target/");

        List<File> jars = Arrays.asList(agentJars);
        jars.sort(Comparator.comparing(File::getName));
        File agentJar = jars.get(0);

        try (JarFile jar = new JarFile(agentJar)) {
            assertEquals(
                    "com.z0fsec.jar2mp.traceagent.TraceAgent",
                    jar.getManifest().getMainAttributes().getValue("Premain-Class"));
            assertEquals("true", jar.getManifest().getMainAttributes().getValue("Can-Retransform-Classes"));
        }
    }
}

package com.z0fsec.jar2mp.db;

import com.z0fsec.jar2mp.model.MavenCoordinates;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PackagePrefixDatabaseTest {

    @Test
    void bundledMappingsResolveKryoCoordinates() throws Exception {
        PackagePrefixDatabase database = new PackagePrefixDatabase();
        try (InputStream mappings = getClass().getResourceAsStream("/db/package-mappings.properties")) {
            assertNotNull(mappings, "package mappings resource should be on the test classpath");
            database.load(mappings);
        }

        MavenCoordinates coordinates = database.lookup("com.esotericsoftware.kryo");

        assertNotNull(coordinates);
        assertEquals("com.esotericsoftware", coordinates.getGroupId());
        assertEquals("kryo", coordinates.getArtifactId());
        assertEquals("5.5.0", coordinates.getVersion());
    }

    @Test
    void bundledMappingsResolveAutoValueAnnotationsForCompileClasspath() throws Exception {
        PackagePrefixDatabase database = new PackagePrefixDatabase();
        try (InputStream mappings = getClass().getResourceAsStream("/db/package-mappings.properties")) {
            assertNotNull(mappings, "package mappings resource should be on the test classpath");
            database.load(mappings);
        }

        MavenCoordinates coordinates = database.lookup("com.google.auto.value");

        assertNotNull(coordinates);
        assertEquals("com.google.auto.value", coordinates.getGroupId());
        assertEquals("auto-value-annotations", coordinates.getArtifactId());
        assertEquals("1.10.4", coordinates.getVersion());
    }

    @Test
    void bundledMappingsResolveJavaxAnnotationsForCompileClasspath() throws Exception {
        PackagePrefixDatabase database = new PackagePrefixDatabase();
        try (InputStream mappings = getClass().getResourceAsStream("/db/package-mappings.properties")) {
            assertNotNull(mappings, "package mappings resource should be on the test classpath");
            database.load(mappings);
        }

        MavenCoordinates coordinates = database.lookup("javax.annotation.concurrent");

        assertNotNull(coordinates);
        assertEquals("com.google.code.findbugs", coordinates.getGroupId());
        assertEquals("jsr305", coordinates.getArtifactId());
        assertEquals("3.0.2", coordinates.getVersion());
    }
}

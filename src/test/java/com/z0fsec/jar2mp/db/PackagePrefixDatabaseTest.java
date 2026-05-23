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
}

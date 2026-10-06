package com.jobproof.modules.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObjectStorageTest {

    @TempDir
    Path temp;

    @Test
    void putGetDeleteStayInsideRootAndRejectDotDot() throws Exception {
        LocalObjectStorage storage = new LocalObjectStorage(temp.toString());
        byte[] body = {1, 2, 3};
        storage.put("private/acc/file.bin", body);
        assertArrayEquals(body, storage.get("private/acc/file.bin"));
        assertTrue(Files.exists(temp.resolve("private").resolve("acc").resolve("file.bin")));

        Path outside = temp.getParent().resolve("outside.bin");
        assertFalse(Files.exists(outside));
        assertThrows(IllegalStateException.class, () -> storage.put("../outside.bin", body));
        assertThrows(IllegalStateException.class, () -> storage.put("private/../../outside.bin", body));
        assertThrows(IllegalStateException.class, () -> storage.get("../outside.bin"));
        assertFalse(Files.exists(outside));

        storage.delete("private/acc/file.bin");
        assertFalse(Files.exists(temp.resolve("private").resolve("acc").resolve("file.bin")));
    }
}

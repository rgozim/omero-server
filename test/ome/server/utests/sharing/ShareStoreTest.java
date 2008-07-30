/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */
package ome.server.utests.sharing;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import junit.framework.TestCase;
import ome.model.IObject;
import ome.model.core.Image;
import ome.services.sharing.ShareStore;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta2
 */
@Test(groups = "sharing")
public class ShareStoreTest extends TestCase {

    ShareStore store;
    File tmpDir;

    @BeforeMethod
    public void setup() throws Exception {
        tmpDir = File.createTempFile("test", "store");
        tmpDir.delete();
        tmpDir.mkdir();
        store = new ShareStore("ShareStoreTest", tmpDir);
    }

    @AfterMethod
    public void cleanup() throws Exception {
        // String[] files = tmpDir.list();
        // for (String filename : files) {
        // new File(filename).delete();
        // }
        // tmpDir.delete();
    }

    @Test
    public <T extends IObject> void testSimple() {
        store.set(1L, "user", Collections.<T> emptyList(), Collections
                .<Long> emptyList(), Collections.<String> emptyList(), false);
        store.set(2L, "other", Arrays.asList(new Image(1L, false)), Arrays
                .asList(1L, 2L), Arrays.asList("example@exmple.com"), true);
    }
    // Helpers
    // ====================

}

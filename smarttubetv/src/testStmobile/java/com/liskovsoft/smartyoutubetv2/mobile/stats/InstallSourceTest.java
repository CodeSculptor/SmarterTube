package com.liskovsoft.smartyoutubetv2.mobile.stats;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InstallSourceTest {
    private static final String OWN = "com.codesculptor.smartertube";

    @Test
    public void mapsKnownInstallers() {
        assertEquals("fdroid", InstallSource.bucket("org.fdroid.fdroid", OWN));
        assertEquals("fdroid", InstallSource.bucket("org.fdroid.basic", OWN));
        assertEquals("obtainium", InstallSource.bucket("dev.imranr.obtainium", OWN));
        assertEquals("play", InstallSource.bucket("com.android.vending", OWN));
        assertEquals("sideload", InstallSource.bucket("com.google.android.packageinstaller", OWN));
        assertEquals("sideload", InstallSource.bucket(null, OWN));
        assertEquals("sideload", InstallSource.bucket("", OWN));
        assertEquals("self", InstallSource.bucket(OWN, OWN));
    }

    @Test
    public void unknownInstallerIsOther() {
        assertEquals("other", InstallSource.bucket("com.example.mystore", OWN));
    }
}

package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AnalyticsDeviceTest {
    @Test public void combinesAndDeduplicatesManufacturer() {
        assertEquals("OnePlus CPH2655", AnalyticsTracker.deviceModel(" OnePlus ", " CPH2655 "));
        assertEquals("Samsung SM-S938U", AnalyticsTracker.deviceModel("Samsung", "Samsung SM-S938U"));
        assertEquals("Google Pixel 10 Pro", AnalyticsTracker.deviceModel("Google", "Pixel 10 Pro"));
        assertEquals("Google", AnalyticsTracker.deviceModel("Google", "unknown"));
    }

    @Test public void rejectsControlsAndMissingValues() {
        assertEquals("Unknown", AnalyticsTracker.deviceModel("unknown", "\n"));
        assertEquals("Pixel", AnalyticsTracker.deviceModel("Google\nDevice", "Pixel"));
        assertEquals("Unknown", AnalyticsTracker.deviceModel(null, null));
        assertEquals("OnePlus", AnalyticsTracker.deviceManufacturer(" OnePlus "));
        assertEquals("Unknown", AnalyticsTracker.deviceManufacturer("bad\nvalue"));
        assertEquals("16", AnalyticsTracker.androidVersion(" 16 "));
        assertEquals("Unknown", AnalyticsTracker.androidVersion("16\nextra"));
    }
}

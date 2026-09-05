package com.webapp.crazyshit;
import org.junit.Test;
import static org.junit.Assert.*;

public class DownloadResumePolicyTest {
    @Test public void acceptsOnlyTheRequestedBytes() {
        assertTrue(DownloadResumePolicy.validRange(206, "bytes 500-999/1000", 500, 999, 1000));
        assertFalse(DownloadResumePolicy.validRange(200, "bytes 500-999/1000", 500, 999, 1000));
        assertFalse(DownloadResumePolicy.validRange(206, "bytes 0-999/1000", 500, 999, 1000));
        assertFalse(DownloadResumePolicy.validRange(206, "bytes 500-899/1000", 500, 999, 1000));
        assertFalse(DownloadResumePolicy.validRange(206, "bytes 500-999/2000", 500, 999, 1000));
    }
    @Test public void malformedOrOverflowingRangesCannotCorruptAFile() {
        assertFalse(DownloadResumePolicy.validRange(206, null, 0, 9, 10));
        assertFalse(DownloadResumePolicy.validRange(206, "bytes 0-9/*", 0, 9, 10));
        assertFalse(DownloadResumePolicy.validRange(206, "bytes 0-999999999999999999999999999/10", 0, 9, 10));
        assertFalse(DownloadResumePolicy.validRange(206, "bytes 0-10/10", 0, 10, 10));
    }
    @Test public void changedOrUnverifiableResourcesRestart() {
        assertTrue(DownloadResumePolicy.sameResource("\"abc\"", "\"abc\"", 1000, 1000));
        assertFalse(DownloadResumePolicy.sameResource("\"abc\"", "\"def\"", 1000, 1000));
        assertFalse(DownloadResumePolicy.sameResource("\"abc\"", "\"abc\"", 1000, 1200));
        assertFalse(DownloadResumePolicy.sameResource("", "", 1000, 1000));
        assertFalse(DownloadResumePolicy.sameResource("W/\"abc\"", "W/\"abc\"", 1000, 1000));
        assertFalse(DownloadResumePolicy.sameResource("\"abc\"", "\"abc\"", -1, -1));
    }
}

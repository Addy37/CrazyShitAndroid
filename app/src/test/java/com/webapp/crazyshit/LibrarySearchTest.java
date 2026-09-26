package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LibrarySearchTest {
    @Test public void matchesPartialWordsIgnoringCaseAndPunctuation() {
        assertTrue(LibrarySearch.matches(
                "goth lee",
                "Big Titty Goth Egg 😈 AKA Lee",
                "https://example.com/video/123"
        ));
        assertTrue(LibrarySearch.matches(
                "public acts",
                "5 PUBLIC ACTS THAT DEFINITELY MAKE YOU...",
                ""
        ));
    }

    @Test public void matchesTokensInAnyOrderAcrossMetadata() {
        assertTrue(LibrarySearch.matches(
                "kaotic arrested",
                "Arrested naked",
                "https://kaotic.com/video/abc"
        ));
        assertTrue(LibrarySearch.matches(
                "ready offline",
                "Some clip",
                "Ready offline"
        ));
    }

    @Test public void emptyQueryMatchesAndMissingTokenDoesNot() {
        assertTrue(LibrarySearch.matches("", "Anything"));
        assertFalse(LibrarySearch.matches("totally missing", "Anything useful"));
    }
}

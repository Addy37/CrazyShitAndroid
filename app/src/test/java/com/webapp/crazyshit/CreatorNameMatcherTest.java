package com.webapp.crazyshit;
import org.junit.Test;
import static org.junit.Assert.*;

public class CreatorNameMatcherTest {
    @Test public void punctuationAndAccentsDoNotHideCreators() {
        assertEquals(0, CreatorNameMatcher.rank("Mía_Rose", "mia rose"));
        assertEquals(0, CreatorNameMatcher.rank("Mia-Rose", "miarose"));
        assertEquals(1, CreatorNameMatcher.rank("Mia Rose", "MIA"));
    }
    @Test public void exactThenPrefixThenWordThenSubstring() {
        assertTrue(CreatorNameMatcher.rank("Anna", "anna") < CreatorNameMatcher.rank("Annabelle", "anna"));
        assertTrue(CreatorNameMatcher.rank("Annabelle", "anna") < CreatorNameMatcher.rank("Jo Anna", "anna"));
        assertTrue(CreatorNameMatcher.rank("Jo Anna", "anna") < CreatorNameMatcher.rank("Brianna", "anna"));
    }
    @Test public void unrelatedAndBlankNamesCannotMatchTypedText() {
        assertEquals(Integer.MAX_VALUE, CreatorNameMatcher.rank("Mia", "anna"));
        assertEquals(Integer.MAX_VALUE, CreatorNameMatcher.rank(null, "anna"));
        assertEquals("", CreatorNameMatcher.normalized(null));
    }
    @Test public void nonLatinCreatorNamesStaySearchable() {
        assertEquals(0, CreatorNameMatcher.rank("さくら", "さくら"));
        assertEquals(1, CreatorNameMatcher.rank("Анна Иванова", "анна"));
    }
}

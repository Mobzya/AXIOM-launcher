package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidAtlasTest {
    private val sample = listOf(
        InstalledApp("Alpha", "app.alpha", "Main", false),
        InstalledApp("Able", "app.able", "Main", false),
        InstalledApp("Beta", "app.beta", "Main", false)
    )

    @Test fun buildsUniqueTypedGroups() {
        val entries = makeAtlasEntries(sample)
        assertEquals(listOf("A", "B"), entries.filter { it.app == null }.map { it.letter })
        assertEquals(sample, entries.mapNotNull { it.app })
        assertEquals(entries.size, entries.map { it.key }.toSet().size)
    }

    @Test fun repeatedLettersNeverShareHeaderKeys() {
        val entries = makeAtlasEntries(listOf(sample[0], sample[2], sample[1]))
        assertEquals(entries.size, entries.map { it.key }.toSet().size)
    }

    @Test fun emptyCatalogIsSafe() {
        assertTrue(makeAtlasEntries(emptyList()).isEmpty())
    }
}

class EquationMorphTest {
    @Test fun isolatesChangedFactorsInsteadOfFadingWholeFormula() {
        val morph = equationMorph("x+3=y", "x+4=y")
        assertEquals("x+", morph.prefix)
        assertEquals("3", morph.removed)
        assertEquals("4", morph.inserted)
        assertEquals("=y", morph.suffix)
    }

    @Test fun handlesUnrelatedAndIdenticalExpressions() {
        val unrelated = equationMorph("abc", "xyz")
        assertEquals("", unrelated.prefix)
        assertEquals("abc", unrelated.removed)
        assertEquals("xyz", unrelated.inserted)
        val identical = equationMorph("L(x)", "L(x)")
        assertEquals("", identical.removed)
        assertEquals("", identical.inserted)
    }
}

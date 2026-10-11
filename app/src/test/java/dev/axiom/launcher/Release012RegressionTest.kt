package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Release012RegressionTest {
    @Test fun exceptionallyRareEventsHaveExactDeterministicWeight() {
        val counted=(0 until 4096).mapNotNull(::rareCosmicEvent)
        assertEquals(3,counted.size)
        assertEquals(setOf(CosmicEventKind.ECLIPSE,CosmicEventKind.COSMIC_WEB,
            CosmicEventKind.PHASE_TIDE),counted.toSet())
        assertEquals(null,rareCosmicEvent(4095))
    }

    @Test fun rareEpisodesCompleteAndRemainFinite() {
        for (event in listOf(CosmicEventKind.ECLIPSE,
                CosmicEventKind.COSMIC_WEB,CosmicEventKind.PHASE_TIDE)) {
            val world=CosmicWorld(45)
            world.trigger(event)
            assertEquals(event,world.episode.kind)
            repeat(1800){world.step(1f/60f)}
            assertTrue(world.bodies.size<=23)
            assertTrue(world.bodies.all { it.x.isFinite() && it.y.isFinite() &&
                it.vx.isFinite() && it.vy.isFinite() && it.mass>0f })
        }
    }

    @Test fun appRemovalNeverSilentlyDeletesProtectedApplications() {
        val thirdParty=InstalledApp("Example","com.example.app","Activity",false)
        val protected=thirdParty.copy(packageName="dev.axiom.launcher")
        val system=thirdParty.copy(packageName="android",system=true)
        assertTrue(canRequestUninstall(thirdParty,"dev.axiom.launcher"))
        assertFalse(canRequestUninstall(protected,"dev.axiom.launcher"))
        assertFalse(canRequestUninstall(system,"dev.axiom.launcher"))
    }

    @Test fun kalmanFilterTracksMeasurementsWithoutNegativeVariance() {
        var state=KalmanEstimate()
        repeat(60){state=kalmanUpdate(state,.75f,.001f,.02f)}
        assertTrue(abs(state.mean-.75f)<.03f)
        assertTrue(state.variance>0f && state.variance.isFinite())
    }

    @Test fun kmeansLloydMovesCentersToClusterMean() {
        val points=listOf(.1f to .1f,.2f to .1f,.8f to .8f,.9f to .8f)
        val centers=listOf(.1f to .2f,.9f to .9f)
        val next=kmeansStep(points,centers)
        assertEquals(.15f,next[0].first,.0001f)
        assertEquals(.85f,next[1].first,.0001f)
        assertEquals(.1f,next[0].second,.0001f)
    }

    @Test fun brownianIsSeededAndAlwaysWithinDrawingBounds() {
        val a=brownianWalk(39,200,.6f)
        val b=brownianWalk(39,200,.6f)
        assertEquals(a,b)
        assertTrue(a.all { it.first in .04f.. .96f &&
            it.second in .04f.. .96f })
    }

    @Test fun allFortyTwoMathModelsHaveThreeDerivationFrames() {
        assertEquals(42,MathKind.entries.size)
        assertTrue(MathKind.entries.all { mathSteps(it).size==3 &&
            mathSteps(it).all(String::isNotBlank) })
        assertTrue(parseAxCommand("event eclipse") is AxCommand.Event)
        assertEquals(CosmicEventKind.COSMIC_WEB,
            (parseAxCommand("event web") as AxCommand.Event).kind)
    }

    @Test fun finiteGravitySettingRejectsNotANumber() {
        val world=CosmicWorld(13)
        world.updateGravity(Float.NaN)
        assertTrue(world.gravity.isFinite())
        assertEquals(1f,world.gravity)
    }
}

package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScienceLabTest {
    @Test fun neuralTrainingActuallyReducesLoss() {
        var network = NeuralState()
        val initial = network.error
        repeat(250) { network = network.train(.7) }
        assertTrue("Training must lower MSE", network.error < initial)
        assertTrue(network.output in 0.0..1.0)
    }

    @Test fun conwayBlinkerOscillates() {
        val board=BooleanArray(25)
        board[1*5+2]=true
        board[2*5+2]=true
        board[3*5+2]=true
        val result=lifeStep(board,5,5)
        assertTrue(result[2*5+1])
        assertTrue(result[2*5+2])
        assertTrue(result[2*5+3])
        assertEquals(3, result.count{it})
        assertTrue(lifeStep(result,5,5).contentEquals(board))
    }

    @Test fun juliaEscapesForFarCoordinates() {
        assertEquals(0, juliaIterations(3.5, 0.0, -.7, .2))
        assertTrue(juliaIterations(0.0,0.0,0.0,0.0) >= 25)
    }

    @Test fun lorenzPathIsBoundedAndFinite() {
        val path=lorenzPath(10.0)
        assertEquals(340,path.size)
        assertTrue(path.all{it.first.isFinite() && it.second.isFinite()})
    }

    @Test fun pendulumAmplitudeAndWaveDependOnParameters() {
        assertTrue(abs(pendulumAngle(0f,1f,.8f)-.8f)<.0001f)
        assertTrue(abs(waveValue(1f,0f,1f,2f)-
            waveValue(1f,0f,.5f,2f))>.1f)
    }

    @Test fun grayScottDynamicsRemainFiniteAndActuallyEvolve() {
        val lab=ScienceLab(14)
        lab.select(LabKind.REACTION)
        val before=lab.reactionV.copyOf()
        repeat(30){lab.step(.05f)}
        assertTrue(lab.reactionV.any{it>0f})
        assertTrue(!lab.reactionV.contentEquals(before))
        assertTrue(lab.reactionV.all{it.isFinite() && it in 0f..1f})
    }

    @Test fun attentionIsNormalizedAndRespondsToQuery() {
        val keys=arrayOf(doubleArrayOf(1.0,0.0),doubleArrayOf(0.0,1.0))
        val horizontal=attentionWeights(doubleArrayOf(2.0,0.0),keys,1.0)
        val vertical=attentionWeights(doubleArrayOf(0.0,2.0),keys,1.0)
        assertEquals(1.0,horizontal.sum(),1e-8)
        assertTrue(horizontal[0]>horizontal[1])
        assertTrue(vertical[1]>vertical[0])
    }

    @Test fun allTwelveExperimentsHaveFormulas() {
        val lab=ScienceLab(30)
        assertEquals(15,LabKind.entries.size)
        for(kind in LabKind.entries){
            lab.select(kind)
            assertTrue(lab.formula().isNotBlank())
            lab.step(.033f)
            assertTrue(lab.time.isFinite())
        }
    }

    @Test fun thirtyDifferentMathModelsAreCovered() {
        assertEquals(42,MathKind.entries.size)
        assertTrue(MathKind.entries.all{mathSteps(it).size==3})
    }
}

class InteractiveUniverseTest {
    @Test fun gravityBrushIsBoundedAndClearsOnRelease() {
        val world=CosmicWorld(11)
        world.startBrush(.3f,.4f)
        repeat(500){world.moveBrush(.31f,.4f)}
        assertTrue(world.brushMass<=4f)
        world.endBrush()
        assertEquals(0f,world.brushMass)
        assertTrue(world.brushPoint==null)
    }

    @Test fun orbitInjectionIsControllable() {
        val world=CosmicWorld(7)
        val before=world.bodies.size
        world.startBrush(.18f,.2f)
        world.moveBrush(.43f,.47f)
        world.endBrush()
        assertEquals(before+1,world.bodies.size)
        assertTrue(world.bodies.last().mass>.0f)
        assertTrue(world.bodies.last().lifetime>=100f)
    }

    @Test fun pauseMakesTimeAndBodiesExactlyStill() {
        val world=CosmicWorld(9)
        world.updateTimeScale(0f)
        val before=world.bodies
        val t=world.elapsed
        repeat(80){world.step(1f/60f)}
        assertEquals(t,world.elapsed)
        assertEquals(before,world.bodies)
        world.updateTimeScale(1f)
        world.step(1f/60f)
        assertTrue(world.elapsed>t)
    }

    @Test fun timeScaleIsQuantizedAndReplayIsBounded() {
        val world=CosmicWorld(9)
        world.updateTimeScale(.62f)
        assertEquals(.5f,world.timeScale)
        repeat(850){world.step(1f/60f)}
        assertTrue(world.rewind(3))
        assertTrue(world.bodies.all{it.x.isFinite()&&it.y.isFinite()})
        world.reset()
        assertFalse(world.rewind(2))
    }

    @Test fun unusualEncountersAndObjectsStayWithinBudget() {
        val world=CosmicWorld(8)
        repeat(12){world.spawnBinary()}
        assertTrue(world.bodies.size<=23)
        world.trigger(CosmicEventKind.REACTION)
        repeat(780){world.step(1f/60f)}
        assertTrue(world.bodies.size<=23)
        assertTrue(world.bodies.all{
            it.kind in 1..34 && it.mass.isFinite() && it.vx.isFinite()
        })
    }

    @Test fun commandPaletteSupportsNewFeatures() {
        assertTrue(parseAxCommand("lab neural") is AxCommand.Lab)
        assertEquals(LabKind.LIFE,
            (parseAxCommand("lab life") as AxCommand.Lab).kind)
        assertTrue(parseAxCommand("rewind 4") is AxCommand.Rewind)
        assertTrue(parseAxCommand("pause") is AxCommand.TimeScale)
        assertTrue(parseAxCommand("spawn binary") is AxCommand.SpawnBinary)
        assertEquals(AxiomThemeMode.AUTO,
            (parseAxCommand("theme auto") as AxCommand.Theme).mode)
    }
}

package dev.axiom.launcher

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ObservatoryPhysicsTest {
    @Test fun softenedCircularSpeedBalancesTheActualForce() {
        for(r in listOf(.025f,.1f,.4f)) {
            val speed=OrbitMechanics.circularSpeed(12f,r,1.7f)
            val expected=OrbitMechanics.G*1.7*12*r/
                (r*r+OrbitMechanics.SOFTENING.pow(2)).pow(1.5)
            assertEquals(expected,speed*speed/r.toDouble(),expected*1e-6)
        }
    }

    @Test fun twoBodyOrbitConservesEnergyAndAngularMomentumAcrossAnOrbit() {
        val u=AstronomicalUniverse()
        val speed=OrbitMechanics.circularSpeed(12.2f,.25f)
        u.grains.add(SandGrain(1,.4f,.5f,0f,-speed*.2f/12.2f,12f,.001,MatterMaterial.IRON,1))
        u.grains.add(SandGrain(2,.65f,.5f,0f,speed*12f/12.2f,.2f,.00001,MatterMaterial.IRON,2))
        val energy=u.totalEnergy(1f)
        val angular=angular(u)
        val initial=u.totals()
        var drift=0.0
        repeat(4200) {
            u.step(1f/120f,1f)
            drift=max(drift,abs((u.totalEnergy(1f)-energy)/energy))
        }
        println("AXIOM 0.24: two-body max relative energy drift=$drift")
        assertTrue("Verlet orbit energy drift $drift",drift<.001)
        assertEquals(angular,angular(u),2e-6)
        assertEquals(initial.momentumX,u.totals().momentumX,2e-6)
        assertEquals(initial.momentumY,u.totals().momentumY,2e-6)
    }

    @Test fun unequalCloudsExchangeReciprocalTidalForcesWithoutGlobalCorrection() {
        val u=AstronomicalUniverse()
        u.grains.add(SandGrain(1,.3f,.3f,.012f,.01f,.1f,.0001,MatterMaterial.ROCK,1))
        u.grains.add(SandGrain(2,.34f,.32f,-.003f,.02f,.3f,.0001,MatterMaterial.ICE,1))
        u.grains.add(SandGrain(3,.7f,.6f,-.02f,-.01f,2f,.0001,MatterMaterial.HYDROGEN,2))
        u.grains.add(SandGrain(4,.67f,.64f,.005f,.003f,1f,.0001,MatterMaterial.IRON,2))
        val before=u.totals(); val angular=angular(u)
        repeat(50) {u.step(1f/240f,1f)}
        assertEquals(before.momentumX,u.totals().momentumX,1e-6)
        assertEquals(before.momentumY,u.totals().momentumY,1e-6)
        assertEquals(angular,angular(u),2e-6)
    }

    @Test fun viscousInternalRelaxationConvertsEnergyToHeatWithoutBrakingSpin() {
        val u=AstronomicalUniverse()
        u.grains.add(SandGrain(1,.47f,.5f,.015f,-.02f,.01f,.000001,MatterMaterial.IRON,1))
        u.grains.add(SandGrain(2,.53f,.5f,-.015f,.02f,.01f,.000001,MatterMaterial.IRON,1))
        val before=u.totals(); val energy=u.totalEnergy(0f); val angular=angular(u)
        repeat(100) {u.step(1f/240f,0f)}
        assertTrue(u.totals().internal>before.internal)
        assertEquals(energy,u.totalEnergy(0f),2e-9)
        assertEquals(angular,angular(u),2e-9)
    }

    @Test fun wallClockRateDoesNotChangeTrajectoriesAndDoubleSpeedWorks() {
        val a=CosmicWorld(42,initialPreset=CosmicPreset.RESONANCE)
        val b=CosmicWorld(42,initialPreset=CosmicPreset.RESONANCE)
        a.configureObservation(events=false); b.configureObservation(events=false)
        a.updateTimeScale(2f); b.updateTimeScale(2f)
        repeat(120){a.step(1f/60f)}
        repeat(60){b.step(1f/30f)}
        assertEquals(4f,a.elapsed,.0001f)
        assertEquals(a.elapsed,b.elapsed,0f)
        assertArrayEquals(a.sand.snapshot().values,b.sand.snapshot().values,0f)
    }

    @Test fun learnerRecoversGravityFromUnseenAccelerationMeasurements() {
        val learner=GravityLearner()
        val primary=CelestialBody(1,12,12f,1f,.5f,.5f,0f,0f)
        repeat(180) { k ->
            val angle=k*.031
            val body=CelestialBody(2,2,.01f,1f,
                (.5+.25*cos(angle)).toFloat(),(.5+.25*sin(angle)).toFloat(),0f,0f)
            val before=listOf(primary,body)
            val field=OrbitMechanics.acceleration(body,before)
            val after=body.copy(vx=(field.first*1.6*.1).toFloat(),vy=(field.second*1.6*.1).toFloat())
            learner.observe(before,listOf(primary,after),.1f)
        }
        assertEquals(1.6,learner.reading.gravity,.005)
        assertTrue(learner.reading.samples>=180)
        assertTrue(learner.reading.relativeError!!<.1)
    }

    @Test fun snapshotRestoresEventDeduplicationAndCoolingLedger() {
        val u=AstronomicalUniverse()
        u.grains.add(SandGrain(1,.5f,.5f,0f,0f,.1f,.001,MatterMaterial.WATER))
        val event=CosmicEpisode(CosmicEventKind.CRYO_TIDE,.2f,10f,.5f,.5f,8)
        u.step(1f/120f,0f,event)
        val state=u.snapshot(); val heat=u.totals().internal
        u.step(1f/120f,0f,event.copy(serial=9))
        u.restore(state)
        u.step(1f/120f,0f,event)
        assertEquals(heat,u.totals().internal,1e-10)
        assertEquals(state.radiatedHeat,u.radiatedHeat,0.0)
    }

    @Test fun rewindRestoresSimulationTimeAndDiscardsFutureFrames() {
        val world=CosmicWorld(7,initialPreset=CosmicPreset.RESONANCE)
        world.configureObservation(events=false);world.updateTimeScale(1f)
        repeat(300){world.step(1f/60f)}
        val time=world.elapsed
        assertTrue(world.rewind(3))
        assertTrue(world.elapsed<time-2.7f)
        assertEquals(world.telemetry.mass,world.sand.totals().mass,1e-7)
    }

    @Test fun squareViewportRoundTripsAndRejectsTouchesOutsideTheUniverse() {
        for ((w,h) in listOf(400f to 900f,900f to 400f)) {
            val v=UniverseViewport(w,h)
            val p=v.normalized(v.x(.23f),v.y(.81f))!!
            assertEquals(.23f,p.first,1e-6f); assertEquals(.81f,p.second,1e-6f)
            assertEquals(v.x(.6f)-v.x(.4f),v.y(.6f)-v.y(.4f),1e-4f)
        }
        assertNull(UniverseViewport(400f,900f).normalized(200f,20f))
    }

    @Test fun invalidInputCannotPoisonWorldAndLowerLimitDoesNotEraseMatterLabels() {
        val world=CosmicWorld(42)
        val before=world.sand.snapshot()
        world.step(Float.NaN);world.step(-1f)
        assertFalse(world.createObject(MatterMaterial.ROCK,Float.NaN))
        assertArrayEquals(before.values,world.sand.snapshot().values,0f)
        world.updateMaximumBodies(12)
        assertEquals(before.values.size,world.sand.snapshot().values.size)
    }

    private fun angular(u:AstronomicalUniverse)=u.grains.sumOf {
        it.mass.toDouble()*(it.x.toDouble()*it.vy-it.y.toDouble()*it.vx)
    }
}

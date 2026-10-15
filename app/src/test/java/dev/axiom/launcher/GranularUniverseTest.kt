package dev.axiom.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class GranularUniverseTest {
    private fun grain(
        id: Int, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f,
        m: Float = .01f, heat: Double = .00002
    ) = SandGrain(id, x, y, vx, vy, m, heat, MatterMaterial.ROCK, 1)

    @Test fun gravityIsSymmetricAndPreservesMomentum() {
        val engine = GranularUniverse()
        engine.grains.add(grain(1, .3f, .48f, m = .02f))
        engine.grains.add(grain(2, .7f, .52f, m = .07f))
        val before = engine.totals()
        engine.step(1f / 60f, 1f)
        val after = engine.totals()
        assertEquals(before.mass, after.mass, 1e-8)
        assertEquals(before.momentumX, after.momentumX, 1e-7)
        assertEquals(before.momentumY, after.momentumY, 1e-7)
        assertTrue(engine.grains[0].vx > 0f && engine.grains[1].vx < 0f)
    }

    @Test fun inelasticContactConvertsKineticEnergyIntoHeat() {
        val engine = GranularUniverse()
        engine.grains.add(grain(1, .4988f, .5f, .15f))
        engine.grains.add(grain(2, .5012f, .5f, -.15f))
        val before = engine.totalEnergy(0f)
        val px = engine.totals().momentumX
        repeat(8) { engine.step(1f / 120f, 0f) }
        val after = engine.totalEnergy(0f)
        assertEquals(2, engine.grains.size)
        assertEquals(px, engine.totals().momentumX, 2e-6)
        assertTrue(engine.totals().internal > .00004)
        assertTrue(abs(after - before) < .00012)
    }

    @Test fun spontaneousAndTriggeredEventsCannotCreateParticlesOrMass() {
        val engine = GranularUniverse()
        val initial = CelestialBody(1, 2, 1f, 1f,
            .5f, .5f, .02f, -.01f, stellarClass = StellarClass.PLANET)
        engine.seed(initial, 54)
        val initialIds = engine.grains.map { it.id }.toSet()
        val initialMass = engine.totals().mass
        val shock = CosmicEpisode(CosmicEventKind.NOVA, age = 4f,
            duration = 13f, x = .5f, y = .5f, serial = 1)
        repeat(120) {
            engine.step(1f / 60f, 1f, if (it == 3) shock else CosmicEpisode())
        }
        assertEquals(initialIds, engine.grains.map { it.id }.toSet())
        assertEquals(initialMass, engine.totals().mass, 1e-6)
        assertTrue(engine.grains.all { it.x.isFinite() && it.y.isFinite()
            && it.vx.isFinite() && it.vy.isFinite() && it.heat >= 0 })
    }

    @Test fun granularMatterDoesNotRetainAnyPrescribedEggOrSphereCoordinates() {
        val engine = GranularUniverse()
        val b = CelestialBody(1, 2, 1.5f, 1f, .48f, .48f, 0f, 0f)
        engine.seed(b, 70)
        val first = engine.grains.map { it.x to it.y }
        repeat(25) { engine.step(1f/60f, 1f) }
        assertTrue(engine.grains.indices.any { i ->
            val (oldX, oldY) = first[i]
            hypot(engine.grains[i].x - oldX, engine.grains[i].y - oldY) > .00001f
        })
        val initialTotal = engine.totals().mass
        assertEquals(b.mass.toDouble(), initialTotal, 1e-5)
    }

    @Test fun detailModeDoesNotSynthesizeExtraMassOrGrains() {
        val engine = GranularUniverse()
        engine.seed(CelestialBody(9, 4, 2f, 1f, .5f, .5f, 0f, 0f), 75)
        val before = engine.totals()
        engine.updateMode(MatterMode.DETAILED)
        engine.step(1f / 60f, 1f)
        engine.updateMode(MatterMode.BALANCED)
        assertEquals(75, engine.grains.size)
        assertEquals(before.mass, engine.totals().mass, 1e-5)
    }

    @Test fun crowded4600GrainUniverseBoundsContactWorkAndPreservesMatter() {
        val engine = GranularUniverse()
        engine.updateMode(MatterMode.DETAILED)
        // 46 deliberately overlapping clouds stress the dense hash buckets.
        // This used to stall despite the accepted-contact limit because the
        // raw candidate scan was not bounded.
        repeat(46) { i ->
            engine.seed(CelestialBody(i + 100, 2, .5f, 1f,
                .46f + (i % 5) * .002f, .46f + (i / 5) * .002f,
                0f, 0f), 100)
        }
        assertEquals(4600, engine.grains.size)
        val before = engine.totals()
        val ids = engine.grains.map { it.id }.toSet()
        val started = System.nanoTime()
        repeat(2) { engine.step(1f / 30f, 1f) }
        val durationMs = (System.nanoTime() - started) / 1_000_000.0
        val after = engine.totals()
        assertEquals(before.mass, after.mass, 1e-4)
        assertEquals(ids, engine.grains.map { it.id }.toSet())
        assertTrue(engine.grains.all { it.x.isFinite() && it.y.isFinite()
            && it.vx.isFinite() && it.vy.isFinite() && it.heat >= 0.0 })
        assertTrue("4600 grain dense-cell contact scan was unbounded: " +
            engine.counters.first, engine.counters.first < 4600L * 3 * 64)
        // CI is shared and is not an Android FPS benchmark. This is only
        // a generous regression watchdog for catastrophic multi-second stalls.
        assertTrue("4600 grain watchdog: " + durationMs + "ms",
            durationMs < 60_000)
    }

    @Test fun cohesionAttractsIndependentNearbyRockGrainsWithoutMomentumSource() {
        val engine = GranularUniverse()
        engine.grains.add(grain(1, .491f, .5f))
        engine.grains.add(grain(2, .496f, .5f))
        val before = engine.totals()
        engine.step(1f/60f, 0f)
        assertTrue(engine.grains[0].vx > 0f)
        assertTrue(engine.grains[1].vx < 0f)
        assertEquals(before.mass, engine.totals().mass, 1e-8)
        assertEquals(before.momentumX, engine.totals().momentumX, 2e-7)
        assertEquals(2, engine.grains.size)
    }

    @Test fun hotPairChemistryBalancesChemicalPotentialAndHeat() {
        val engine = GranularUniverse()
        val h = SandGrain(1, .49f, .5f, 0f, 0f, .02f,
            .02 * .000001 * 800, MatterMaterial.HYDROGEN)
        val oxygen = SandGrain(2, .493f, .5f, 0f, 0f, .015f,
            .015 * .000001 * 800, MatterMaterial.OXYGEN)
        engine.grains.add(h); engine.grains.add(oxygen)
        val before = engine.totals()
        assertTrue(engine.reactPair(h, oxygen))
        val after = engine.totals()
        assertEquals(before.mass, after.mass, 1e-8)
        assertEquals(before.momentumX, after.momentumX, 1e-8)
        assertEquals(before.internal + before.chemical,
            after.internal + after.chemical, 1e-10)
        assertTrue(h.heat >= 0.0 && oxygen.heat >= 0.0)
        assertEquals(MatterMaterial.STEAM, h.material)
        assertEquals(MatterMaterial.STEAM, oxygen.material)
    }

    @Test fun coldPairDoesNotReactAndStarContainsMostlyGas() {
        val engine = GranularUniverse()
        val h = SandGrain(1, .49f, .5f, 0f, 0f, .01f,
            .01 * .000001 * 180, MatterMaterial.HYDROGEN)
        val oxygen = SandGrain(2, .493f, .5f, 0f, 0f, .01f,
            .01 * .000001 * 180, MatterMaterial.OXYGEN)
        assertTrue(!engine.reactPair(h, oxygen))
        val star = CelestialBody(21, 3, 1.8f, 1f, .5f, .5f, 0f, 0f,
            stellarClass = StellarClass.STAR, material = MatterMaterial.HYDROGEN)
        engine.seed(star, 100)
        assertTrue(engine.grains.count { it.material.gas } >= 90)
        assertEquals(star.mass.toDouble(), engine.totals().mass, 1e-5)
    }

    @Test fun incomingPlanetIsCircularButItsGrainsAreNotPinned() {
        val world = CosmicWorld(911)
        val count = world.sand.grains.size
        val originalMass = world.sand.totals().mass
        world.spawnFromCorner(physicalCluster = true)
        assertEquals(count + world.sand.grains.takeLast(105).count {
            it.owner == world.bodies.last().id
        }, world.sand.grains.size)
        assertTrue(world.sand.grains.size >= count + 72)
        assertTrue(world.sand.grains.size <= count + 105)
        assertTrue(world.sand.totals().mass > originalMass)
        val newborn = world.bodies.last()
        val samples = world.sand.grains.filter { it.owner == newborn.id }
        assertTrue(samples.all { it.vx == newborn.vx && it.vy == newborn.vy })
        val radials = samples.map {
            hypot(it.x - newborn.x, it.y - newborn.y)
        }
        val radius = radials.maxOrNull() ?: 0f
        assertTrue(radius > 0f)
        assertTrue(radials.minOrNull()!! < radius * .45f)
        val before = samples.map { it.x to it.y }
        repeat(10) { world.step(1f/60f) }
        assertTrue(samples.indices.any { i ->
            hypot(samples[i].x-before[i].first,
                samples[i].y-before[i].second) > .00001f
        })
    }

    @Test fun pulsingStarUsesExistingHeatAndPreservesGrainMomentum() {
        val sand = GranularUniverse()
        sand.grains.add(SandGrain(100, .48f, .5f, 0f, 0f,
            .01f, .0001, MatterMaterial.HYDROGEN, 7))
        sand.grains.add(SandGrain(101, .52f, .5f, 0f, 0f,
            .01f, .0001, MatterMaterial.PLASMA, 7))
        val before = sand.totals()
        val ids = sand.grains.map { it.id }
        val e1 = CosmicEpisode(CosmicEventKind.PULSAR,
            age = .8f, duration = 17f, x = .5f, y = .5f, serial = 43)
        sand.step(1f/60f, 0f, e1)
        val first = sand.totals()
        assertTrue(first.kinetic > before.kinetic)
        assertTrue(first.internal < before.internal)
        assertEquals(before.mass, first.mass, 1e-8)
        assertEquals(before.momentumX, first.momentumX, 1e-7)
        assertEquals(before.momentumY, first.momentumY, 1e-7)
        // Same pulse ID cannot spend heat twice.
        val heatBefore = sand.totals().internal
        sand.step(1f/60f, 0f, e1)
        assertEquals(heatBefore, sand.totals().internal, 1e-9)
        val e2 = e1.copy(age = 1.5f)
        sand.step(1f/60f, 0f, e2)
        assertTrue(sand.totals().internal < heatBefore)
        assertEquals(ids, sand.grains.map { it.id })
    }

    @Test fun fieryMeteorShowerAddsOnlyGenuineIncomingGrains() {
        val world = CosmicWorld(24)
        val before = world.sand.grains.size
        val massBefore = world.sand.totals().mass
        world.trigger(CosmicEventKind.METEORS)
        val added = world.sand.grains.drop(before)
        assertTrue(added.size in 52..78)
        assertTrue(added.all { it.y < 0f && it.vy > .19f })
        assertTrue(added.any { it.material == MatterMaterial.PLASMA ||
            it.material == MatterMaterial.SULFUR })
        assertTrue(world.sand.totals().mass > massBefore)
        val duringMass = world.sand.totals().mass
        repeat(10) { world.step(1f/60f) }
        assertEquals(duringMass, world.sand.totals().mass, 1e-5)
    }

    @Test fun hotQuasarJetDoesNotFabricateMatter() {
        val sand = GranularUniverse()
        val star = CelestialBody(52, 3, 2f, 1f,
            .5f, .5f, 0f, 0f,
            material = MatterMaterial.PLASMA,
            temperature = 6500f, stellarClass = StellarClass.QUASAR)
        sand.seed(star, 90)
        val ids = sand.grains.map { it.id }.toSet()
        val before = sand.totals()
        repeat(10) { step ->
            val jet = CosmicEpisode(CosmicEventKind.QUASAR_JET,
                age = step*.72f, duration = 15f,
                x = .5f, y = .5f, serial = 55)
            sand.step(1f/30f, 1f, jet)
        }
        assertEquals(ids, sand.grains.map { it.id }.toSet())
        assertEquals(before.mass, sand.totals().mass, 1e-6)
        assertTrue(sand.grains.all {
            it.x.isFinite() && it.y.isFinite() && it.heat >= 0
        })
    }

    @Test fun worldWithActiveEventHasConstantMassAndCount() {
        val world = CosmicWorld(321)
        val before = world.sand.totals()
        val count = world.sand.grains.size
        world.trigger(CosmicEventKind.NOVA)
        repeat(65) { world.step(1f / 60f) }
        assertEquals(count, world.sand.grains.size)
        assertEquals(before.mass, world.sand.totals().mass, 1e-5)
    }
}

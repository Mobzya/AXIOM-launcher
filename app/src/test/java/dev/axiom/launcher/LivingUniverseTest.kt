package dev.axiom.launcher

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class ClockContourTest {
    private fun ring():GlyphDistanceField {
        val alpha=ByteArray(21*21)
        for(y in 4..16)for(x in 4..16)if(x in 4..6 || x in 14..16 || y in 4..6 || y in 14..16)alpha[y*21+x]=255.toByte()
        return GlyphDistanceField.fromAlpha(21,21,alpha)
    }
    @Test fun distanceTransformMatchesBruteForcePixelDistances() {
        val w=13;val h=9;val alpha=ByteArray(w*h){if(it%7==0 || it%13==4)255.toByte() else 0}
        val field=GlyphDistanceField.fromAlpha(w,h,alpha)
        for(y in 0 until h)for(x in 0 until w) {
            val ink=alpha[y*w+x].toInt()!=0
            var best=Double.POSITIVE_INFINITY
            for(j in 0 until h)for(i in 0 until w)
                if((alpha[j*w+i].toInt()!=0)!=ink)best=min(best,hypot((x-i).toDouble(),(y-j).toDouble()))
            val expected=(best-.5)*(if(ink)-1 else 1)
            assertEquals(expected,field.distance(x.toFloat(),y.toFloat()).toDouble(),1e-5)
        }
    }
    @Test fun holesArePassableWhileInkReflectsWithoutTangentialBraking() {
        val obstacle=ClockObstacle(ring(),0f,0f,1f)
        assertTrue(obstacle.distance(10f,10f)>2f)
        assertNull(obstacle.collide(10f,10f,1f,0f,.6f))
        val hit=obstacle.collide(4.1f,10f,1f,.35f,.6f)!!
        assertTrue(hit.x<4f)
        assertTrue(hit.vx<0)
        assertEquals(.35f,hit.vy,1e-6f)
        assertTrue(obstacle.distance(hit.x,hit.y)>=.59f)
        assertEquals(1f+.35f*.35f,hit.vx*hit.vx+hit.vy*hit.vy,1e-5f)
    }
    @Test fun replacingTheMinuteGlyphRemovesOldCollisions() {
        val before=ClockObstacle(ring(),0f,0f,1f)
        val after=ClockObstacle(GlyphDistanceField.fromAlpha(21,21,ByteArray(441)),0f,0f,1f)
        assertNotNull(before.collide(4.1f,10f,1f,0f,.5f))
        assertNull(after.collide(4.1f,10f,1f,0f,.5f))
    }
    @Test fun entirePortraitScreenMapsToWallsAboveAndBelowTheOldSquare() {
        val view=UniverseViewport(400f,900f)
        val bounds=view.screenBounds(2f)
        assertTrue(bounds.top<-.5f);assertTrue(bounds.bottom>1.5f)
        val p=view.screenPoint(180f,24f)!!
        assertTrue(p.second<0f);assertEquals(24f,view.y(p.second),1e-4f)
        assertNull(view.normalized(180f,24f))
        val u=AstronomicalUniverse()
        val y=bounds.top+.0006f
        u.grains.add(SandGrain(1,.5f,y,0f,-.1f,.001f,.000001,MatterMaterial.IRON))
        u.step(1f/60f,0f,walls=true,bounds=bounds)
        assertTrue(u.grains[0].vy>0f)
        assertTrue(u.grains[0].y<0f)
    }
    @Test fun grainContactUsesGlyphWorldCoordinatesRatherThanItsRectangle() {
        val u=AstronomicalUniverse()
        val wall=ClockObstacle(ring(),.3f,-.4f,.001f)
        u.grains.add(SandGrain(1,.3041f,-.390f,.01f,0f,.001f,.000001,MatterMaterial.IRON))
        u.step(1f/240f,0f,walls=true,bounds=SpaceBounds(0f,-.8f,1f,1.8f),clock=wall)
        assertTrue(u.grains[0].vx<0f)
        assertTrue(wall.distance(u.grains[0].x,u.grains[0].y)>.0003f)
    }
}

class CompactObjectTest {
    @Test fun blackHoleIsOneRealMassAndCapturePreservesMomentumAndSpinBudget() {
        val u=AstronomicalUniverse()
        u.seed(CelestialBody(1,99,10f,1f,.5f,.5f,.01f,.02f,
            nature=CelestialNature.BLACK_HOLE),300)
        assertEquals(1,u.grains.size);assertEquals(10.0,u.totals().mass,1e-6)
        u.grains.add(SandGrain(5,.506f,.5f,-.03f,.07f,.2f,.0001,MatterMaterial.ICE,2))
        u.grains.add(SandGrain(6,.7f,.6f,.02f,0f,.1f,.0001,MatterMaterial.ROCK,3))
        val before=u.totals();val angular=angular(u)
        val result=u.accrete(1,.015f)
        assertEquals(.2,result.consumedMass,1e-6)
        assertEquals(2,u.grains.size)
        val after=u.totals()
        assertEquals(before.mass,after.mass,1e-6)
        assertEquals(before.momentumX,after.momentumX,1e-7)
        assertEquals(before.momentumY,after.momentumY,1e-7)
        assertEquals(angular,angular(u)+result.spin,1e-7)
        assertEquals(before.kinetic+before.internal+before.chemical,
            after.kinetic+after.internal+after.chemical+u.radiatedHeat,1e-7)
    }
    @Test fun newlySeededCloudHasTheRequestedCenterMomentumAndDoesNotCollapseIntoHeat() {
        val u=AstronomicalUniverse()
        val body=CelestialBody(1,12,9f,1f,.4f,.6f,.013f,-.021f,temperature=5000f,stellarClass=StellarClass.STAR)
        u.seed(body,190)
        val initial=u.totals();val before=u.summarizeBodies(listOf(body)).single()
        assertEquals(body.x,before.x,1e-6f);assertEquals(body.y,before.y,1e-6f)
        assertEquals(body.mass*body.vx,initial.momentumX.toFloat(),1e-6f)
        assertEquals(body.mass*body.vy,initial.momentumY.toFloat(),1e-6f)
        repeat(240){u.step(1f/60f,0f)}
        val after=u.summarizeBodies(listOf(body)).single()
        assertTrue("Virial start should prevent artificial formation heating",abs(after.temperature-before.temperature)<100f)
    }
    @Test fun ordinaryClimateHeatingAndCoolingPreserveTheFiniteThermalLedger() {
        val u=AstronomicalUniverse()
        val star=CelestialBody(1,12,9f,1f,.5f,.5f,0f,0f,temperature=5000f,stellarClass=StellarClass.STAR)
        val planet=CelestialBody(2,2,.1f,1f,.75f,.5f,0f,0f,temperature=130f)
        u.seed(star,80);u.seed(planet,20)
        val before=u.totals();u.exchangeStellarRadiation(listOf(star,planet),1f)
        val after=u.totals()
        assertEquals(before.mass,after.mass,0.0)
        assertEquals(before.internal,after.internal+u.radiatedHeat,1e-9)
        assertTrue(u.summarizeBodies(listOf(planet)).single().temperature>130)
        val hot=planet.copy(temperature=1400f)
        for(p in u.grains)if(p.owner==2)p.heat=p.mass*.000001*1400
        val energy=u.totals().internal+u.radiatedHeat
        u.exchangeStellarRadiation(listOf(star,hot),1f)
        assertTrue(u.radiatedHeat>0)
        assertEquals(energy,u.totals().internal+u.radiatedHeat,1e-9)
    }
    @Test fun emptyHorizonCannotFabricateMassOrConsumeDistantMatter() {
        val world=CosmicWorld(42,initialPreset=CosmicPreset.BLACK_HOLE_NURSERY)
        val core=world.bodies.first();val n=world.sand.grains.size;val mass=world.sand.totals().mass
        val result=world.sand.accrete(core.id,.001f)
        assertEquals(0.0,result.consumedMass,0.0)
        assertEquals(n,world.sand.grains.size);assertEquals(mass,world.sand.totals().mass,0.0)
    }
    @Test fun stellarRemnantsAreActuallyCompactAndEachRecipeCreatesItsNature() {
        val world=CosmicWorld(33,initialPreset=CosmicPreset.LIVING_SYSTEM)
        for(recipe in listOf(BodyRecipe.BLACK_HOLE,BodyRecipe.NEUTRON,BodyRecipe.WHITE_DWARF,
            BodyRecipe.COMET,BodyRecipe.ASTEROID)) {
            assertTrue(world.createObject(recipe.material,recipe.mass,recipe=recipe))
            assertEquals(recipe.nature,world.bodies.last().nature)
        }
        val neutron=world.bodies.first {it.nature==CelestialNature.NEUTRON_STAR}
        assertTrue(world.sand.grains.count {it.owner==neutron.id}<=12)
    }
    @Test fun defenseChargeTransfersItsFiniteEnergyWithoutDeletingMass() {
        val u=AstronomicalUniverse()
        u.seed(CelestialBody(5,1,.2f,1f,.5f,.5f,.03f,-.02f,nature=CelestialNature.ASTEROID),30)
        val before=u.totals();val energy=.0008
        assertTrue(u.disruptBody(5,energy))
        val after=u.totals()
        assertEquals(before.mass,after.mass,0.0)
        assertEquals(before.momentumX,after.momentumX,1e-7)
        assertEquals(before.momentumY,after.momentumY,1e-7)
        assertEquals(energy,after.kinetic-before.kinetic,1e-8)
        assertEquals(30,u.grains.size)
    }
    private fun angular(u:AstronomicalUniverse)=u.grains.sumOf {it.mass.toDouble()*(it.x.toDouble()*it.vy-it.y.toDouble()*it.vx)}
}

class PhenomenaAndQuantumTest {
    @Test fun radiationHasAnExternalEnergyLedgerAndDoesNotWarmOutsideTheBeam() {
        val u=AstronomicalUniverse()
        u.grains.add(SandGrain(1,.5f,.5f,0f,0f,.1f,.00001,MatterMaterial.IRON))
        u.grains.add(SandGrain(2,.5f,.8f,0f,0f,.1f,.00001,MatterMaterial.IRON))
        val event=CosmicEpisode(CosmicEventKind.GAMMA_BURST,age=8f,duration=10f,
            x=0f,y=.5f,serial=42,intensity=CosmicEventIntensity.CATASTROPHIC,axis=0f)
        val before=u.totals()
        u.step(1f/240f,0f,event)
        assertTrue(u.grains[0].heat>.00001);assertEquals(.00001,u.grains[1].heat,1e-12)
        val heat=u.totals().internal;val energy=u.externalRadiationEnergy
        assertEquals(before.mass,u.totals().mass,0.0)
        assertEquals(heat-before.internal,energy,1e-9)
        val frame=u.snapshot()
        u.step(1f/240f,0f,event);assertEquals(energy,u.externalRadiationEnergy,0.0)
        u.restore(frame);u.step(1f/240f,0f,event)
        assertEquals(energy,u.externalRadiationEnergy,0.0)
    }
    @Test fun extremeEventsUseActiveTimeAndWaitUntilTheSceneIsAvailable() {
        val director=ExtremeEventDirector(Random(5));val start=director.remaining
        assertNull(director.advance(1_000_000f,false,CatastropheRate.NATURAL,true))
        assertEquals(start,director.remaining,0.0)
        assertNull(director.advance(1_000_000f,true,CatastropheRate.NATURAL,false))
        assertNotNull(director.advance(.01f,true,CatastropheRate.NATURAL,true))
        assertTrue(director.remaining>0)
    }
    @Test fun perKindRarityHasHoursOfExpectedActiveTimeRatherThanMinuteTimers() {
        val expected=1/extremePhenomena.sumOf {1/(it.meanHours*3600)}
        val average=(0 until 1000).map {ExtremeEventDirector(Random(it)).remaining}.average()
        assertEquals(expected,average,expected*.12)
        assertTrue(extremePhenomena.all {it.meanHours>=4})
    }
    @Test fun unitaryEvolutionHasTheExpectedRabiProbabilityAndRemainsNormalized() {
        val q=QuantumExperiment(Random(2))
        repeat(600){q.step((PI/600).toFloat(),1f,0f)}
        assertTrue(q.reading.leftProbability<1e-8)
        repeat(600){q.step((PI/600).toFloat(),1f,0f)}
        assertTrue(q.reading.leftProbability>.999999)
        assertTrue(q.reading.coherence in 0.0..1.0)
    }
    @Test fun dephasingReducesCoherenceAndBornMeasurementsMatchPreparedStatistics() {
        val q=QuantumExperiment(Random(6));q.resetSuperposition()
        q.step(1f,1f,1f);assertEquals(exp(-1.0),q.reading.coherence,1e-6)
        repeat(4000){q.resetSuperposition();q.measure()}
        assertEquals(4000,q.reading.measurements)
        assertTrue(q.reading.leftResults in 1880..2120)
        assertEquals(0.0,q.reading.coherence,0.0)
    }
    @Test fun quantumSnapshotRestoresAmplitudesAndMeasurementCounts() {
        val q=QuantumExperiment();q.step(.7f);q.measure();q.step(.3f)
        val frame=q.snapshot();val before=q.reading
        q.measure();q.step(1f);q.restore(frame)
        assertEquals(before,q.reading)
    }
    @Test fun hourlyCyclesAvoidReloadingOnEveryPollAndHonorTheSelectedPool() {
        val cycle=HourlySceneCycle();val pool=listOf(CosmicPreset.LIVING_SYSTEM,CosmicPreset.BLACK_HOLE_NURSERY)
        assertNull(cycle.check(40,CosmicPreset.LIVING_SYSTEM,pool,true))
        assertNull(cycle.check(40,CosmicPreset.LIVING_SYSTEM,pool,true))
        assertEquals(CosmicPreset.BLACK_HOLE_NURSERY,cycle.check(41,CosmicPreset.LIVING_SYSTEM,pool,true)!!.preset)
        assertNull(cycle.check(41,CosmicPreset.BLACK_HOLE_NURSERY,pool,true))
        assertNull(cycle.check(90,CosmicPreset.BLACK_HOLE_NURSERY,pool,false))
        assertNull(cycle.check(91,CosmicPreset.BLACK_HOLE_NURSERY,pool,true))
    }
    @Test fun hourlyWorldReloadRestartsMatterAndColoniesButKeepsThePause() {
        val world=CosmicWorld(7,initialPreset=CosmicPreset.LIVING_SYSTEM)
        world.configureObservation(events=false)
        world.configureCosmos(hourly=true,pool=setOf(CosmicPreset.LIVING_SYSTEM,CosmicPreset.BLACK_HOLE_NURSERY))
        assertFalse(world.checkSceneHour(1))
        repeat(90){world.step(1f/60f)}
        world.updateTimeScale(0f)
        assertTrue(world.checkSceneHour(2));assertEquals(0f,world.elapsed,0f)
        assertEquals(0f,world.timeScale,0f);assertEquals(CosmicPreset.BLACK_HOLE_NURSERY,world.preset)
        assertTrue(world.civilization.colonies.isEmpty())
        assertEquals(world.grainFrames.current.size,world.grainFrames.previous.size)
    }
}

class CivilizationTest {
    private fun planet(id:Int,x:Float=.3f,y:Float=.5f,temp:Float=292f)=
        CelestialBody(id,2,.4f,1f,x,y,0f,0f,temperature=temp)
    private val config=CivilizationConfig(mode=LifeMode.SEEDED)
    @Test fun coloniesNeedAHabitableWorldAndDieQuicklyInViolentConditions() {
        val sim=CivilizationSimulation(Random(4));val home=planet(1)
        val cold=planet(2,.7f,temp=60f)
        assertFalse(sim.seed(cold,listOf(home,cold)))
        assertTrue(sim.seed(home,listOf(home,cold)))
        val star=CelestialBody(3,12,12f,1f,.301f,.501f,0f,0f,stellarClass=StellarClass.STAR,temperature=5000f)
        repeat(160){sim.step(.05f,listOf(home,cold,star),1f,1f,emptyMap(),config,true)}
        assertTrue(sim.reading.colonies.isEmpty());assertEquals(1,sim.reading.extinct)
    }
    @Test fun shipsSpendResourcesAndReachANewPlanetWithoutTeleportation() {
        val sim=CivilizationSimulation(Random(1));val bodies=listOf(planet(1),planet(2,.7f))
        assertTrue(sim.seed(bodies[0],bodies))
        var sawFlight=false;var spent=false
        repeat(900) {
            val before=sim.reading.colonies.firstOrNull {it.bodyId==1}?.resources ?: 0.0
            sim.step(.05f,bodies,0f,0f,emptyMap(),config,true)
            if(sim.reading.ships.isNotEmpty()) {
                sawFlight=true
                if(sim.reading.colonies.first().resources<before-40)spent=true
            }
        }
        assertTrue("A real flight should precede settlement",sawFlight)
        assertTrue("Launching must spend accumulated resources",spent)
        assertTrue("The autopilot should land on a quiet destination",sim.reading.colonies.any {it.bodyId==2})
        assertTrue(sim.reading.colonies.all {it.resources in 0.0..250.0})
    }
    @Test fun radiationCanSterilizeAnOtherwiseStableColony() {
        val sim=CivilizationSimulation();val home=planet(1)
        assertTrue(sim.seed(home,listOf(home)))
        repeat(100){sim.step(.05f,listOf(home),0f,0f,mapOf(1 to 3f),config,true)}
        assertTrue(sim.reading.colonies.isEmpty());assertTrue(sim.reading.extinct>0)
    }
    @Test fun interceptionHasBothSuccessesAndMissesAndUsesFiniteBudgets() {
        var hits=0;var misses=0
        for(seed in 0 until 60) {
            val sim=CivilizationSimulation(Random(seed));val home=planet(1,.5f)
            val initial=CelestialBody(2,1,.035f,1f,.2f,.5f,.035f,0f,
                temperature=150f,nature=CelestialNature.ASTEROID)
            assertTrue(sim.seed(home,listOf(home)))
            for(i in 0 until 170) {
                val asteroid=initial.copy(x=initial.x+initial.vx*i*.05f)
                val det=sim.step(.05f,listOf(home,asteroid),0f,0f,emptyMap(),config.copy(colonization=false),true)
                if(det.isNotEmpty() || sim.reading.misses>0)break
            }
            hits+=sim.reading.interceptions;misses+=sim.reading.misses
            assertTrue(sim.reading.colonies.all {it.resources>=0})
            assertTrue(sim.reading.ships.size<=28)
        }
        println("AXIOM defence trial: hits=$hits misses=$misses")
        assertTrue("Interception must really be possible",hits>0)
        assertTrue("Imperfect navigation must permit a miss",misses>0)
    }
    @Test fun logisticLearnerImprovesItsForecastFromActualLabelsAndRestoresState() {
        val model=SurvivalLearner();val safe=doubleArrayOf(1.0,.9,.9,0.0,.1)
        val dangerous=doubleArrayOf(1.0,.4,.2,1.4,.8)
        val before=(1-model.predict(safe)).pow(2)+model.predict(dangerous).pow(2)
        repeat(400){model.learn(safe,true);model.learn(dangerous,false)}
        val after=(1-model.predict(safe)).pow(2)+model.predict(dangerous).pow(2)
        assertTrue(after<before*.4);assertTrue(model.brier!!>=0)
        val s=model.snapshot();val p=model.predict(safe);model.learn(safe,false);model.restore(s)
        assertEquals(p,model.predict(safe),0.0)
    }
    @Test fun disabledLearningDoesNotTrainAndSnapshotsRestoreTravellersAndResources() {
        val sim=CivilizationSimulation(Random(1));val bodies=listOf(planet(1),planet(2,.7f))
        sim.seed(bodies[0],bodies)
        repeat(270){sim.step(.05f,bodies,0f,0f,emptyMap(),config,false)}
        assertEquals(0,sim.reading.survivalSamples);assertTrue(sim.reading.ships.isNotEmpty())
        val snapshot=sim.snapshot();val before=sim.reading
        repeat(50){sim.step(.05f,bodies,0f,0f,emptyMap(),config,true)}
        sim.restore(snapshot);assertEquals(before,sim.reading)
    }
    @Test fun livingSystemCanLaunchAndSettleUnderTheActualGravitationalSolver() {
        val world=CosmicWorld(52,initialPreset=CosmicPreset.LIVING_SYSTEM)
        world.configureObservation(events=false);world.updateTimeScale(2f)
        repeat(1800){world.step(1f/60f)}
        println("Living system: temperatures="+world.bodies.map {it.id to it.temperature}+
            ", launches="+world.civilization.launches+", settlements="+world.civilization.settlements)
        assertTrue("At least one ship should launch",world.civilization.launches>0)
        assertTrue("At least one colony ship should really land",world.civilization.settlements>0)
        assertTrue("Quiet systems should sustain surviving colonies",world.civilization.colonies.isNotEmpty())
    }

    @Test fun lifeDisabledAndAllCapsKeepLongRunsBounded() {
        val sim=CivilizationSimulation();val bodies=listOf(planet(1),planet(2,.7f))
        repeat(1000){sim.step(.1f,bodies,1f,1f,emptyMap(),config.copy(mode=LifeMode.OFF),true)}
        assertTrue(sim.reading.colonies.isEmpty());assertTrue(sim.reading.ships.isEmpty())
        val world=CosmicWorld(52,initialPreset=CosmicPreset.LIVING_SYSTEM)
        world.configureObservation(events=false)
        repeat(1000){world.step(1f/60f)}
        assertTrue(world.civilization.colonies.isNotEmpty())
        assertTrue(world.civilization.colonies.size<=16 && world.civilization.ships.size<=28)
        assertTrue(world.sand.grains.size<=world.matterMode.maxParticles)
    }
}

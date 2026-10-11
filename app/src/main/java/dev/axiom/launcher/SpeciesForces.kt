package dev.axiom.launcher

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Twelve subtle species-specific physical tendencies for 62 fine-detail bodies,
 * and eight more for the giant families. Mutations apply directly to the
 * existing acceleration buffers, without allocations inside the 60Hz loop.
 * These are artistic external fields; pairwise gravity still uses Newton's 3rd.
 */
internal fun addSpeciesForces(
    bodies: List<CelestialBody>, time: Float,
    ax: FloatArray, ay: FloatArray
) {
    for(i in bodies.indices){
        val b=bodies[i]
        if(b.kind !in 35..104) continue
        val family=if(b.kind<=96)(b.kind-35)%12 else b.kind-97
        val intensity=(.00032f+(b.id%5)*.00005f)/
            b.depth.coerceAtLeast(.5f)
        val x=b.x-.5f
        val y=b.y-.5f
        val d=hypot(x,y).coerceAtLeast(.06f)
        when(family) {
            0 -> { // Accretion: thin tangential orbital flow.
                ax[i]+=-intensity*y/d
                ay[i]+=intensity*x/d
            }
            1 -> { // Magnetized ion: weak Lorentz-like deflection.
                ax[i]+=-intensity*b.vy*4f
                ay[i]+=intensity*b.vx*4f
            }
            2 -> { // Quasar: polar acceleration.
                ay[i]+=intensity*sin(b.rotation)*.7f
            }
            3 -> { // Galactic swirl.
                ax[i]+=-intensity*y*.5f
                ay[i]+=intensity*x*.5f
            }
            4 -> { // Compact lens, gentle inward drift.
                ax[i]-=intensity*x/d*.55f
                ay[i]-=intensity*y/d*.55f
            }
            5 -> { // Lattice: attraction toward nearby grid nodes.
                val gx=kotlin.math.round(b.x*5f)/5f
                val gy=kotlin.math.round(b.y*5f)/5f
                ax[i]+=(gx-b.x)*intensity*2f
                ay[i]+=(gy-b.y)*intensity*2f
            }
            6 -> { // Triple system: stable weak epicyclic forcing.
                ax[i]+=intensity*sin(time*.3f+b.id)
                ay[i]+=intensity*cos(time*.3f+b.id)
            }
            7 -> { // Planet: atmospheric drag.
                ax[i]-=intensity*b.vx*.45f
                ay[i]-=intensity*b.vy*.45f
            }
            8 -> { // Shell: pressure wave.
                val wave=sin(b.age*.72f+b.rotation)
                ax[i]+=intensity*x/d*wave
                ay[i]+=intensity*y/d*wave
            }
            9 -> { // Filament: deterministic small turbulence.
                ax[i]+=intensity*sin(b.y*11f+time*.21f)
                ay[i]+=intensity*cos(b.x*11f-time*.22f)
            }
            10 -> { // Crystal: tiny settling / anisotropic damping.
                ax[i]-=intensity*b.vx*.35f
                ay[i]-=intensity*b.vy*.62f
            }
            11 -> { // Helical stream.
                ax[i]+=intensity*cos(b.age*.7f+b.rotation)
                ay[i]+=intensity*sin(b.age*.7f+b.rotation)
            }
        }
        if(b.kind in 97..104){
            // Eight rare giants displace neighboring trajectories primarily
            // through their ordinary large physical mass. Weak extra precession
            // is species-dependent, never an unlimited strong attractor.
            val extra=(b.kind-97)*.000015f
            ax[i]-=extra*y/d
            ay[i]+=extra*x/d
        }
    }
}

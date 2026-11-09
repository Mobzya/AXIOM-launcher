package dev.axiom.launcher

import kotlin.math.*

/** Isotropic world units; wall mode can use the entire portrait/landscape area. */
internal data class SpaceBounds(val left:Float=0f,val top:Float=0f,
    val right:Float=1f,val bottom:Float=1f) {
    init { require(right>left && bottom>top) }
    fun contains(x:Float,y:Float)=x in left..right && y in top..bottom
}

/** Immutable signed distance field, sampled at pixel centers. Negative = ink.
 * Exact separable Euclidean distance transform, O(width × height), once/minute.
 * Holes in the glyphs remain holes. No bounding-box collision approximation. */
internal class GlyphDistanceField private constructor(val width:Int,val height:Int,
    private val distances:FloatArray) {
    fun distance(x:Float,y:Float):Float {
        if(x < -.5f || y < -.5f || x > width-.5f || y > height-.5f) {
            val cx=x.coerceIn(0f,(width-1).toFloat());val cy=y.coerceIn(0f,(height-1).toFloat())
            return hypot(x-cx,y-cy)+sample(cx,cy).coerceAtLeast(0f)
        }
        return sample(x,y)
    }
    private fun sample(x:Float,y:Float):Float {
        val px=x.coerceIn(0f,(width-1).toFloat());val py=y.coerceIn(0f,(height-1).toFloat())
        val x0=floor(px).toInt();val y0=floor(py).toInt()
        val x1=min(x0+1,width-1);val y1=min(y0+1,height-1)
        val a=px-x0;val b=py-y0
        return (distances[y0*width+x0]*(1-a)+distances[y0*width+x1]*a)*(1-b)+
            (distances[y1*width+x0]*(1-a)+distances[y1*width+x1]*a)*b
    }
    companion object {
        fun fromAlpha(width:Int,height:Int,alpha:ByteArray):GlyphDistanceField {
            require(width>0 && height>0 && width.toLong()*height<=2_000_000L)
            require(alpha.size==width*height)
            val ink=BooleanArray(alpha.size) {(alpha[it].toInt() and 255)>=96}
            val outside=transform(width,height,ink,true)
            val inside=transform(width,height,ink,false)
            return GlyphDistanceField(width,height,FloatArray(alpha.size) {
                if(ink[it]) -(sqrt(inside[it])-.5f).coerceAtLeast(.05f)
                else (sqrt(outside[it])-.5f).coerceAtLeast(.05f)
            })
        }
        private fun transform(w:Int,h:Int,mask:BooleanArray,target:Boolean):FloatArray {
            val inf=1e12f
            val temp=FloatArray(w*h);val out=FloatArray(w*h)
            val size=max(w,h);val input=FloatArray(size);val output=FloatArray(size)
            val vertices=IntArray(size);val boundaries=DoubleArray(size+1)
            fun edt(n:Int) {
                var k=0;vertices[0]=0;boundaries[0]=Double.NEGATIVE_INFINITY
                boundaries[1]=Double.POSITIVE_INFINITY
                for(q in 1 until n) {
                    var v=vertices[k]
                    var s=((input[q].toDouble()+q.toDouble()*q)-(input[v]+v.toDouble()*v))/(2.0*(q-v))
                    while(s<=boundaries[k] && k>0) {
                        k--;v=vertices[k]
                        s=((input[q].toDouble()+q.toDouble()*q)-(input[v]+v.toDouble()*v))/(2.0*(q-v))
                    }
                    k++;vertices[k]=q;boundaries[k]=s;boundaries[k+1]=Double.POSITIVE_INFINITY
                }
                k=0
                for(q in 0 until n) {
                    while(boundaries[k+1]<q)k++
                    val d=q-vertices[k];output[q]=d.toFloat()*d+input[vertices[k]]
                }
            }
            for(y in 0 until h) {
                for(x in 0 until w)input[x]=if(mask[y*w+x]==target)0f else inf
                edt(w);for(x in 0 until w)temp[y*w+x]=output[x]
            }
            for(x in 0 until w) {
                for(y in 0 until h)input[y]=temp[y*w+x]
                edt(h);for(y in 0 until h)out[y*w+x]=output[y]
            }
            return out
        }
    }
}

internal data class ClockObstacle(val field:GlyphDistanceField,val left:Float,
    val top:Float,val pixelUnit:Float,val minParticleRadius:Float=0f) {
    fun distance(x:Float,y:Float)=field.distance((x-left)/pixelUnit,(y-top)/pixelUnit)*pixelUnit
    fun nearby(x:Float,y:Float,r:Float)=x>=left-r && y>=top-r &&
        x<=left+field.width*pixelUnit+r && y<=top+field.height*pixelUnit+r

    /** Project an overlap along the glyph normal, then reflect only inward motion.
     * The clock is an external kinematic wall, so it can exchange momentum. */
    fun collide(x:Float,y:Float,vx:Float,vy:Float,radius:Float):ClockContact? {
        val radius=max(radius,minParticleRadius)
        if(!nearby(x,y,radius))return null
        var px=x;var py=y;var nx=0f;var ny=0f;var touched=false
        repeat(5) {
            val d=distance(px,py)
            if(d>=radius)return@repeat
            val e=pixelUnit*.8f
            var gx=distance(px+e,py)-distance(px-e,py)
            var gy=distance(px,py+e)-distance(px,py-e)
            var norm=hypot(gx,gy)
            if(norm<1e-7f) {
                // Symmetric stroke centers have no unique gradient: select the
                // nearest of eight escape directions, never a digit rectangle.
                var best=Float.NEGATIVE_INFINITY
                for(k in 0 until 8) {
                    val a=k*PI.toFloat()/4f;val dx=cos(a);val dy=sin(a)
                    val probe=distance(px+dx*e*2,py+dy*e*2)
                    if(probe>best){best=probe;gx=dx;gy=dy}
                }
                norm=hypot(gx,gy)
            }
            nx=gx/norm;ny=gy/norm
            val shift=(radius-d+pixelUnit*.05f).coerceAtMost(pixelUnit*12f+radius)
            px+=nx*shift;py+=ny*shift;touched=true
        }
        if(!touched)return null
        val normal=vx*nx+vy*ny
        return ClockContact(px,py,if(normal<0)vx-2*normal*nx else vx,
            if(normal<0)vy-2*normal*ny else vy)
    }
}
internal data class ClockContact(val x:Float,val y:Float,val vx:Float,val vy:Float)

internal fun UniverseViewport.screenBounds(insetPixels:Float=2f):SpaceBounds {
    val p=insetPixels.coerceIn(0f,side*.1f)
    return SpaceBounds((p-left)/side,(p-top)/side,
        (width-p-left)/side,(height-p-top)/side)
}
internal fun UniverseViewport.screenPoint(x:Float,y:Float):Pair<Float,Float>? =
    if(x in 0f..width && y in 0f..height) (x-left)/side to (y-top)/side else null

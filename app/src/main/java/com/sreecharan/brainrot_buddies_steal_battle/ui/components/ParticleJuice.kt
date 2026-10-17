package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Particle juice for the game board, contract §10: bursts of 5-20 pooled
 * sprites, <= 1000 total alive, simulation under 2 ms.
 *
 * Struct-of-arrays storage, zero allocation in [update] and [draw]:
 * dead particles are swap-removed, live ones are plain float math.
 */
class ParticleSystem(
    val capacity: Int = JuiceTimings.PARTICLE_MAX,
) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val gravity = FloatArray(capacity)
    private val drag = FloatArray(capacity)
    private val shape = IntArray(capacity)

    var aliveCount: Int = 0
        private set

    fun burst(
        cx: Float,
        cy: Float,
        spec: BurstSpec,
        random: Random = Random,
    ) {
        val count = spec.count.coerceIn(JuiceTimings.PARTICLE_BURST_MIN, JuiceTimings.PARTICLE_BURST_MAX)
        repeat(count) {
            if (aliveCount >= capacity) return
            val i = aliveCount++
            val angle = random.nextFloat() * FULL_CIRCLE
            val speed = spec.speedMin + random.nextFloat() * (spec.speedMax - spec.speedMin)
            x[i] = cx + (random.nextFloat() - SPREAD_HALF) * spec.spreadPx
            y[i] = cy + (random.nextFloat() - SPREAD_HALF) * spec.spreadPx
            vx[i] = cos(angle) * speed
            vy[i] = sin(angle) * speed - spec.upBias
            val lifeMs = spec.lifeMinMs + random.nextLong(spec.lifeMaxMs - spec.lifeMinMs)
            life[i] = lifeMs.toFloat()
            maxLife[i] = lifeMs.toFloat()
            size[i] = spec.sizeMinPx + random.nextFloat() * (spec.sizeMaxPx - spec.sizeMinPx)
            color[i] = spec.colors[random.nextInt(spec.colors.size)]
            gravity[i] = spec.gravityPxPerSec2
            drag[i] = spec.dragPerSecond
            shape[i] = spec.shape.ordinal
        }
    }

    fun update(dtSeconds: Float) {
        if (dtSeconds <= 0f) return
        var i = 0
        while (i < aliveCount) {
            life[i] -= dtSeconds * MILLIS_PER_SECOND_F
            if (life[i] <= 0f) {
                val last = --aliveCount
                x[i] = x[last]
                y[i] = y[last]
                vx[i] = vx[last]
                vy[i] = vy[last]
                life[i] = life[last]
                maxLife[i] = maxLife[last]
                size[i] = size[last]
                color[i] = color[last]
                gravity[i] = gravity[last]
                drag[i] = drag[last]
                shape[i] = shape[last]
                continue
            }
            val dragFactor = 1f - drag[i] * dtSeconds
            vx[i] *= dragFactor
            vy[i] = vy[i] * dragFactor + gravity[i] * dtSeconds
            x[i] += vx[i] * dtSeconds
            y[i] += vy[i] * dtSeconds
            i++
        }
    }

    fun draw(scope: DrawScope) {
        for (i in 0 until aliveCount) {
            val alpha = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            val c = Color(color[i]).copy(alpha = alpha)
            val px = x[i]
            val py = y[i]
            when (shape[i]) {
                ParticleShape.CIRCLE.ordinal -> {
                    scope.drawCircle(c, size[i] / 2f, Offset(px, py))
                }

                ParticleShape.SQUARE.ordinal -> {
                    scope.drawRect(
                        c,
                        Offset(px - size[i] / 2f, py - size[i] / 2f),
                        androidx.compose.ui.geometry
                            .Size(size[i], size[i] * SQUARE_HEIGHT_FRAC),
                    )
                }

                else -> {
                    val half = size[i]
                    val dx = vx[i]
                    val dy = vy[i]
                    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    scope.drawLine(
                        c,
                        Offset(px - dx / len * half, py - dy / len * half),
                        Offset(px + dx / len * half, py + dy / len * half),
                        strokeWidth = (size[i] * 0.35f).coerceAtLeast(1f),
                    )
                }
            }
        }
    }

    fun clear() {
        aliveCount = 0
    }

    companion object {
        private const val FULL_CIRCLE = (Math.PI * 2).toFloat()
        private const val SPREAD_HALF = 0.5f
        private const val MILLIS_PER_SECOND_F = 1000f
        private const val SQUARE_HEIGHT_FRAC = 0.7f
    }
}

enum class ParticleShape { CIRCLE, SQUARE, SPARK }

/**
 * One burst recipe. [count] is always within the §10 5-20 band.
 */
data class BurstSpec(
    val count: Int,
    val colors: IntArray,
    val speedMin: Float,
    val speedMax: Float,
    val lifeMinMs: Long,
    val lifeMaxMs: Long,
    val sizeMinPx: Float,
    val sizeMaxPx: Float,
    val gravityPxPerSec2: Float,
    val dragPerSecond: Float,
    val shape: ParticleShape,
    /** Random spawn disc diameter, so bursts have volume. */
    val spreadPx: Float = 8f,
    /** Extra upward velocity: confetti pops up before it falls. */
    val upBias: Float = 0f,
) {
    // IntArray breaks data-class equals; bursts are recipes, identity is fine.
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** Canned recipes used across the board. Colors are ARGB ints. */
object Bursts {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GOLD = 0xFFFFD54F.toInt()
    private const val GOLD_DEEP = 0xFFFFB300.toInt()
    private const val SKY = 0xFF4FC3F7.toInt()
    private const val PINK = 0xFFF06292.toInt()
    private const val MINT = 0xFF69F0AE.toInt()
    private const val DUST = 0xFFD7CCC8.toInt()
    private const val BLUE = 0xFF448AFF.toInt()

    val CONFETTI =
        BurstSpec(
            count = 16,
            colors = intArrayOf(WHITE, GOLD, SKY, PINK, MINT),
            speedMin = 120f,
            speedMax = 420f,
            lifeMinMs = 700,
            lifeMaxMs = 1300,
            sizeMinPx = 6f,
            sizeMaxPx = 12f,
            gravityPxPerSec2 = 900f,
            dragPerSecond = 1.2f,
            shape = ParticleShape.SQUARE,
            upBias = 260f,
        )
    val DUST_POOF =
        BurstSpec(
            count = 8,
            colors = intArrayOf(DUST, WHITE),
            speedMin = 40f,
            speedMax = 160f,
            lifeMinMs = 400,
            lifeMaxMs = 700,
            sizeMinPx = 8f,
            sizeMaxPx = 18f,
            gravityPxPerSec2 = -60f,
            dragPerSecond = 2.5f,
            shape = ParticleShape.CIRCLE,
            spreadPx = 24f,
        )
    val COINS =
        BurstSpec(
            count = 12,
            colors = intArrayOf(GOLD, GOLD_DEEP, WHITE),
            speedMin = 100f,
            speedMax = 380f,
            lifeMinMs = 600,
            lifeMaxMs = 1100,
            sizeMinPx = 8f,
            sizeMaxPx = 14f,
            gravityPxPerSec2 = 1100f,
            dragPerSecond = 0.6f,
            shape = ParticleShape.CIRCLE,
            upBias = 320f,
        )
    val STARS =
        BurstSpec(
            count = 12,
            colors = intArrayOf(WHITE, GOLD),
            speedMin = 80f,
            speedMax = 300f,
            lifeMinMs = 500,
            lifeMaxMs = 900,
            sizeMinPx = 10f,
            sizeMaxPx = 20f,
            gravityPxPerSec2 = 200f,
            dragPerSecond = 1.8f,
            shape = ParticleShape.SPARK,
        )
    val BLUE_RING =
        BurstSpec(
            count = 14,
            colors = intArrayOf(BLUE, SKY, WHITE),
            speedMin = 200f,
            speedMax = 340f,
            lifeMinMs = 500,
            lifeMaxMs = 800,
            sizeMinPx = 6f,
            sizeMaxPx = 12f,
            gravityPxPerSec2 = 0f,
            dragPerSecond = 2.2f,
            shape = ParticleShape.CIRCLE,
        )
    val GOLD_RAYS =
        BurstSpec(
            count = 20,
            colors = intArrayOf(GOLD, WHITE, GOLD_DEEP),
            speedMin = 260f,
            speedMax = 520f,
            lifeMinMs = 600,
            lifeMaxMs = 1000,
            sizeMinPx = 12f,
            sizeMaxPx = 24f,
            gravityPxPerSec2 = 0f,
            dragPerSecond = 2.6f,
            shape = ParticleShape.SPARK,
        )
    val RED_POOF =
        BurstSpec(
            count = 10,
            colors = intArrayOf(0xFFFF8A80.toInt(), 0xFFFFAB91.toInt(), WHITE),
            speedMin = 60f,
            speedMax = 220f,
            lifeMinMs = 400,
            lifeMaxMs = 700,
            sizeMinPx = 10f,
            sizeMaxPx = 20f,
            gravityPxPerSec2 = 100f,
            dragPerSecond = 2.4f,
            shape = ParticleShape.CIRCLE,
            spreadPx = 20f,
        )
}

/**
 * The board's juice handle, remembered once per Base screen. Bursts,
 * trauma shake and the photosensitivity guard all live here so the Canvas
 * stays a pure function of (state, frame time, juice).
 */
class JuiceController {
    val particles = ParticleSystem()
    val shake = TraumaShake()
    val flashGuard = FlashGuard()

    fun confetti(
        cx: Float,
        cy: Float,
    ) = particles.burst(cx, cy, Bursts.CONFETTI)

    fun dust(
        cx: Float,
        cy: Float,
    ) = particles.burst(cx, cy, Bursts.DUST_POOF)

    fun coins(
        cx: Float,
        cy: Float,
    ) = particles.burst(cx, cy, Bursts.COINS)

    fun stars(
        cx: Float,
        cy: Float,
    ) = particles.burst(cx, cy, Bursts.STARS)

    fun blueRing(
        cx: Float,
        cy: Float,
    ) = particles.burst(cx, cy, Bursts.BLUE_RING)

    /** Epic gold rays: gated by the photosensitivity guard (small area, never red). */
    fun goldRays(
        cx: Float,
        cy: Float,
        canvasAreaPx: Float,
    ) {
        val areaFraction =
            (GOLD_RAYS_RADIUS_PX * GOLD_RAYS_RADIUS_PX * Math.PI.toFloat()) /
                canvasAreaPx.coerceAtLeast(1f)
        if (flashGuard.allowFlash(areaFraction, isRed = false)) {
            particles.burst(cx, cy, Bursts.GOLD_RAYS)
        }
    }

    /** Comedy poof for steals. Desaturated warm tones: never a red flash. */
    fun stealPoof(
        cx: Float,
        cy: Float,
    ) = particles.burst(cx, cy, Bursts.RED_POOF)

    companion object {
        private const val GOLD_RAYS_RADIUS_PX = 160f
    }
}

package app.pilgrim.domain

/** Where a sitting is. */
enum class Phase {
    /** "Leave the phone": waiting for the screen to go dark. */
    DARKEN,

    /** The screen is off and the time runs. */
    SITTING,

    /** It ended before ten minutes. No stamp; the place will wait. */
    BROKEN,

    /** Ten minutes or more: a stamp. */
    DONE,
}

/** Why a sitting ended early. */
enum class Why { SCREEN, LEFT, MOVED, MOCK, STOPPED }

/**
 * The honest part, kept apart from Android so it can be tested: what counts as ten minutes without
 * the phone.
 *
 * - The time runs only once the screen has gone dark.
 * - A screen lit for a glance (a notification, the clock) is forgiven; lit for [GLANCE] in a row,
 *   the phone was picked up.
 * - Fixes far from the place for [AWAY] in a row: you left. Rough fixes (indoors) don't count either way.
 * - The phone in motion for a minute, with some forgetting: you're walking, not sitting.
 * - A faked location ends it.
 *
 * Once ten minutes are in, any of those just ends the sitting with the time it had: the stamp is yours.
 */
class SitRules(private val radius: Double, private val slack: Double, private val need: Long = TEN) {
    var phase = Phase.DARKEN
        private set
    var why: Why? = null
        private set

    /** Milliseconds sat. */
    var sat = 0L
        private set

    /** When the screen lit, while it is lit. */
    var litSince: Long? = null
        private set

    private var last = 0L
    private var awaySince: Long? = null
    private var moving = 0L
    private val recent = ArrayDeque<Double>()

    val reached get() = sat >= need
    val over get() = phase == Phase.BROKEN || phase == Phase.DONE

    fun screen(on: Boolean, t: Long) {
        if (over) return
        if (!on) {
            if (phase == Phase.SITTING) tick(t)
            if (over) return
            litSince = null
            if (phase == Phase.DARKEN) {
                phase = Phase.SITTING
                last = t
            }
        } else if (litSince == null) {
            if (phase == Phase.SITTING) tick(t)
            litSince = t
        }
    }

    /** The screen unlocked: after ten minutes, that is getting up. */
    fun unlocked(t: Long) {
        if (phase == Phase.SITTING && reached) {
            tick(t)
            end(null)
        }
    }

    fun tick(t: Long) {
        if (phase != Phase.SITTING) return
        val lit = litSince
        // A glance counts as sitting; only the part of a lit screen beyond the glance doesn't.
        val dead = if (lit == null) 0 else (t - maxOf(last, lit + GLANCE)).coerceAtLeast(0)
        sat += (t - last - dead).coerceAtLeast(0)
        last = t
        if (sat >= CAP) end(null)
        else if (lit != null && t - lit >= GLANCE) end(Why.SCREEN)
    }

    /** A position fix: metres from the place, how sure. */
    fun fix(t: Long, metres: Double, accuracy: Float, mock: Boolean) {
        if (over) return
        if (mock) {
            // Faked: nothing earned, not even after ten minutes.
            phase = Phase.BROKEN
            why = Why.MOCK
            return
        }
        if (accuracy > ROUGH) return
        recent.addLast(metres - minOf(accuracy.toDouble(), 40.0))
        while (recent.size > 5) recent.removeFirst()
        if (phase != Phase.SITTING) return
        if (Geo.median(recent.toList()) > radius + slack) {
            val since = awaySince ?: t.also { awaySince = it }
            if (t - since >= AWAY) { tick(t); end(Why.LEFT) }
        } else awaySince = null
    }

    /** One stretch of motion samples: walking or not, over [dt] ms. */
    fun motion(walking: Boolean, dt: Long) {
        if (phase != Phase.SITTING) return
        moving = if (walking) moving + dt else (moving - dt / 4).coerceAtLeast(0)
        if (moving >= WALK) end(Why.MOVED)
    }

    /** "Get up". */
    fun stop(t: Long) {
        if (over) return
        tick(t)
        if (phase == Phase.DARKEN) { phase = Phase.BROKEN; why = Why.STOPPED } else end(Why.STOPPED)
    }

    private fun end(reason: Why?) {
        if (reached) {
            phase = Phase.DONE
            why = null
        } else {
            phase = Phase.BROKEN
            why = reason ?: Why.STOPPED
        }
    }

    companion object {
        const val TEN = 10 * 60_000L
        const val GLANCE = 10_000L
        const val AWAY = 90_000L
        const val WALK = 60_000L
        const val CAP = 3 * 60 * 60_000L
        const val ROUGH = 80f
    }
}

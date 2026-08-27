package dev.agentbayu.app.ai

interface Clock {
    fun nowMillis(): Long
}

object RealClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

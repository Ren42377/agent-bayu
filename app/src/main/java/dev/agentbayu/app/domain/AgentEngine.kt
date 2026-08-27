package dev.agentbayu.app.domain

interface AgentEngine {

    suspend fun reply(prompt: String, screenContext: String?): String
}

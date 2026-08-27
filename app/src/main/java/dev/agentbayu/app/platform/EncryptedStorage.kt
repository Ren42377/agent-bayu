package dev.agentbayu.app.platform

interface EncryptedStorage {
    fun read(name: String): String?

    fun write(name: String, content: String)

    fun delete(name: String)
}

class InMemoryStorage : EncryptedStorage {

    private val entries = HashMap<String, String>()

    override fun read(name: String): String? = synchronized(entries) { entries[name] }

    override fun write(name: String, content: String) {
        synchronized(entries) { entries[name] = content }
    }

    override fun delete(name: String) {
        synchronized(entries) { entries.remove(name) }
    }
}

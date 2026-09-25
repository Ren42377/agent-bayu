package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.EncryptedStorage
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface CatalogUpdateResult {
    data class Success(val catalog: ProviderCatalog, val fetchedAtMillis: Long) : CatalogUpdateResult

    data class Failure(val message: String) : CatalogUpdateResult
}

class CatalogUpdater(
    private val client: OkHttpClient,
    private val storage: EncryptedStorage,
    private val clock: Clock = RealClock,
    private val minimumVersion: Int = ProviderCatalog.DEFAULT_VERSION
) {

    fun restore(): CatalogUpdateResult? {
        val raw = storage.read(CATALOG_FILE) ?: return null
        val fetchedAt = storage.read(CATALOG_TIME_FILE)?.toLongOrNull() ?: 0L
        return try {
            val catalog = ProviderCatalog.parse(raw)
            if (catalog.providers.isEmpty()) {
                null
            } else if (catalog.version < minimumVersion) {
                clear()
                null
            } else {
                CatalogUpdateResult.Success(catalog, fetchedAt)
            }
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    fun clear() {
        storage.delete(CATALOG_FILE)
        storage.delete(CATALOG_TIME_FILE)
    }

    suspend fun fetch(url: String): CatalogUpdateResult = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return@withContext CatalogUpdateResult.Failure("catalog url is empty")
        val request = Request.Builder()
            .url(trimmed)
            .header("Accept", "application/json")
            .build()
        val body = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext CatalogUpdateResult.Failure(
                        "http " + response.code
                    )
                }
                response.body?.string().orEmpty()
            }
        } catch (error: IOException) {
            return@withContext CatalogUpdateResult.Failure(
                error.javaClass.simpleName
            )
        }
        if (body.isBlank()) return@withContext CatalogUpdateResult.Failure("empty catalog")
        val catalog = try {
            ProviderCatalog.parse(body)
        } catch (error: IllegalArgumentException) {
            return@withContext CatalogUpdateResult.Failure("malformed catalog")
        }
        if (catalog.providers.isEmpty()) {
            return@withContext CatalogUpdateResult.Failure("catalog has no providers")
        }
        if (catalog.version < minimumVersion) {
            return@withContext CatalogUpdateResult.Failure("catalog version is older than bundled")
        }
        val fetchedAt = clock.nowMillis()
        storage.write(CATALOG_FILE, body)
        storage.write(CATALOG_TIME_FILE, fetchedAt.toString())
        CatalogUpdateResult.Success(catalog, fetchedAt)
    }

    companion object {
        const val DEFAULT_UPDATE_URL =
            "https://raw.githubusercontent.com/Ren42377/agent-bayu/main/app/src/main/assets/providers.json"
        const val CATALOG_FILE = "catalog_remote.json"
        const val CATALOG_TIME_FILE = "catalog_remote_time"
    }
}

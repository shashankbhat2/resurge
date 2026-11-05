package com.resurgeai.core.data.mcp

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.resurgeai.core.model.McpApiKey
import com.resurgeai.core.model.NewMcpApiKey
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

class McpApiKeyException(message: String) : Exception(message)

/**
 * Personal API keys for the MCP server, through the createMcpApiKey / listMcpApiKeys /
 * revokeMcpApiKey callables. The keys live in a collection the app can't read directly.
 */
@Singleton
class McpApiKeysRepository @Inject constructor(
    private val functions: FirebaseFunctions,
) {
    suspend fun list(): List<McpApiKey> {
        val data = call("listMcpApiKeys", null)
        return (data["keys"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::toKey) }
    }

    /** Makes a key; the returned [NewMcpApiKey.key] is the only copy of it. */
    suspend fun create(name: String): NewMcpApiKey {
        val data = call("createMcpApiKey", mapOf("name" to name.trim()))
        val key = data["key"] as? String
        val info = (data["info"] as? Map<*, *>)?.let(::toKey)
        if (key == null || info == null) throw McpApiKeyException("Couldn't make the key. Try again.")
        return NewMcpApiKey(key, info)
    }

    suspend fun revoke(id: String) {
        call("revokeMcpApiKey", mapOf("id" to id))
    }

    private suspend fun call(name: String, data: Any?): Map<*, *> {
        val result = try {
            functions.getHttpsCallable(name).call(data).await()
        } catch (e: FirebaseFunctionsException) {
            throw McpApiKeyException(
                when (e.code) {
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED,
                    FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                    FirebaseFunctionsException.Code.NOT_FOUND -> e.message ?: "Something went wrong. Try again."
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Please sign in again."
                    else -> "Something went wrong. Try again."
                },
            )
        } catch (e: Exception) {
            throw McpApiKeyException("Couldn't reach the server. Check your connection and try again.")
        }
        return result.getData() as? Map<*, *> ?: emptyMap<String, Any?>()
    }

    private fun toKey(m: Map<*, *>): McpApiKey? {
        val id = m["id"] as? String ?: return null
        val created = (m["createdAtMs"] as? Number)?.toLong() ?: return null
        return McpApiKey(
            id = id,
            name = m["name"] as? String ?: "Key",
            last4 = m["last4"] as? String ?: "",
            createdAt = Instant.ofEpochMilli(created),
            lastUsedAt = (m["lastUsedAtMs"] as? Number)?.toLong()?.let(Instant::ofEpochMilli),
        )
    }
}

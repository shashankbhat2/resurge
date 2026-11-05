package com.resurgeai.core.model

import java.time.Instant

/** A personal API key for the MCP server, as listed in Settings. The key itself is never stored on the phone. */
data class McpApiKey(
    /** Server id (the key's hash); used to revoke it. */
    val id: String,
    val name: String,
    /** Last 4 characters, shown as "rsg_…abcd". */
    val last4: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
) {
    val masked: String get() = "rsg_…$last4"
}

/** A key that was just made. [key] is shown once and then forgotten. */
data class NewMcpApiKey(val key: String, val info: McpApiKey)

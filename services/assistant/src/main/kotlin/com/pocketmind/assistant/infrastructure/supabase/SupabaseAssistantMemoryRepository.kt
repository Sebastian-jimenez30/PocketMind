package com.pocketmind.assistant.infrastructure.supabase

import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.domain.memory.AssistantCheckpoint
import com.pocketmind.assistant.domain.memory.AssistantCommandDraft
import com.pocketmind.assistant.domain.memory.AssistantCommandEvent
import com.pocketmind.assistant.domain.memory.AssistantConversation
import com.pocketmind.assistant.domain.memory.AssistantMemoryConflictException
import com.pocketmind.assistant.domain.memory.AssistantMemoryRemoteException
import com.pocketmind.assistant.domain.memory.AssistantMemoryRepository
import com.pocketmind.assistant.domain.memory.AssistantMessage
import com.pocketmind.assistant.domain.memory.AssistantProductAlias
import com.pocketmind.assistant.domain.memory.ConversationStatus
import com.pocketmind.assistant.domain.memory.DraftState
import com.pocketmind.assistant.domain.memory.DraftTransition
import com.pocketmind.assistant.domain.memory.InputModality
import com.pocketmind.assistant.domain.memory.MessageRole
import com.pocketmind.assistant.domain.memory.NewAssistantCheckpoint
import com.pocketmind.assistant.domain.memory.NewCommandDraft
import com.pocketmind.assistant.domain.memory.NewConversation
import com.pocketmind.assistant.domain.memory.NewMessage
import com.pocketmind.assistant.domain.memory.NewProductAlias
import com.pocketmind.assistant.domain.memory.ProposedDraftRevision
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

class SupabaseAssistantMemoryRepository(
    private val client: HttpClient,
    private val config: AssistantConfig,
) : AssistantMemoryRepository {
    override suspend fun createConversation(
        session: AuthenticatedUser,
        value: NewConversation,
    ): AssistantConversation {
        val response = client.post(tableUrl(CONVERSATIONS)) {
            authenticate(session)
            returnRepresentation()
            setBody(
                ConversationWriteDto(
                    id = value.id.requireUuid(),
                    userId = session.userId,
                    title = value.title,
                    locale = value.locale,
                    promptVersion = value.promptVersion,
                    toolSchemaVersion = value.toolSchemaVersion,
                ),
            )
        }
        return response.singleRepresentation<ConversationDto>("createConversation").toDomain()
    }

    override suspend fun getConversation(
        session: AuthenticatedUser,
        conversationId: String,
    ): AssistantConversation? {
        val response = client.get(tableUrl(CONVERSATIONS)) {
            authenticate(session)
            selectAll()
            parameter("id", "eq.${conversationId.requireUuid()}")
            parameter("user_id", "eq.${session.userId}")
            parameter("limit", 1)
        }
        return response.optionalRepresentation<ConversationDto>("getConversation")?.toDomain()
    }

    override suspend fun listConversations(
        session: AuthenticatedUser,
        limit: Int,
    ): List<AssistantConversation> {
        val response = client.get(tableUrl(CONVERSATIONS)) {
            authenticate(session)
            selectAll()
            parameter("user_id", "eq.${session.userId}")
            parameter("order", "last_message_at.desc.nullslast,created_at.desc")
            parameter("limit", limit.validatedLimit(MAX_CONVERSATIONS))
        }
        return response.representationList<ConversationDto>("listConversations")
            .map(ConversationDto::toDomain)
    }

    override suspend fun deleteConversation(
        session: AuthenticatedUser,
        conversationId: String,
    ): Boolean {
        val response = client.delete(tableUrl(CONVERSATIONS)) {
            authenticate(session)
            returnRepresentation()
            parameter("id", "eq.${conversationId.requireUuid()}")
            parameter("user_id", "eq.${session.userId}")
        }
        return response.representationList<ConversationDto>("deleteConversation").isNotEmpty()
    }

    override suspend fun appendMessage(
        session: AuthenticatedUser,
        value: NewMessage,
    ): AssistantMessage {
        val response = client.post(tableUrl(MESSAGES)) {
            authenticate(session)
            returnRepresentation()
            setBody(
                MessageWriteDto(
                    id = value.id.requireUuid(),
                    conversationId = value.conversationId.requireUuid(),
                    userId = session.userId,
                    turnId = value.turnId.requireUuid(),
                    clientMessageId = value.clientMessageId,
                    role = value.role.wireValue,
                    content = value.content,
                    inputModality = value.inputModality.wireValue,
                    promptVersion = value.promptVersion,
                    modelId = value.modelId,
                ),
            )
        }
        return response.singleRepresentation<MessageDto>("appendMessage").toDomain()
    }

    override suspend fun findMessageByClientMessageId(
        session: AuthenticatedUser,
        clientMessageId: String,
    ): AssistantMessage? {
        require(clientMessageId.trim().length in 1..160)
        val response = client.get(tableUrl(MESSAGES)) {
            authenticate(session)
            selectAll()
            parameter("user_id", "eq.${session.userId}")
            parameter("client_message_id", "eq.${clientMessageId.trim()}")
            parameter("limit", 1)
        }
        return response.optionalRepresentation<MessageDto>(
            "findMessageByClientMessageId",
        )?.toDomain()
    }

    override suspend fun listMessages(
        session: AuthenticatedUser,
        conversationId: String,
        limit: Int,
    ): List<AssistantMessage> {
        val response = client.get(tableUrl(MESSAGES)) {
            authenticate(session)
            selectAll()
            parameter("conversation_id", "eq.${conversationId.requireUuid()}")
            parameter("user_id", "eq.${session.userId}")
            parameter("order", "created_at.asc,id.asc")
            parameter("limit", limit.validatedLimit(MAX_MESSAGES))
        }
        return response.representationList<MessageDto>("listMessages")
            .map(MessageDto::toDomain)
    }

    override suspend fun createDraft(
        session: AuthenticatedUser,
        value: NewCommandDraft,
    ): AssistantCommandDraft {
        val response = client.post(tableUrl(DRAFTS)) {
            authenticate(session)
            returnRepresentation()
            setBody(
                DraftCreateDto(
                    id = value.id.requireUuid(),
                    conversationId = value.conversationId.requireUuid(),
                    userId = session.userId,
                    commandType = value.commandType,
                    commandPayload = value.commandPayload,
                    commandSchemaVersion = value.commandSchemaVersion,
                    idempotencyKey = value.idempotencyKey,
                    payloadHash = CanonicalJson.sha256(value.commandPayload),
                    financialStateVersion = value.financialStateVersion,
                    expiresAt = value.expiresAt.toString(),
                ),
            )
        }
        return response.singleRepresentation<DraftDto>("createDraft").toDomain()
    }

    override suspend fun getDraft(
        session: AuthenticatedUser,
        draftId: String,
    ): AssistantCommandDraft? {
        val response = client.get(tableUrl(DRAFTS)) {
            authenticate(session)
            selectAll()
            parameter("id", "eq.${draftId.requireUuid()}")
            parameter("user_id", "eq.${session.userId}")
            parameter("limit", 1)
        }
        return response.optionalRepresentation<DraftDto>("getDraft")?.toDomain()
    }

    override suspend fun getDraftByIdempotencyKey(
        session: AuthenticatedUser,
        idempotencyKey: String,
    ): AssistantCommandDraft? {
        require(idempotencyKey.trim().length in 16..160)
        val response = client.get(tableUrl(DRAFTS)) {
            authenticate(session)
            selectAll()
            parameter("user_id", "eq.${session.userId}")
            parameter("idempotency_key", "eq.${idempotencyKey.trim()}")
            parameter("limit", 1)
        }
        return response.optionalRepresentation<DraftDto>(
            "getDraftByIdempotencyKey",
        )?.toDomain()
    }

    override suspend fun reviseProposedDraft(
        session: AuthenticatedUser,
        draftId: String,
        expectedVersion: Long,
        revision: ProposedDraftRevision,
    ): AssistantCommandDraft {
        val response = client.patch(tableUrl(DRAFTS)) {
            authenticate(session)
            returnRepresentation()
            parameter("id", "eq.${draftId.requireUuid()}")
            parameter("user_id", "eq.${session.userId}")
            parameter("state", "eq.${DraftState.PROPOSED.wireValue}")
            parameter("version", "eq.$expectedVersion")
            setBody(
                DraftRevisionDto(
                    commandType = revision.commandType,
                    commandPayload = revision.commandPayload,
                    commandSchemaVersion = revision.commandSchemaVersion,
                    payloadHash = CanonicalJson.sha256(revision.commandPayload),
                    financialStateVersion = revision.financialStateVersion,
                ),
            )
        }
        return response.requiredMutation<DraftDto>("reviseProposedDraft").toDomain()
    }

    override suspend fun transitionDraft(
        session: AuthenticatedUser,
        draftId: String,
        transition: DraftTransition,
    ): AssistantCommandDraft {
        val response = client.patch(tableUrl(DRAFTS)) {
            authenticate(session)
            returnRepresentation()
            parameter("id", "eq.${draftId.requireUuid()}")
            parameter("user_id", "eq.${session.userId}")
            parameter("state", "eq.${transition.expectedState.wireValue}")
            parameter("version", "eq.${transition.expectedVersion}")
            setBody(
                DraftTransitionDto(
                    state = transition.nextState.wireValue,
                    executionResult = transition.executionResult,
                    errorCode = transition.errorCode,
                ),
            )
        }
        return response.requiredMutation<DraftDto>("transitionDraft").toDomain()
    }

    override suspend fun listDraftEvents(
        session: AuthenticatedUser,
        draftId: String,
    ): List<AssistantCommandEvent> {
        val response = client.get(tableUrl(EVENTS)) {
            authenticate(session)
            selectAll()
            parameter("draft_id", "eq.${draftId.requireUuid()}")
            parameter("user_id", "eq.${session.userId}")
            parameter("order", "created_at.asc,id.asc")
        }
        return response.representationList<EventDto>("listDraftEvents")
            .map(EventDto::toDomain)
    }

    override suspend fun upsertProductAlias(
        session: AuthenticatedUser,
        value: NewProductAlias,
    ): AssistantProductAlias {
        val response = client.post(tableUrl(ALIASES)) {
            authenticate(session)
            mergeRepresentation()
            parameter("on_conflict", "id")
            setBody(
                AliasWriteDto(
                    id = value.id.requireUuid(),
                    userId = session.userId,
                    productId = value.productId,
                    productType = value.productType,
                    alias = value.alias,
                ),
            )
        }
        return response.singleRepresentation<AliasDto>("upsertProductAlias").toDomain()
    }

    override suspend fun listProductAliases(
        session: AuthenticatedUser,
    ): List<AssistantProductAlias> {
        val response = client.get(tableUrl(ALIASES)) {
            authenticate(session)
            selectAll()
            parameter("user_id", "eq.${session.userId}")
            parameter("order", "normalized_alias.asc")
        }
        return response.representationList<AliasDto>("listProductAliases")
            .map(AliasDto::toDomain)
    }

    override suspend fun deleteProductAlias(
        session: AuthenticatedUser,
        aliasId: String,
    ): Boolean {
        val response = client.delete(tableUrl(ALIASES)) {
            authenticate(session)
            returnRepresentation()
            parameter("id", "eq.${aliasId.requireUuid()}")
            parameter("user_id", "eq.${session.userId}")
        }
        return response.representationList<AliasDto>("deleteProductAlias").isNotEmpty()
    }

    override suspend fun saveCheckpoint(
        session: AuthenticatedUser,
        value: NewAssistantCheckpoint,
    ): AssistantCheckpoint {
        val response = client.post(tableUrl(CHECKPOINTS)) {
            authenticate(session)
            mergeRepresentation()
            parameter("on_conflict", "user_id,conversation_id,checkpoint_key")
            setBody(
                CheckpointWriteDto(
                    id = value.id.requireUuid(),
                    conversationId = value.conversationId.requireUuid(),
                    userId = session.userId,
                    checkpointKey = value.checkpointKey,
                    graphVersion = value.graphVersion,
                    checkpointVersion = value.checkpointVersion,
                    state = value.state,
                    checkpointCreatedAt = value.checkpointCreatedAt.toString(),
                    expiresAt = value.expiresAt.toString(),
                ),
            )
        }
        return response.singleRepresentation<CheckpointDto>("saveCheckpoint").toDomain()
    }

    override suspend fun listCheckpoints(
        session: AuthenticatedUser,
        conversationId: String,
    ): List<AssistantCheckpoint> {
        val response = client.get(tableUrl(CHECKPOINTS)) {
            authenticate(session)
            selectAll()
            parameter("conversation_id", "eq.${conversationId.requireUuid()}")
            parameter("user_id", "eq.${session.userId}")
            parameter("order", "checkpoint_created_at.desc,id.desc")
        }
        return response.representationList<CheckpointDto>("listCheckpoints")
            .map(CheckpointDto::toDomain)
    }

    private fun tableUrl(table: String): String = "${config.supabaseUrl}/rest/v1/$table"

    private fun io.ktor.client.request.HttpRequestBuilder.authenticate(
        session: AuthenticatedUser,
    ) {
        contentType(ContentType.Application.Json)
        header("apikey", config.supabasePublishableKey.reveal())
        bearerAuth(session.accessToken.reveal())
    }

    private fun io.ktor.client.request.HttpRequestBuilder.selectAll() {
        parameter("select", "*")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.returnRepresentation() {
        header(PREFER_HEADER, "return=representation")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.mergeRepresentation() {
        header(PREFER_HEADER, "resolution=merge-duplicates,return=representation")
    }

    private companion object {
        const val CONVERSATIONS = "assistant_conversations"
        const val MESSAGES = "assistant_messages"
        const val DRAFTS = "assistant_command_drafts"
        const val EVENTS = "assistant_command_events"
        const val ALIASES = "assistant_product_aliases"
        const val CHECKPOINTS = "assistant_checkpoints"
        const val PREFER_HEADER = "Prefer"
        const val MAX_CONVERSATIONS = 100
        const val MAX_MESSAGES = 200
    }
}

private suspend inline fun <reified T> HttpResponse.representationList(
    operation: String,
): List<T> {
    if (!status.isSuccess()) {
        throw AssistantMemoryRemoteException(operation, status.value)
    }
    return body()
}

private suspend inline fun <reified T> HttpResponse.singleRepresentation(
    operation: String,
): T = representationList<T>(operation).singleOrNull()
    ?: throw AssistantMemoryRemoteException(operation, status.value)

private suspend inline fun <reified T> HttpResponse.optionalRepresentation(
    operation: String,
): T? = representationList<T>(operation).singleOrNull()

private suspend inline fun <reified T> HttpResponse.requiredMutation(
    operation: String,
): T = representationList<T>(operation).singleOrNull()
    ?: throw AssistantMemoryConflictException(operation)

private fun Int.validatedLimit(maximum: Int): Int {
    require(this in 1..maximum) { "Limit must be between 1 and $maximum." }
    return this
}

private fun String.requireUuid(): String {
    require(runCatching { java.util.UUID.fromString(this) }.isSuccess) {
        "Invalid UUID."
    }
    return this
}

internal object CanonicalJson {
    fun sha256(value: JsonObject): String {
        val canonical = value.canonicalString()
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    }

    private fun JsonElement.canonicalString(): String = when (this) {
        is JsonObject -> entries
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                "${JsonPrimitive(key)}:${value.canonicalString()}"
            }

        is JsonArray -> joinToString(prefix = "[", postfix = "]", separator = ",") {
            it.canonicalString()
        }

        JsonNull -> "null"
        is JsonPrimitive -> toString()
    }
}

@Serializable
private data class ConversationWriteDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val title: String?,
    val locale: String,
    @SerialName("prompt_version")
    val promptVersion: String,
    @SerialName("tool_schema_version")
    val toolSchemaVersion: Int,
)

@Serializable
private data class ConversationDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val title: String?,
    val status: String,
    val locale: String,
    @SerialName("prompt_version")
    val promptVersion: String,
    @SerialName("tool_schema_version")
    val toolSchemaVersion: Int,
    @SerialName("last_message_at")
    val lastMessageAt: String?,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
) {
    fun toDomain(): AssistantConversation = AssistantConversation(
        id = id,
        userId = userId,
        title = title,
        status = ConversationStatus.entries.requireWireValue(status),
        locale = locale,
        promptVersion = promptVersion,
        toolSchemaVersion = toolSchemaVersion,
        lastMessageAt = lastMessageAt?.let(Instant::parse),
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
    )
}

@Serializable
private data class MessageWriteDto(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("turn_id")
    val turnId: String,
    @SerialName("client_message_id")
    val clientMessageId: String?,
    val role: String,
    val content: String,
    @SerialName("input_modality")
    val inputModality: String,
    @SerialName("prompt_version")
    val promptVersion: String?,
    @SerialName("model_id")
    val modelId: String?,
)

@Serializable
private data class MessageDto(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("turn_id")
    val turnId: String,
    @SerialName("client_message_id")
    val clientMessageId: String?,
    val role: String,
    val content: String,
    @SerialName("input_modality")
    val inputModality: String,
    @SerialName("prompt_version")
    val promptVersion: String?,
    @SerialName("model_id")
    val modelId: String?,
    @SerialName("created_at")
    val createdAt: String,
) {
    fun toDomain(): AssistantMessage = AssistantMessage(
        id = id,
        conversationId = conversationId,
        userId = userId,
        turnId = turnId,
        clientMessageId = clientMessageId,
        role = MessageRole.entries.requireWireValue(role),
        content = content,
        inputModality = InputModality.entries.requireWireValue(inputModality),
        promptVersion = promptVersion,
        modelId = modelId,
        createdAt = Instant.parse(createdAt),
    )
}

@Serializable
private data class DraftCreateDto(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("command_type")
    val commandType: String,
    @SerialName("command_payload")
    val commandPayload: JsonObject,
    @SerialName("command_schema_version")
    val commandSchemaVersion: Int,
    @SerialName("idempotency_key")
    val idempotencyKey: String,
    @SerialName("payload_hash")
    val payloadHash: String,
    @SerialName("financial_state_version")
    val financialStateVersion: Long,
    @SerialName("expires_at")
    val expiresAt: String,
)

@Serializable
private data class DraftRevisionDto(
    @SerialName("command_type")
    val commandType: String,
    @SerialName("command_payload")
    val commandPayload: JsonObject,
    @SerialName("command_schema_version")
    val commandSchemaVersion: Int,
    @SerialName("payload_hash")
    val payloadHash: String,
    @SerialName("financial_state_version")
    val financialStateVersion: Long,
)

@Serializable
private data class DraftTransitionDto(
    val state: String,
    @SerialName("execution_result")
    val executionResult: JsonObject?,
    @SerialName("error_code")
    val errorCode: String?,
)

@Serializable
private data class DraftDto(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("command_type")
    val commandType: String,
    @SerialName("command_payload")
    val commandPayload: JsonObject,
    @SerialName("command_schema_version")
    val commandSchemaVersion: Int,
    val state: String,
    @SerialName("idempotency_key")
    val idempotencyKey: String,
    @SerialName("payload_hash")
    val payloadHash: String,
    @SerialName("financial_state_version")
    val financialStateVersion: Long,
    @SerialName("execution_result")
    val executionResult: JsonObject?,
    @SerialName("error_code")
    val errorCode: String?,
    val version: Long,
    @SerialName("expires_at")
    val expiresAt: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
) {
    fun toDomain(): AssistantCommandDraft = AssistantCommandDraft(
        id = id,
        conversationId = conversationId,
        userId = userId,
        commandType = commandType,
        commandPayload = commandPayload,
        commandSchemaVersion = commandSchemaVersion,
        state = DraftState.entries.requireWireValue(state),
        idempotencyKey = idempotencyKey,
        payloadHash = payloadHash,
        financialStateVersion = financialStateVersion,
        executionResult = executionResult,
        errorCode = errorCode,
        version = version,
        expiresAt = Instant.parse(expiresAt),
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
    )
}

@Serializable
private data class EventDto(
    val id: String,
    @SerialName("draft_id")
    val draftId: String,
    @SerialName("event_type")
    val eventType: String,
    @SerialName("from_state")
    val fromState: String?,
    @SerialName("to_state")
    val toState: String,
    @SerialName("draft_version")
    val draftVersion: Long,
    @SerialName("created_at")
    val createdAt: String,
) {
    fun toDomain(): AssistantCommandEvent = AssistantCommandEvent(
        id = id,
        draftId = draftId,
        eventType = eventType,
        fromState = fromState?.let { DraftState.entries.requireWireValue(it) },
        toState = DraftState.entries.requireWireValue(toState),
        draftVersion = draftVersion,
        createdAt = Instant.parse(createdAt),
    )
}

@Serializable
private data class AliasWriteDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("product_id")
    val productId: String,
    @SerialName("product_type")
    val productType: String,
    val alias: String,
)

@Serializable
private data class AliasDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("product_id")
    val productId: String,
    @SerialName("product_type")
    val productType: String,
    val alias: String,
    @SerialName("normalized_alias")
    val normalizedAlias: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
) {
    fun toDomain(): AssistantProductAlias = AssistantProductAlias(
        id = id,
        userId = userId,
        productId = productId,
        productType = productType,
        alias = alias,
        normalizedAlias = normalizedAlias,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
    )
}

@Serializable
private data class CheckpointWriteDto(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("checkpoint_key")
    val checkpointKey: String,
    @SerialName("graph_version")
    val graphVersion: String,
    @SerialName("checkpoint_version")
    val checkpointVersion: Long,
    val state: JsonObject,
    @SerialName("checkpoint_created_at")
    val checkpointCreatedAt: String,
    @SerialName("expires_at")
    val expiresAt: String,
)

@Serializable
private data class CheckpointDto(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("checkpoint_key")
    val checkpointKey: String,
    @SerialName("graph_version")
    val graphVersion: String,
    @SerialName("checkpoint_version")
    val checkpointVersion: Long,
    val state: JsonObject,
    @SerialName("checkpoint_created_at")
    val checkpointCreatedAt: String,
    @SerialName("expires_at")
    val expiresAt: String,
) {
    fun toDomain(): AssistantCheckpoint = AssistantCheckpoint(
        id = id,
        conversationId = conversationId,
        userId = userId,
        checkpointKey = checkpointKey,
        graphVersion = graphVersion,
        checkpointVersion = checkpointVersion,
        state = state,
        checkpointCreatedAt = Instant.parse(checkpointCreatedAt),
        expiresAt = Instant.parse(expiresAt),
    )
}

private inline fun <reified T : Enum<T>> Iterable<T>.requireWireValue(value: String): T =
    firstOrNull { enumValue ->
        when (enumValue) {
            is ConversationStatus -> enumValue.wireValue == value
            is MessageRole -> enumValue.wireValue == value
            is InputModality -> enumValue.wireValue == value
            is DraftState -> enumValue.wireValue == value
            else -> false
        }
    } ?: error("Unsupported ${T::class.simpleName} value.")

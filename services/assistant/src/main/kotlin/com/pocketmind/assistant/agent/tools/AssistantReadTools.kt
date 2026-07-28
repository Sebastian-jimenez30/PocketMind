package com.pocketmind.assistant.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.domain.finance.FinancialContextRepository
import com.pocketmind.assistant.domain.finance.FinancialReadService
import com.pocketmind.assistant.domain.finance.TransactionQuery
import com.pocketmind.assistant.domain.memory.AssistantMemoryRepository
import java.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds a user-bound registry containing only read operations.
 *
 * A registry is created per authenticated request/session. No tool receives a
 * generic Supabase client or a write-capable financial repository.
 */
class AssistantReadToolRegistryFactory(
    private val contextRepository: FinancialContextRepository,
    private val memoryRepository: AssistantMemoryRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json {
        encodeDefaults = true
        explicitNulls = false
    },
) {
    fun create(session: AuthenticatedUser): ToolRegistry {
        val service = FinancialReadService(
            contextRepository = contextRepository,
            memoryRepository = memoryRepository,
            session = session,
            clock = clock,
        )
        return ToolRegistry {
            tool(GetFinancialOverviewTool(service, json))
            tool(ListFinancialProductsTool(service, json))
            tool(GetFinancialProductTool(service, json))
            tool(ListFinancialTransactionsTool(service, json))
        }
    }
}

@Serializable
@LLMDescription(
    "Obtiene el panorama financiero agregado por moneda. " +
        "No suma monedas diferentes y no escribe datos.",
)
class GetFinancialOverviewArgs

@Serializable
@LLMDescription("Lista los productos financieros reales del usuario.")
data class ListFinancialProductsArgs(
    @LLMDescription("Incluye productos archivados cuando es true.")
    val includeArchived: Boolean = false,
)

@Serializable
@LLMDescription(
    "Busca un producto por su identificador exacto, nombre exacto o alias " +
        "confirmado. Si hay ambigüedad devuelve candidatos y se debe preguntar.",
)
data class GetFinancialProductArgs(
    @LLMDescription("Referencia expresada por el usuario.")
    val reference: String,
)

@Serializable
@LLMDescription(
    "Consulta movimientos reales. Los filtros nulos no se aplican. " +
        "Los nombres de tipo y estado se escriben en mayúsculas.",
)
data class ListFinancialTransactionsArgs(
    @LLMDescription(
        "Identificador, nombre o alias del producto. " +
            "Debe quedar null para consultar todos.",
    )
    val productReference: String? = null,
    @LLMDescription("Inicio UTC inclusivo en milisegundos Unix.")
    val fromEpochMillis: Long? = null,
    @LLMDescription("Fin UTC inclusivo en milisegundos Unix.")
    val toEpochMillis: Long? = null,
    @LLMDescription("INCOME, EXPENSE o TRANSFER.")
    val type: String? = null,
    @LLMDescription("POSTED, PENDING o IGNORED. Null incluye todos.")
    val status: String? = "POSTED",
    @LLMDescription("Máximo de resultados entre 1 y 200.")
    val limit: Int = 50,
)

private class GetFinancialOverviewTool(
    private val service: FinancialReadService,
    private val json: Json,
) : SimpleTool<GetFinancialOverviewArgs>(
    argsType = typeToken<GetFinancialOverviewArgs>(),
    name = "get_financial_overview",
    description = "Devuelve el panorama financiero real y versionado del usuario.",
) {
    override suspend fun execute(args: GetFinancialOverviewArgs): String =
        json.encodeToString(service.getOverview())
}

private class ListFinancialProductsTool(
    private val service: FinancialReadService,
    private val json: Json,
) : SimpleTool<ListFinancialProductsArgs>(
    argsType = typeToken<ListFinancialProductsArgs>(),
    name = "list_financial_products",
    description =
        "Lista cuentas bancarias, efectivo, ahorros, tarjetas y préstamos reales.",
) {
    override suspend fun execute(args: ListFinancialProductsArgs): String =
        json.encodeToString(service.listProducts(args.includeArchived))
}

private class GetFinancialProductTool(
    private val service: FinancialReadService,
    private val json: Json,
) : SimpleTool<GetFinancialProductArgs>(
    argsType = typeToken<GetFinancialProductArgs>(),
    name = "get_financial_product",
    description =
        "Resuelve una referencia sin coincidencias difusas ni identificadores inventados.",
) {
    override suspend fun execute(args: GetFinancialProductArgs): String =
        json.encodeToString(service.getProduct(args.reference))
}

private class ListFinancialTransactionsTool(
    private val service: FinancialReadService,
    private val json: Json,
) : SimpleTool<ListFinancialTransactionsArgs>(
    argsType = typeToken<ListFinancialTransactionsArgs>(),
    name = "list_financial_transactions",
    description =
        "Lista movimientos reales con filtros, truncamiento y resolución segura de producto.",
) {
    override suspend fun execute(args: ListFinancialTransactionsArgs): String =
        json.encodeToString(
            service.listTransactions(
                TransactionQuery(
                    productReference = args.productReference,
                    fromEpochMillis = args.fromEpochMillis,
                    toEpochMillis = args.toEpochMillis,
                    type = args.type,
                    status = args.status,
                    limit = args.limit,
                ),
            ),
        )
}

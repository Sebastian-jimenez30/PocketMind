package com.pocketmind.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProductReferenceResolverTest {
    private val bank = FinancialAccount(
        id = "bank",
        name = "Cuenta principal",
        type = FinancialAccountType.BANK_ACCOUNT,
        currency = CurrencyCode.COP,
        aliases = listOf("mi Bancolombia"),
    )
    private val card = FinancialAccount(
        id = "card",
        name = "Tarjeta Bancolombia",
        type = FinancialAccountType.CREDIT_CARD,
        currency = CurrencyCode.COP,
        aliases = listOf("la Bancolombia"),
    )

    @Test
    fun `exact name has priority over aliases`() {
        val result = assertIs<ProductReferenceResolution.Resolved>(
            resolveProductReference(" Cuenta principal ", listOf(bank, card)),
        )

        assertEquals(bank.id, result.product.id)
        assertEquals(false, result.matchedByAlias)
    }

    @Test
    fun `confirmed alias resolves a product ignoring case`() {
        val result = assertIs<ProductReferenceResolution.Resolved>(
            resolveProductReference("MI BANCOLOMBIA", listOf(bank, card)),
        )

        assertEquals(bank.id, result.product.id)
        assertEquals(true, result.matchedByAlias)
    }

    @Test
    fun `shared alias remains ambiguous`() {
        val duplicateAlias = card.copy(aliases = listOf("mi Bancolombia"))
        val result = assertIs<ProductReferenceResolution.Ambiguous>(
            resolveProductReference("mi bancolombia", listOf(bank, duplicateAlias)),
        )

        assertEquals(setOf(bank.id, card.id), result.candidates.map { it.id }.toSet())
    }

    @Test
    fun `unique words resolve a product from a natural reference`() {
        val bancolombia = bank.copy(
            name = "Ahorros Bancolombia",
            aliases = emptyList(),
        )
        val result = assertIs<ProductReferenceResolution.Resolved>(
            resolveProductReference(
                "desde mi cuenta de bancolombia",
                listOf(
                    bancolombia,
                    card.copy(name = "Tarjeta Nu", aliases = emptyList()),
                ),
            ),
        )

        assertEquals(bancolombia.id, result.product.id)
    }

    @Test
    fun `partial reference remains ambiguous across matching products`() {
        val bankProduct = bank.copy(
            name = "Ahorros Bancolombia",
            aliases = emptyList(),
        )
        val result = assertIs<ProductReferenceResolution.Ambiguous>(
            resolveProductReference("Bancolombia", listOf(bankProduct, card)),
        )

        assertEquals(
            setOf(bankProduct.id, card.id),
            result.candidates.map { it.id }.toSet(),
        )
    }

    @Test
    fun `generic words alone never resolve a product`() {
        assertIs<ProductReferenceResolution.NotFound>(
            resolveProductReference("mi cuenta", listOf(bank, card)),
        )
    }
}

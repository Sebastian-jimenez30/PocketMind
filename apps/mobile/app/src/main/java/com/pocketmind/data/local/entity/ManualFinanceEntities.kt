package com.pocketmind.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "credit_card_profiles",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CreditCardProfileEntity(
    @PrimaryKey val accountId: String,
    val creditLimitMinorUnits: Long,
    val currency: String,
    val annualInterestBasisPoints: Int,
    val statementClosingDay: Int,
    val paymentDueDay: Int,
    val openingDebtInstallmentCount: Int,
    val openingDebtFirstPaymentAtEpochMillis: Long?,
)

@Entity(
    tableName = "installment_purchases",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("firstPaymentAtEpochMillis")],
)
data class InstallmentPurchaseEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val merchant: String,
    val principalMinorUnits: Long,
    val currency: String,
    val installmentCount: Int,
    val annualInterestBasisPoints: Int,
    val purchasedAtEpochMillis: Long,
    val firstPaymentAtEpochMillis: Long,
    val categoryId: String?,
    val note: String?,
)

@Entity(
    tableName = "credit_card_payments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("paidAtEpochMillis")],
)
data class CreditCardPaymentEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val paidAtEpochMillis: Long,
    val sourceAccountId: String?,
    val note: String?,
)

@Entity(
    tableName = "savings_profiles",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SavingsProfileEntity(
    @PrimaryKey val accountId: String,
    val type: String,
    val annualYieldBasisPoints: Int,
    val openedAtEpochMillis: Long,
    val maturityAtEpochMillis: Long?,
)

@Entity(
    tableName = "savings_movements",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("occurredAtEpochMillis")],
)
data class SavingsMovementEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: String,
    val amountMinorUnits: Long,
    val currency: String,
    val annualYieldBasisPoints: Int?,
    val occurredAtEpochMillis: Long,
    val note: String?,
)

@Entity(
    tableName = "loan_profiles",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LoanProfileEntity(
    @PrimaryKey val accountId: String,
    val annualInterestBasisPoints: Int,
    val monthlyPaymentMinorUnits: Long,
    val currency: String,
    val paymentDueDay: Int,
    val openedAtEpochMillis: Long,
)

@Entity(
    tableName = "loan_payments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("paidAtEpochMillis")],
)
data class LoanPaymentEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val paidAtEpochMillis: Long,
    val sourceAccountId: String?,
    val note: String?,
)

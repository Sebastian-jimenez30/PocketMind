# Contrato remoto de sincronización v1

Este contrato es compartido por Android y la futura aplicación iOS. Los nombres
de propiedades son sensibles a mayúsculas y se codifican en `camelCase`.
Importes y fechas son enteros de 64 bits:

- importes: unidades menores de la moneda (`100` = COP 100; COP no usa
  decimales en la UI actual);
- fechas: milisegundos Unix UTC;
- tasas: puntos básicos anuales (`1250` = 12,50 % E.A.);
- booleanos: JSON `true` o `false`;
- propiedades marcadas `?`: aceptan JSON `null`.

Cada fila remota incluye `schema_version = 1`. El payload no contiene
`user_id`, `entity_type`, `entity_id` ni metadatos remotos.

## Payloads

| `entity_type` | Propiedades del payload |
|---|---|
| `FINANCIAL_SETUP` | `id: Int`, `completedAtEpochMillis: Long` |
| `ACCOUNT` | `id: String`, `name: String`, `type: String`, `currency: String`, `openingBalanceMinorUnits: Long`, `isArchived: Boolean` |
| `TRANSACTION` | `id: String`, `accountId: String`, `type: String`, `amountMinorUnits: Long`, `currency: String`, `occurredAtEpochMillis: Long`, `categoryId: String?`, `merchant: String?`, `note: String?`, `source: String`, `status: String`, `relatedAccountId: String?` |
| `INCOME_SOURCE` | `id: String`, `name: String`, `expectedAmountMinorUnits: Long`, `currency: String`, `recurrence: String`, `nextExpectedAtEpochMillis: Long?`, `isActive: Boolean` |
| `DEBT` | `id: String`, `name: String`, `outstandingBalanceMinorUnits: Long`, `currency: String`, `interestRateAnnualBasisPoints: Int?`, `installmentAmountMinorUnits: Long?`, `dueDayOfMonth: Int?`, `nextDueAtEpochMillis: Long?`, `isActive: Boolean` |
| `SAVINGS_PLAN` | `id: String`, `name: String`, `type: String`, `currentAmountMinorUnits: Long`, `targetAmountMinorUnits: Long?`, `monthlyContributionMinorUnits: Long?`, `currency: String`, `annualYieldBasisPoints: Int?`, `targetDateEpochMillis: Long?`, `isActive: Boolean` |
| `RECURRING_OBLIGATION` | `id: String`, `name: String`, `amountMinorUnits: Long`, `currency: String`, `recurrence: String`, `dueDayOfMonth: Int?`, `isActive: Boolean` |
| `CREDIT_CARD_PROFILE` | `accountId: String`, `creditLimitMinorUnits: Long`, `currency: String`, `annualInterestBasisPoints: Int`, `statementClosingDay: Int`, `paymentDueDay: Int`, `openingDebtInstallmentCount: Int`, `openingDebtFirstPaymentAtEpochMillis: Long?` |
| `INSTALLMENT_PURCHASE` | `id: String`, `accountId: String`, `merchant: String`, `principalMinorUnits: Long`, `currency: String`, `installmentCount: Int`, `annualInterestBasisPoints: Int`, `purchasedAtEpochMillis: Long`, `firstPaymentAtEpochMillis: Long`, `categoryId: String?`, `note: String?` |
| `CREDIT_CARD_PAYMENT` | `id: String`, `accountId: String`, `amountMinorUnits: Long`, `currency: String`, `paidAtEpochMillis: Long`, `sourceAccountId: String?`, `note: String?` |
| `SAVINGS_PROFILE` | `accountId: String`, `type: String`, `annualYieldBasisPoints: Int`, `openedAtEpochMillis: Long`, `maturityAtEpochMillis: Long?` |
| `SAVINGS_MOVEMENT` | `id: String`, `accountId: String`, `type: String`, `amountMinorUnits: Long`, `currency: String`, `annualYieldBasisPoints: Int?`, `occurredAtEpochMillis: Long`, `note: String?` |
| `LOAN_PROFILE` | `accountId: String`, `annualInterestBasisPoints: Int`, `monthlyPaymentMinorUnits: Long`, `currency: String`, `paymentDueDay: Int`, `openedAtEpochMillis: Long` |
| `LOAN_PAYMENT` | `id: String`, `accountId: String`, `amountMinorUnits: Long`, `currency: String`, `paidAtEpochMillis: Long`, `sourceAccountId: String?`, `note: String?` |

`entity_id` debe ser igual a `id`, o a `accountId` en los perfiles de tarjeta,
ahorro y préstamo. Para `FINANCIAL_SETUP` siempre es `"1"`.

## Compatibilidad

- Se pueden añadir propiedades opcionales manteniendo la versión 1 si los
  clientes antiguos pueden ignorarlas.
- Renombrar, eliminar o cambiar el tipo de una propiedad exige incrementar
  `schema_version` y añadir un migrador.
- Un cliente que encuentre una versión mayor a la soportada debe detener el
  pull y conservar Room intacto.
- Un `entity_type` desconocido se conserva remotamente y se ignora localmente.
- La eliminación se representa con `is_deleted = true` y `payload = null`;
  nunca se elimina físicamente durante el sync normal.

## Orden referencial

En push y aplicación local:

1. productos `ACCOUNT`;
2. perfiles de producto;
3. movimientos y pagos dependientes.

Para tombstones el orden es inverso. Aunque Supabase almacena sobres sin claves
foráneas entre payloads, respetar el orden evita violaciones de claves foráneas
en las bases locales.

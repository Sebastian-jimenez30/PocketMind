# Contrato remoto de sincronización v2

Este contrato es compartido por Android y la futura aplicación iOS. Los nombres
de propiedades son sensibles a mayúsculas y se codifican en `camelCase`.
Importes y fechas son enteros de 64 bits:

- importes: unidades menores de la moneda (`100` = COP 100; COP no usa
  decimales en la UI actual);
- fechas: milisegundos Unix UTC;
- tasas: puntos básicos anuales (`1250` = 12,50 % E.A.);
- booleanos: JSON `true` o `false`;
- propiedades marcadas `?`: aceptan JSON `null`.

Los nuevos registros usan `schema_version = 2`. El cliente mantiene lectura
retrocompatible de registros v1 mediante valores predeterminados. El payload no contiene
`user_id`, `entity_type`, `entity_id` ni metadatos remotos.

## Payloads

| `entity_type` | Propiedades del payload |
|---|---|
| `FINANCIAL_SETUP` | `id: Int`, `completedAtEpochMillis: Long` |
| `ACCOUNT` | `id: String`, `name: String`, `type: String`, `currency: String`, `openingBalanceMinorUnits: Long`, `isArchived: Boolean`, `aliasesJson: String` |
| `TRANSACTION` | Propiedades v1 más `manualRevision: Int` |
| `INCOME_SOURCE` | `id: String`, `name: String`, `expectedAmountMinorUnits: Long`, `currency: String`, `recurrence: String`, `nextExpectedAtEpochMillis: Long?`, `isActive: Boolean` |
| `DEBT` | `id: String`, `name: String`, `outstandingBalanceMinorUnits: Long`, `currency: String`, `interestRateAnnualBasisPoints: Int?`, `installmentAmountMinorUnits: Long?`, `dueDayOfMonth: Int?`, `nextDueAtEpochMillis: Long?`, `isActive: Boolean` |
| `SAVINGS_PLAN` | `id: String`, `name: String`, `type: String`, `currentAmountMinorUnits: Long`, `targetAmountMinorUnits: Long?`, `monthlyContributionMinorUnits: Long?`, `currency: String`, `annualYieldBasisPoints: Int?`, `targetDateEpochMillis: Long?`, `isActive: Boolean` |
| `RECURRING_OBLIGATION` | `id: String`, `name: String`, `amountMinorUnits: Long`, `currency: String`, `recurrence: String`, `dueDayOfMonth: Int?`, `isActive: Boolean` |
| `CREDIT_CARD_PROFILE` | Propiedades v1 más `scheduleRuleVersion: Int` |
| `INSTALLMENT_PURCHASE` | Propiedades v1 más `promotionalRatePeriodsJson: String`, `calculationRuleVersion: Int` |
| `CREDIT_CARD_PAYMENT` | Propiedades v1 más `paymentType: String`, `calculationRuleVersion: Int` |
| `SAVINGS_PROFILE` | Propiedades v1 más `calculationRuleVersion: Int` |
| `SAVINGS_MOVEMENT` | Propiedades v1 más `calculationRuleVersion: Int` |
| `LOAN_PROFILE` | Propiedades v1 más `scheduleRuleVersion: Int` |
| `LOAN_PAYMENT` | Propiedades v1 más `paymentType: String`, `calculationRuleVersion: Int` |
| `CUSTOM_CATEGORY` | `id: String`, `name: String`, `createdAtEpochMillis: Long` |
| `BUDGET` | `id: String`, `name: String`, `categoryId: String`, `maxAmountMinorUnits: Long`, `currency: String`, `periodType: String`, `startDateEpochMillis: Long`, `endDateEpochMillis: Long`, `isRecurring: Boolean`, `status: String`, `notificationThresholdPercent: Int` |

`entity_id` debe ser igual a `id`, o a `accountId` en los perfiles de tarjeta,
ahorro y préstamo. Para `FINANCIAL_SETUP` siempre es `"1"`.

## Compatibilidad

- Se pueden añadir propiedades opcionales manteniendo una versión si los
  clientes antiguos pueden ignorarlas.
- Renombrar, eliminar o cambiar el tipo de una propiedad exige incrementar
  `schema_version` y añadir un migrador.
- Un cliente que encuentre una versión mayor a la soportada debe detener el
  pull y conservar Room intacto.
- Un `entity_type` desconocido se conserva remotamente y se ignora localmente.
- La eliminación se representa con `is_deleted = true` y `payload = null`;
  nunca se elimina físicamente durante el sync normal.

Los campos nuevos de v2 tienen valores seguros al leer v1:

- alias y periodos promocionales: `[]`;
- tipo de pago: `CUSTOM`;
- versión de reglas: `1`.
- revisión manual: `0`.

## Orden referencial

En push y aplicación local:

1. productos `ACCOUNT`;
2. perfiles de producto;
3. categorías personales `CUSTOM_CATEGORY`;
4. presupuestos `BUDGET`;
5. movimientos y pagos dependientes.

Para tombstones el orden es inverso. Aunque Supabase almacena sobres sin claves
foráneas entre payloads, respetar el orden evita violaciones de claves foráneas
en las bases locales.

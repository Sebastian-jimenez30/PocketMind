# Herramientas de lectura del asistente

## Objetivo

Las herramientas de lectura permiten que el asistente consulte el estado
financiero real antes de interpretar una petición. No ejecutan comandos, no
modifican registros y no sustituyen la confirmación del usuario.

Implementación de referencia:

- `FinancialContextRepository`;
- `SupabaseFinancialContextRepository`;
- `FinancialReadService`;
- `AssistantReadToolRegistryFactory`.

## Frontera de seguridad

1. Cada registro se consulta con el JWT previamente validado del usuario.
2. Supabase vuelve a comprobar `auth.uid()` mediante RLS.
3. El backend nunca usa `service_role`.
4. El repositorio financiero solo expone `fetchSnapshot`.
5. El registro Koog no recibe repositorios con métodos de escritura.
6. Los filtros no aceptan un `userId`; la identidad proviene de la sesión.
7. Las herramientas no generan ni corrigen identificadores.
8. Una referencia ambigua produce candidatos y exige una pregunta.

## Fuente de datos

Room continúa siendo la fuente local para Android. El servicio reconstruye su
contexto desde `finance_sync_records`, que representa el último estado
sincronizado con Supabase.

El snapshot incluye:

- productos y movimientos;
- perfiles, compras y pagos de tarjetas;
- perfiles y movimientos de ahorro;
- perfiles y pagos de préstamos;
- ingresos esperados, deudas, planes de ahorro y obligaciones heredadas del
  onboarding.

Los tombstones no se convierten en entidades activas, pero sí participan en la
versión del estado.

## Versión y vigencia

`stateVersion` es un `Long` derivado de SHA-256 sobre los metadatos ordenados de
todos los sobres remotos:

```text
entity_type | entity_id | schema_version | is_deleted | updated_at_epoch_millis
```

Por tanto, cambia cuando se crea, actualiza o elimina cualquier registro
sincronizado. Un borrador posterior debe guardar esta versión y volver a leer el
snapshot antes de ejecutarse.

Cada resultado incluye:

- `stateVersion`;
- `latestRemoteUpdateEpochMillis`;
- `observedAtEpochMillis`;
- `remoteRecordCount`;
- tipos desconocidos ignorados;
- `mayLagUnsyncedDeviceChanges = true`;
- `mustRevalidateBeforeWrite = true`.

El backend no puede afirmar que el teléfono ya publicó su outbox. Por eso no
presenta el snapshot remoto como una copia instantánea del dispositivo.

## Compatibilidad

La versión de sync soportada es `2`.

- Un tipo conocido con una versión superior rechaza todo el snapshot.
- Un tipo desconocido se ignora, se informa en metadata y participa en
  `stateVersion`.
- Un payload inválido o cuyo identificador no coincide con `entity_id` se
  rechaza completo.
- No se omiten silenciosamente entidades conocidas dañadas.
- Los valores predeterminados de v1 siguen el contrato de sync v2.

## Alias y resolución

Los alias proceden de dos fuentes confirmadas:

1. `aliasesJson` del producto sincronizado;
2. `assistant_product_aliases`.

Se normalizan espacios y mayúsculas únicamente para comparar. El texto
persistido se conserva.

Orden de resolución:

1. identificador exacto;
2. nombre exacto normalizado;
3. alias confirmado exacto normalizado.

No hay coincidencia aproximada. Los resultados posibles son:

| Estado | Conducta |
|---|---|
| `resolved` | Devuelve un producto real. |
| `ambiguous` | Devuelve únicamente candidatos del usuario. |
| `not_found` | No inventa un producto; el agente debe preguntar. |

## Catálogo Koog

| Herramienta | Responsabilidad |
|---|---|
| `get_financial_overview` | Panorama agregado, siempre separado por moneda. |
| `list_financial_products` | Productos activos o, si se pide, archivados. |
| `get_financial_product` | Resolución exacta y detalle calculado. |
| `list_financial_transactions` | Movimientos filtrados, ordenados y limitados. |

No existe ninguna herramienta `create`, `update`, `delete`, `save` o `execute`
en este registro.

## Cálculos

Los cálculos reutilizan las reglas del módulo `shared`:

- `calculateCreditCardOverview`;
- `calculateSavingsProjection`;
- `calculateLoanOverview`;
- `resolveProductReference`.

Para efectivo y cuentas bancarias:

- ingreso publicado suma;
- gasto publicado resta;
- transferencia resta al origen y suma al producto relacionado.

Las monedas nunca se convierten ni se suman entre sí. El panorama devuelve un
bloque independiente por `COP`, `USD` u otra moneda que se soporte después.

Los productos que requieren perfil y no lo tienen se devuelven con
`dataStatus = missing_profile`; no se completan con datos inferidos.

## Límites de movimientos

- `limit`: de 1 a 200;
- fechas: milisegundos Unix UTC inclusivos;
- tipo: `INCOME`, `EXPENSE` o `TRANSFER`;
- estado: `POSTED`, `PENDING`, `IGNORED` o `null`;
- orden: fecha descendente y luego ID;
- el resultado indica `totalMatches`, `returnedCount` y `truncated`.

## Errores

| Código | Significado |
|---|---|
| `FINANCIAL_SCHEMA_UNSUPPORTED` | El cliente usa una versión de sync más nueva. |
| `FINANCIAL_DATA_INVALID` | El snapshot es inconsistente o está dañado. |
| `FINANCIAL_CONTEXT_UNAVAILABLE` | Supabase no respondió correctamente. |
| `UNAUTHORIZED` | El JWT fue rechazado o expiró. |

Los errores no contienen payloads, movimientos, JWT ni cuerpos devueltos por
Supabase.

## Validación

Desde `apps/mobile`:

```powershell
.\gradlew.bat :assistant-service:test
.\gradlew.bat :shared:testAndroidHostTest
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Las pruebas cubren:

- JWT y filtro de usuario en PostgREST;
- mapeo del contrato de sync;
- rechazo de esquema futuro e identidad inconsistente;
- separación de monedas;
- saldos deterministas;
- alias resuelto, ambiguo e inexistente;
- filtros y truncamiento de movimientos;
- catálogo Koog sin herramientas de escritura.

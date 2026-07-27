# Sincronización de datos

Esta es la guía operativa de la persistencia Android y Supabase. La decisión
vinculante está en
[`architecture/ADR-001-SINCRONIZACION-OFFLINE-FIRST.md`](architecture/ADR-001-SINCRONIZACION-OFFLINE-FIRST.md).

## Estado implementado

| Componente | Estado |
|---|---|
| Room 5 y migración 4→5 | Implementado; pendiente de ejecución manual en dispositivo |
| Outbox transaccional para 14 tipos | Implementada |
| Adopción segura de datos anteriores | Implementada |
| Push idempotente y tombstones | Implementado |
| Pull y aplicación atómica | Implementado |
| Cambio de usuario sin fuga de caché | Implementado |
| WorkManager inmediato y cada 15 min | Implementado |
| Indicador y reintento en Perfil | Implementado |
| Tabla, índices, constraints y RLS Supabase | Desplegados el 2026-07-27 |
| Realtime | Fuera del MVP; no necesario para consistencia eventual |
| Implementación iOS | Pendiente; reutilizará el contrato remoto |

## Entidades sincronizadas

- configuración financiera;
- productos/cuentas;
- movimientos;
- fuentes de ingreso;
- deudas y planes de ahorro heredados del onboarding;
- obligaciones recurrentes;
- perfiles de tarjeta, ahorro y préstamo;
- compras a cuotas;
- pagos de tarjeta y préstamo;
- movimientos de ahorro.

Agregar una entidad requiere:

1. hacer serializable su entidad local;
2. añadir un `SyncEntityType`;
3. crear triggers Room para insert, update y delete;
4. añadir lectura, siembra y aplicación en `SyncDao`;
5. ampliar codificación y decodificación en `FinanceSyncEngine`;
6. agregar pruebas de migración, orden y round-trip;
7. incrementar `schema_version` si cambia un payload existente.

## Estados visibles

| Estado | Significado |
|---|---|
| Guardando | Hay una sincronización activa |
| Cambios pendientes | Room contiene operaciones aún no confirmadas remotamente |
| Sincronizado | Último push/pull terminó y no quedan operaciones |
| Sin conexión/error | Los datos locales siguen seguros y se reintentará |

No se debe mostrar “sincronizado” basándose solo en tener Internet.

## Protocolo de validación

### Automatizado

Desde `apps/mobile`:

```powershell
.\gradlew.bat :shared:testAndroidHostTest
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

Con un dispositivo físico conectado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

### Manual en dos dispositivos

1. Iniciar sesión con la misma cuenta en A y B.
2. En A crear un producto, un movimiento y un ahorro.
3. En Perfil, comprobar “Sincronizado”.
4. Abrir B o volverlo a primer plano y verificar los mismos datos.
5. Desactivar Internet en A y crear/editar datos.
6. Verificar que aparecen inmediatamente y Perfil indica pendientes.
7. Recuperar Internet y confirmar que llegan a B.
8. Eliminar un movimiento en B y comprobar que desaparece en A.
9. Intentar cerrar sesión con cambios offline: debe conservar la sesión y
   explicar que primero hay que sincronizar.
10. Cerrar sesión con todo sincronizado e iniciar otra cuenta: no debe aparecer
    ningún dato financiero de la cuenta anterior.

### Supabase

Confirmar en Table Editor:

- la tabla `finance_sync_records`;
- RLS habilitado;
- políticas para select, insert y update;
- filas separadas por `user_id`;
- eliminaciones representadas por `is_deleted = true` y `payload = null`.

Nunca editar manualmente `user_id`, `entity_type` o `entity_id` en producción.

## Diagnóstico

- “Pendientes” con red: tocar Sincronización en Perfil y revisar el mensaje.
- Inicio en otro dispositivo sin datos: confirmar que usa la misma cuenta y que
  la migración remota está aplicada.
- Error al abrir una instalación actualizada: ejecutar la prueba Room 4→5 y
  conservar el log completo.
- Cambio que reaparece: revisar qué dispositivo realizó la última escritura;
  el conflicto actual es última escritura recibida.

No se debe borrar la base Room como solución general: hacerlo puede destruir
cambios que todavía estén en la outbox.

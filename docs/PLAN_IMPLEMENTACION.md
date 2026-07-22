# PocketMind — Plan rector de implementación

> **Estado:** base de planeación inicial
> **Producto:** aplicación Android nativa para registrar y comprender las
> finanzas personales a partir de notificaciones bancarias.

Este documento fija el contexto, alcance y reglas de PocketMind. Es una guía
viva: se actualizará mediante decisiones explícitas, sin reescribir la historia
de decisiones importantes.

## 1. Visión del producto

PocketMind reduce el registro manual de finanzas personales. Tras la instalación,
el consentimiento informado y la activación del acceso a notificaciones, la
aplicación detecta operaciones bancarias, extrae sus datos, las clasifica y las
presenta en un dashboard local y sincronizado.

El objetivo no es solamente acumular movimientos: es ofrecer información
comprensible sobre hábitos financieros, respetando privacidad, control del
usuario y funcionamiento sin conexión.

### Flujo principal

```text
Notificación bancaria
  → validación del banco
  → normalización y análisis
  → monto, comercio y tipo de operación
  → deduplicación
  → clasificación automática
  → Room (fuente de verdad local)
  → UI inmediata
  → sincronización en segundo plano
```

## 2. Alcance

### MVP

- Registro, inicio y cierre de sesión.
- Onboarding claro y activación de `NotificationListenerService`.
- Lectura de notificaciones bancarias autorizadas.
- Detección de ingresos y gastos.
- Extracción de monto, comercio, fecha y tipo de operación.
- Clasificación automática con reglas y nivel de confianza.
- Dashboard de balance, ingresos, gastos y categorías.
- Historial, filtros, búsqueda y edición de movimientos.
- Persistencia local con Room y sincronización con Supabase.
- Estados sin conexión, errores y reintentos.

### Versión 2

- OCR de facturas con ML Kit.
- Importación de extractos PDF y archivos Excel/CSV.
- Metas de ahorro, presupuestos, alertas y operaciones recurrentes.
- Detección de suscripciones.

### Versión 3

- Clasificación y normalización asistidas por IA.
- Chat financiero en lenguaje natural.
- Resúmenes, recomendaciones explicables y presupuestos asistidos.
- Consultas como: “¿En qué gasté más este mes?” y “¿Qué suscripciones tengo?”.

### Fuera de alcance inicial

- Automatizar transacciones desde la aplicación.
- Asesoría financiera, crediticia, tributaria o de inversión profesional.
- Soporte genérico para todos los bancos desde el primer lanzamiento.
- Llamadas directas desde el dispositivo a proveedores de IA con claves secretas.

## 3. Reglas no negociables

1. **Privacidad por diseño.** El acceso a notificaciones exige consentimiento
   informado, una explicación sencilla y una alternativa clara si se rechaza.
2. **Local-first.** Room es la fuente de verdad para la interfaz; la red no puede
   impedir registrar ni consultar datos locales.
3. **No inventar datos.** Si un parser no tiene confianza suficiente, el
   movimiento queda pendiente de revisión o sin clasificar.
4. **No duplicar movimientos.** La ingestión y sincronización deben ser
   idempotentes.
5. **La edición del usuario prevalece.** Una clasificación, comercio o monto
   corregido manualmente no puede ser sobrescrito automáticamente.
6. **Mínima recolección.** Solo se guarda el contenido de notificación necesario
   para el producto; nunca se usa para analítica o logs.
7. **Secretos fuera de la app.** Ninguna clave privada (incluida la de OpenAI)
   se incorpora en el APK, repositorio o `BuildConfig` de producción.
8. **IA con control.** OpenAI se consume desde Supabase Edge Functions o un
   backend posterior; se aplican límites, consentimiento, trazabilidad y datos
   minimizados.
9. **Cambios de esquema seguros.** Toda migración de Room incluye una prueba de
   migración y una estrategia de compatibilidad.
10. **Calidad como criterio de entrega.** Una funcionalidad no está terminada
    sin pruebas proporcionales, estados de error y revisión de accesibilidad.
11. **No PII en telemetría.** No registrar texto de notificaciones, montos,
    tokens, correos ni identificadores personales en logs o crash reports.

## 4. Stack tecnológico

| Área | Decisión |
|---|---|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Arquitectura | Clean Architecture + MVVM + flujo unidireccional de estado |
| Estado y concurrencia | ViewModel, StateFlow, Coroutines y Flow |
| Inyección de dependencias | Hilt |
| Base local | Room |
| Navegación | Navigation Compose tipada |
| Trabajo diferido | WorkManager |
| Autenticación y sincronización | Supabase Auth + PostgreSQL + RLS |
| Acceso bancario | NotificationListenerService |
| IA futura | OpenAI mediante Supabase Edge Functions/backend |
| OCR futuro | Google ML Kit |
| Pruebas | JUnit, MockK/fakes, Turbine, Compose UI Test y Maestro/Espresso |
| Observabilidad | Crash reporting y analítica sin PII |

Las versiones concretas se decidirán al inicializar el proyecto y se centralizarán
en `gradle/libs.versions.toml`.

## 5. Arquitectura objetivo

### Módulos Gradle

```text
:app                         punto de entrada, navegación y wiring final
:core:common                 Result, dispatchers, errores y utilidades
:core:model                  modelos compartidos y contratos básicos
:core:ui                     design system y componentes reutilizables
:core:database               Room, entidades, DAOs y migraciones
:core:network                cliente y configuración de Supabase
:core:security               sesión, cifrado y almacenamiento seguro
:core:testing                fakes, builders y fixtures de prueba
:feature:auth
:feature:onboarding
:feature:notification-ingestion
:feature:dashboard
:feature:transactions
:feature:settings
```

La modularización se aplicará de manera pragmática: se podrá iniciar con módulos
`core` y `feature` esenciales, pero los límites anteriores no se romperán al
crecer el código.

### Capas de cada funcionalidad

```text
feature/<nombre>/
  presentation/              Compose, ViewModels, UiState y UiEvent
  domain/                    casos de uso y contratos de repositorio
  data/                      repositorios, fuentes de datos y mapeadores
```

Flujo obligatorio:

```text
Acción de usuario / evento del sistema
  → ViewModel
  → caso de uso
  → repositorio
  → fuente local/remota
  → StateFlow de UiState
  → Composable
```

### Reglas de implementación

- Los Composables no acceden a DAOs, APIs ni repositorios.
- Las interfaces de repositorio viven en `domain`; sus implementaciones, en
  `data`.
- Los ViewModels no contienen reglas de parsing, SQL ni lógica de red.
- No se usa `GlobalScope`, `!!` ni I/O en el hilo principal.
- Las dependencias se inyectan; no se crean manualmente en pantallas ni casos de
  uso.
- Cada operación asíncrona expone resultados y errores explícitos.
- Las pantallas representan carga, vacío, contenido y error.

## 6. Modelo de datos inicial

Entidades previstas:

- `Transaction`: monto, moneda, fecha, tipo, comercio, categoría, origen,
  confianza del parser, estado de sincronización y marcas de edición manual.
- `Merchant`: comercio normalizado y alias conocidos.
- `Category`: categorías y subcategorías del usuario/sistema.
- `Account`: cuenta o producto financiero, sin almacenar información innecesaria.
- `NotificationRecord`: huella para deduplicación y metadatos mínimos de parsing.
- `ClassificationRule`: reglas locales, precedencia y confianza.
- `SyncMetadata`: versiones, marcas de tiempo y estado de cola.
- `UserPreferences`: preferencias, consentimiento y configuración.

Toda entidad sincronizable debe contar con identificador estable, timestamps y
metadatos suficientes para resolver conflictos sin perder una edición del usuario.

## 7. Ingestión de notificaciones

El primer banco objetivo es **Bancolombia**. Se ampliará el soporte banco por
banco mediante parsers independientes y casos de prueba anonimizados.

```kotlin
interface BankNotificationParser {
    val supportedPackages: Set<String>
    suspend fun parse(notification: BankNotification): ParseResult
}
```

El pipeline debe:

1. Recibir el evento de `NotificationListenerService`.
2. Filtrar paquetes explícitamente autorizados.
3. Normalizar el contenido sin persistir texto superfluo.
4. Elegir el parser correspondiente.
5. Extraer datos y calcular confianza.
6. Generar una huella de deduplicación.
7. Clasificar mediante reglas locales e historial del usuario.
8. Guardar en Room en una transacción atómica.
9. Programar sincronización sin bloquear la interfaz.

Un parser no reconocido, ambiguo o inválido se registra de forma segura como
evento no procesado/pending review, sin crear un gasto incorrecto.

## 8. Sincronización, autenticación e IA

### Supabase

- Supabase Auth gestiona sesión y renovación de token.
- PostgreSQL usa Row Level Security en todas las tablas de usuario.
- Room conserva una cola de operaciones pendientes y permite reintentos.
- La política inicial de conflicto es: **edición manual del usuario > dato
  automático**.
- Cerrar sesión y borrar cuenta deben tener políticas explícitas para los datos
  locales y remotos antes de su implementación.

### IA

La IA es una capacidad remota, opcional y no bloqueante. Para la primera versión:

- Priorizar reglas deterministas y aprendizaje de correcciones locales.
- Usar IA solo para casos no resueltos o consultas expresamente solicitadas.
- Enviar el mínimo contexto posible y nunca secretos del dispositivo.
- Ejecutar llamadas en Edge Functions, con rate limit, auditoría de costo y
  validación de respuesta.
- Presentar resultados como sugerencias explicables, no como asesoría financiera.

## 9. UX, accesibilidad y diseño

Pantallas MVP: autenticación, onboarding, permiso de notificaciones, dashboard,
historial, búsqueda/filtros, detalle/edición y configuración.

Reglas:

- Material 3, design tokens y recursos de texto; no colores, dimensiones o
  strings hardcodeados en pantallas.
- Tema claro y oscuro.
- Objetivos táctiles mínimos de 48dp, contraste WCAG AA y soporte TalkBack.
- Soporte de tamaño de fuente, rotación y tamaños compactos, medianos y tablet.
- Estado de conexión y degradación offline visibles cuando sea relevante.
- La navegación evita duplicar destinos y maneja explícitamente el back stack.

## 10. Plan por fases

### Fase 0 — Descubrimiento y decisiones

- Cerrar el alcance del MVP y métricas de éxito.
- Definir las categorías, moneda COP y zona horaria inicial.
- Reunir corpus anonimizado de notificaciones de Bancolombia.
- Definir política de privacidad, retención, eliminación y consentimiento.
- Documentar contratos de datos y reglas de parsing.

**Salida:** PRD, criterios de aceptación y matriz de notificaciones.

### Fase 1 — Fundación

- Crear proyecto Android en Android Studio y módulos iniciales.
- Configurar Compose, Hilt, Room, Navigation, WorkManager y versiones.
- Crear design system, navegación esqueleto, flavors y configuración segura.
- Incorporar CI con formato, análisis estático, pruebas unitarias y build debug.
- Configurar observabilidad sin PII.

**Salida:** aplicación compilable con navegación base y CI en verde.

### Fase 2 — Datos, sesión y sincronización

- Implementar modelo Room, DAOs, migraciones y repositorios.
- Configurar Auth, RLS y contrato de Supabase.
- Implementar sesión, cierre de sesión, reintentos y cola de sincronización.
- Construir pantallas de autenticación y estados offline.

**Salida:** movimientos de prueba locales sincronizables de forma segura.

### Fase 3 — Ingestión bancaria y clasificación

- Construir onboarding y comprobación de acceso a notificaciones.
- Implementar listener, parser Bancolombia, deduplicación y trazabilidad.
- Crear reglas de clasificación inicial y corrección manual.
- Preparar fixtures anonimizados y pruebas de regresión por cada patrón.

**Salida:** una notificación soportada genera un movimiento local correcto o una
revisión pendiente, nunca un dato inventado.

### Fase 4 — Experiencia MVP

- Dashboard, historial, búsqueda, filtros y detalle de movimiento.
- Estados de carga, vacío, error y sin conexión.
- Accesibilidad, tema oscuro, adaptación de pantalla y rendimiento básico.

**Salida:** MVP navegable y usable de punta a punta.

### Fase 5 — Endurecimiento y lanzamiento

- Auditoría de seguridad y privacidad.
- Profiling de arranque, memoria, batería y recomposiciones.
- E2E, pruebas en dispositivos físicos y OEMs prioritarios.
- R8, firma, política de privacidad, monitoreo y distribución interna.

**Salida:** AAB candidato a lanzamiento en canal interno de Play Console.

### Fase 6 — Evolución

Implementar V2 y V3 solo tras validar el MVP, precisión de parsers, retención y
uso real. Cada nueva capacidad debe mantener las reglas de privacidad y pruebas.

## 11. Estrategia de pruebas

| Nivel | Objetivo | Ejemplos |
|---|---|---|
| Unitarias | lógica aislada | parsers, casos de uso, ViewModels, mapeadores |
| Integración | límites entre capas | Room, repositorios, sincronización, migraciones |
| UI | comportamiento de pantalla | navegación, filtros, edición y estados |
| E2E | recorridos críticos | login, onboarding, ingestión y dashboard |
| Manual/dispositivo | sistema y OEM | listener, permisos, batería y red inestable |

Reglas de calidad:

- Todo bug recibe una prueba de regresión antes o junto a su corrección.
- Los fixtures de notificaciones se anonimizarán y no contendrán datos reales.
- Las pruebas son herméticas: no comparten estado mutable ni dependen de red real.
- Se priorizan fakes sobre mocks complejos.
- Como objetivo inicial, `domain` y `presentation` mantienen una cobertura alta;
  el porcentaje exacto se fijará en la configuración de CI.

Casos críticos mínimos:

- Una misma notificación nunca genera dos transacciones.
- Sin red, el movimiento se guarda y aparece en la UI.
- Al recuperar red, la operación pendiente se sincroniza una sola vez.
- Una edición manual no se pierde por clasificación ni sincronización.
- Un parser inválido no cierra la app ni registra un movimiento falso.
- Revocar el acceso a notificaciones se comunica de forma útil y segura.
- Las migraciones preservan datos existentes.

## 12. Android Studio, dispositivos y entrega

- Emuladores: UI, navegación, tamaños de pantalla, rotación y tema.
- Dispositivos físicos: `NotificationListenerService`, permisos y restricciones
  reales de fabricantes.
- Android Studio Profiler y Layout Inspector: rendimiento y Compose.
- LeakCanary: solo en builds de desarrollo.
- Antes de releases: al menos dispositivos Samsung, Xiaomi/MIUI y un móvil de
  gama media; ampliar según base real de usuarios.

Pipeline esperado:

```text
PR → formato + análisis estático + unit tests + build debug
main → integración + build staging + distribución QA
release → E2E + dispositivos + AAB firmado + Play internal testing
```

Las publicaciones siguen canales internos, cerrados y despliegue gradual,
monitorizando crashes, ANR, batería y exactitud del parser.

## 13. Sesiones de planeación

1. **Producto y privacidad:** MVP, consentimiento, borrado, retención y bancos.
2. **Arquitectura:** módulos, entidades, Supabase, seguridad y sincronización.
3. **Notificaciones:** corpus, patrones, parser y confianza.
4. **UX/UI:** flujos, dashboard, lenguaje y estados de excepción.
5. **Calidad:** fixtures, automatización, dispositivos y criterios de salida.
6. **IA y evolución:** funciones, límites, costo, datos permitidos y seguridad.

## 14. Skills futuras del repositorio

Después de cerrar las decisiones de arquitectura, se crearán skills locales que
automaticen y preserven convenciones reales del proyecto:

- `pocketmind-architecture`
- `pocketmind-notification-parsers`
- `pocketmind-data-sync`
- `pocketmind-testing`
- `pocketmind-security-privacy`

No se crearán antes de tener contratos, estructura de módulos y convenciones
validadas; deben codificar decisiones reales, no suposiciones.

## 15. Próxima decisión

La primera sesión debe cerrar el alcance exacto del MVP y reunir ejemplos
anonimizados de notificaciones de Bancolombia. Con ello se puede definir el
contrato del parser y comenzar la Fase 1 sin decisiones implícitas.

# PocketMind — Plan rector de implementación

> **Estado:** base de planeación inicial
> **Producto:** aplicaciones móviles nativas para Android e iOS que registran y
> explican finanzas personales. Android incorpora automatización desde
> notificaciones; iOS comienza con captura manual, voz y foto.

Este documento fija el contexto, alcance y reglas de PocketMind. Es una guía
viva: se actualizará mediante decisiones explícitas, sin reescribir la historia
de decisiones importantes.

## 1. Visión del producto

PocketMind reduce el registro manual de finanzas personales. Ambas aplicaciones
permiten capturar, organizar y analizar operaciones. En Android, tras el
consentimiento informado y la activación del acceso a notificaciones, también se
detectan operaciones bancarias, se extraen sus datos y se clasifican.

El objetivo no es solamente acumular movimientos: es ofrecer información
comprensible sobre hábitos financieros, respetando privacidad, control del
usuario y funcionamiento sin conexión.

### Flujo principal

```text
Android: notificación bancaria
  → validación del banco → análisis → deduplicación → clasificación
  → almacenamiento local → UI inmediata → sincronización

iOS: captura manual, voz o foto
  → borrador verificable → almacenamiento local → UI inmediata → sincronización
```

## 2. Alcance

### MVP

- Registro, inicio y cierre de sesión.
- Onboarding claro en ambas plataformas; activación de
  `NotificationListenerService` solo en Android.
- Lectura de notificaciones bancarias autorizadas solo en Android.
- Detección de ingresos y gastos.
- Extracción de monto, comercio, fecha y tipo de operación.
- Clasificación automática con reglas y nivel de confianza.
- Dashboard de balance, ingresos, gastos y categorías.
- Historial, filtros, búsqueda y edición de movimientos.
- Persistencia local por plataforma y sincronización con Supabase.
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
2. **Local-first.** La base local de cada plataforma es fuente de verdad para la
   interfaz; la red no puede impedir registrar ni consultar datos locales.
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
9. **Cambios de esquema seguros.** Toda migración de persistencia local incluye
   una prueba de migración y una estrategia de compatibilidad.
10. **Calidad como criterio de entrega.** Una funcionalidad no está terminada
    sin pruebas proporcionales, estados de error y revisión de accesibilidad.
11. **No PII en telemetría.** No registrar texto de notificaciones, montos,
    tokens, correos ni identificadores personales en logs o crash reports.

## 4. Stack tecnológico

| Área | Decisión |
|---|---|
| Lenguajes | Kotlin compartido; Swift para la capa iOS |
| UI | Jetpack Compose + Material 3 (Android); SwiftUI (iOS) |
| Arquitectura | Clean Architecture + KMP + flujo unidireccional de estado |
| Estado y concurrencia | Coroutines y Flow compartidos; ViewModel Android / estado SwiftUI iOS |
| Inyección de dependencias | Hilt en Android; composición explícita o DI iOS |
| Base local | Room en Android; SwiftData/Core Data en iOS |
| Navegación | Navigation Compose (Android); NavigationStack (iOS) |
| Trabajo diferido | WorkManager (Android); BGTaskScheduler según necesidad iOS |
| Autenticación y sincronización | Supabase Auth + PostgreSQL + RLS |
| Acceso bancario | NotificationListenerService, exclusivamente Android |
| IA futura | OpenAI mediante Supabase Edge Functions/backend |
| OCR futuro | Google ML Kit |
| Pruebas | JUnit, MockK/fakes, Turbine, Compose UI Test y Maestro/Espresso |
| Observabilidad | Crash reporting y analítica sin PII |

Las versiones concretas se decidirán al inicializar el proyecto y se centralizarán
en `gradle/libs.versions.toml`.

## 5. Arquitectura objetivo

### Estructura multiplataforma

```text
shared/
  core/                       modelos, errores, validaciones y casos de uso KMP
  data/                       contratos remotos, mapeadores y política de sync
  testing/                    fixtures, builders y fakes compartidos
androidApp/
  app/                        punto de entrada Compose, Hilt y navegación
  database/                   Room, entidades, DAO y migraciones Android
  notification-ingestion/     listener y parsers bancarios Android
iosApp/
  PocketMind/                 SwiftUI, navegación y composición de dependencias
  Persistence/                SwiftData/Core Data y migraciones iOS
```

Se comparte la lógica que debe ser idéntica: modelos de dominio, validaciones,
cálculos financieros, reglas de categorización, deduplicación, contratos de
repositorio y política de conflictos. La UI, permisos, persistencia local y APIs
del sistema son específicas de cada plataforma.

### Capas de cada funcionalidad

```text
shared/<nombre>/
  commonMain/                dominio y reglas compartidas
  androidMain/               adaptadores Android cuando sean necesarios
  iosMain/                   adaptadores iOS cuando sean necesarios
```

Flujo obligatorio:

```text
Acción de usuario / evento del sistema
  → capa de presentación nativa
  → caso de uso compartido
  → contrato de repositorio
  → fuente local/remota de plataforma
  → estado nativo de UI
```

### Reglas de implementación

- Los Composables y las vistas SwiftUI no acceden a DAOs, APIs ni repositorios.
- Las interfaces de repositorio viven en `domain`; sus implementaciones, en
  `data`.
- La capa de presentación no contiene reglas de parsing, SQL ni lógica de red.
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

## 7. Capacidades por plataforma

| Capacidad | Android | iOS inicial |
|---|---|---|
| Autenticación, perfil y sincronización | Sí | Sí |
| Registro manual, voz y foto/OCR | Sí | Sí |
| Cuentas, ahorros, tarjetas, préstamos y análisis | Sí | Sí |
| Persistencia local offline | Room | SwiftData/Core Data |
| Lectura de notificaciones bancarias de terceros | Sí | No |

La ausencia inicial del listener en iOS no implica un modelo de datos distinto:
todas las fuentes producen la misma entidad `Transaction`. Android añade el
origen `NOTIFICATION`; iOS utiliza inicialmente `MANUAL`, `VOICE` y `RECEIPT`.

## 8. Ingestión de notificaciones en Android

El primer banco objetivo es **Bancolombia** en Android. Se ampliará el soporte
banco por banco mediante parsers independientes y casos de prueba anonimizados.

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

## 9. Sincronización, autenticación e IA

### Supabase

- Supabase Auth gestiona sesión y renovación de token.
- PostgreSQL usa Row Level Security en todas las tablas de usuario.
- Cada persistencia local conserva una cola de operaciones pendientes y permite
  reintentos; la política de conflictos se comparte en KMP.
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

## 10. UX, accesibilidad y diseño

Pantallas MVP en ambas plataformas: autenticación, onboarding, dashboard,
historial, búsqueda/filtros, detalle/edición y configuración. El permiso y
estado de notificaciones bancarias aparece solo en Android.

La dirección visual, los tokens, componentes, patrones de pantalla y criterios
de revisión están definidos en
[Identidad visual y experiencia de usuario](IDENTIDAD_VISUAL.md). Ese documento
es obligatorio para cualquier trabajo de UI/UX y se aplica mediante la skill
local `pocketmind-design-system`.

Reglas:

- Material 3 y tokens Android; SwiftUI y tokens equivalentes iOS. No se usan
  colores, dimensiones o strings hardcodeados en pantallas.
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

- Crear el proyecto KMP, `androidApp` en Android Studio y `iosApp` en Xcode.
- Configurar Compose/Hilt/Room/WorkManager para Android y SwiftUI/SwiftData para
  iOS; extraer dominio y sincronización compartidos.
- Crear design system, navegación esqueleto, flavors y configuración segura.
- Incorporar CI con formato, análisis estático, pruebas unitarias y build debug.
- Configurar observabilidad sin PII.

**Salida:** aplicación compilable con navegación base y CI en verde.

### Fase 2 — Datos, sesión y sincronización

- Implementar el modelo compartido, Room/DAOs/migraciones Android y persistencia
  local/migraciones iOS mediante adaptadores de repositorio.
- Configurar Auth, RLS y contrato de Supabase.
- Implementar sesión, cierre de sesión, reintentos y cola de sincronización.
- Construir pantallas de autenticación y estados offline.

**Salida:** movimientos de prueba locales sincronizables de forma segura.

### Fase 3 — Ingestión bancaria y clasificación

- Construir onboarding común; añadir comprobación de acceso a notificaciones
  exclusivamente en Android.
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
| Integración | límites entre capas | Room Android, persistencia iOS, repositorios y sincronización |
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
- Revocar el acceso a notificaciones se comunica de forma útil y segura en Android.
- Las migraciones preservan datos existentes.

## 12. Herramientas, dispositivos y entrega multiplataforma

- Emuladores Android y simuladores iOS: UI, navegación, tamaños, rotación y tema.
- Dispositivos Android físicos: `NotificationListenerService`, permisos y
  restricciones reales de fabricantes; iPhone físico: permisos, cámara, voz y
  sincronización iOS.
- Android Studio Profiler/Layout Inspector y Xcode Instruments: rendimiento.
- LeakCanary: solo en builds de desarrollo.
- Antes de releases: al menos dispositivos Samsung, Xiaomi/MIUI y un móvil de
  gama media; ampliar según base real de usuarios.

Pipeline esperado:

```text
PR → formato + análisis estático + pruebas compartidas + builds Android/iOS
main → integración + staging + distribución QA en ambas plataformas
release → E2E + dispositivos + AAB para Play + TestFlight/App Store
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

- `pocketmind-design-system` — creada; preserva la identidad visual y UX.
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

# PocketMind Assistant Service

Servicio JVM que alojará el agente financiero de PocketMind. Usa Ktor para la
API, Koog para la orquestación y Supabase Auth como autoridad de identidad.

## Límites de seguridad

- Este módulo no se empaqueta en Android ni iOS.
- `OPENAI_API_KEY` nunca se lee desde `local.properties`.
- Ninguna ruta acepta un `userId` enviado por el cliente como identidad.
- El JWT llega en `Authorization: Bearer <token>` y se valida con Supabase.
- El asistente propondrá comandos, pero no escribirá directamente en las tablas
  financieras.
- Los logs no incluyen cuerpos, tokens, claves ni parámetros de consulta.

## Estructura actual

```text
services/assistant/
├── agent/
│   ├── memory/             Adaptador de checkpoints de Koog
│   └── tools/              Catálogo financiero de solo lectura
├── api/                    Contratos HTTP y errores públicos
├── application/            Composición, plugins y rutas
├── auth/                   Principal y puerto de validación
├── config/                 Variables de entorno y secretos
├── domain/memory/           Modelos y puertos de persistencia
└── infrastructure/
    ├── openai/             Frontera de construcción de Koog
    └── supabase/           JWT, memoria y snapshot financiero remoto
```

El módulo `:assistant-service` está registrado en `apps/mobile/settings.gradle.kts`
y consume `:shared`. El target `server` de `shared` permite que el futuro grafo
agéntico use exactamente los mismos `FinancialCommand` que las interfaces
manuales.

Versiones fijadas para esta base:

| Componente | Versión |
|---|---|
| JDK del servicio | 21 |
| Kotlin | 2.2.10 |
| Ktor del servicio | 3.5.1 |
| Koog estable | 1.0.0 |

El cliente móvil conserva su versión independiente de Ktor para no ampliar el
alcance de esta fase.

## Variables requeridas

| Variable | Descripción |
|---|---|
| `APP_ENV` | `local`, `test`, `staging` o `production`. |
| `PORT` | Puerto HTTP; por defecto `8080`. |
| `SERVICE_VERSION` | Versión desplegada; por defecto `dev`. |
| `SUPABASE_URL` | URL del proyecto de PocketMind. |
| `SUPABASE_PUBLISHABLE_KEY` | Clave pública usada para `/auth/v1/user`. |
| `SUPABASE_AUTH_TIMEOUT_MS` | Timeout entre `500` y `30000`; por defecto `5000`. |
| `OPENAI_API_KEY` | Secreto del servidor, nunca del APK. |
| `POCKETMIND_AGENT_MODEL` | Modelo principal configurable; inicialmente `gpt-4o-mini`. |
| `POCKETMIND_FALLBACK_MODEL` | Respaldo configurable; inicialmente `gpt-4o`. |
| `POCKETMIND_PROMPT_VERSION` | Versión auditable del prompt; actualmente `assistant-v2`. |
| `POCKETMIND_TOOL_SCHEMA_VERSION` | Versión positiva del catálogo de herramientas. |

`.env.example` documenta el contrato, pero el servicio deliberadamente no carga
archivos `.env`: las claves deben entrar por variables de proceso o por el
gestor de secretos del entorno de despliegue. Los `.env` reales están ignorados
por Git.

En `staging` y `production`, `SUPABASE_URL` debe usar HTTPS. Si falta una
variable, el proceso falla antes de abrir el puerto y solo informa el nombre de
la variable, no su valor.

## Validación local

Los comandos se ejecutan desde `apps/mobile`. La primera sincronización descarga
las dependencias nuevas.

```powershell
.\gradlew.bat :assistant-service:test
```

Para iniciar el servicio, configura las variables en la misma terminal. Este
ejemplo pide las claves sin mostrarlas en pantalla:

```powershell
$env:APP_ENV = "local"
$env:PORT = "8080"
$env:SERVICE_VERSION = "local"
$env:SUPABASE_URL = "https://TU_PROJECT_REF.supabase.co"
$env:SUPABASE_PUBLISHABLE_KEY = "TU_CLAVE_PUBLICA"

$openAiSecure = Read-Host "OPENAI_API_KEY" -AsSecureString
$openAiPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($openAiSecure)
try {
    $env:OPENAI_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($openAiPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($openAiPointer)
}

.\gradlew.bat :assistant-service:run
```

Comprobación pública:

```powershell
Invoke-RestMethod http://localhost:8080/health
```

Comprobación autenticada con un access token vigente de Supabase:

```powershell
$headers = @{ Authorization = "Bearer TU_ACCESS_TOKEN" }
Invoke-RestMethod http://localhost:8080/v1/session -Headers $headers
```

El endpoint autenticado debe devolver únicamente `userId` y `role`. No devuelve
correo ni metadatos del perfil.

## API de memoria

Todas estas rutas requieren el JWT de Supabase:

```text
POST   /v1/assistant/conversations
GET    /v1/assistant/conversations
GET    /v1/assistant/conversations/{conversationId}
DELETE /v1/assistant/conversations/{conversationId}
POST   /v1/assistant/drafts/{draftId}/confirm
POST   /v1/assistant/drafts/{draftId}/cancel
POST   /v1/assistant/drafts/{draftId}/complete
POST   /v1/assistant/turn
```

El servicio reenvía el JWT a la Data REST API de Supabase. No usa
`service_role`, y PostgreSQL vuelve a verificar `auth.uid()` mediante RLS.
`SupabaseKoogPersistenceStorageProvider` vincula cada instancia a un usuario y
una conversación concretos.

## Chat de texto

`POST /v1/assistant/turn` recibe texto autenticado, usa Koog con salida
estructurada y genera borradores para ingresos, gastos y transferencias. El
modelo solo dispone de herramientas financieras de lectura; las reglas del
servicio vuelven a validar cada propuesta antes de persistirla.

Android configura la URL pública del servicio mediante `ASSISTANT_BASE_URL` en
`apps/mobile/local.properties`. La clave de OpenAI permanece exclusivamente en
las variables de entorno del servicio.

Esta fase no confirma ni ejecuta el borrador. Consulta
[`docs/ASISTENTE_CHAT_TEXTO.md`](../../docs/ASISTENTE_CHAT_TEXTO.md) para el
contrato y los casos de validación.

## Despliegue

El servicio se despliega desde la raíz del monorepo mediante
`Dockerfile.vercel`. La imagen final usa Java 21, ejecuta un usuario sin
privilegios y recibe secretos exclusivamente por variables del entorno.

La guía completa está en
[`docs/DESPLIEGUE_ASISTENTE_VERCEL.md`](../../docs/DESPLIEGUE_ASISTENTE_VERCEL.md).

## Herramientas financieras

El registro creado por `AssistantReadToolRegistryFactory` queda ligado al JWT
del usuario e incluye:

```text
get_financial_overview
list_financial_products
get_financial_product
list_financial_transactions
```

El snapshot remoto conserva una versión estable para revalidar futuros
borradores y advierte que puede no incluir cambios que todavía estén en el
outbox de un dispositivo. Consulta
[`docs/ASISTENTE_HERRAMIENTAS_LECTURA.md`](../../docs/ASISTENTE_HERRAMIENTAS_LECTURA.md)
para los contratos y reglas completas.

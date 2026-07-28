# Despliegue del asistente en Vercel

## Objetivo

Publicar `assistant-service` como un contenedor JVM independiente y conectar la
aplicación Android mediante HTTPS. El servicio permanece en el monorepo porque
comparte `FinancialCommand` y otros contratos con Android e iOS.

## Arquitectura de despliegue

```mermaid
flowchart LR
    Android[Android] -->|HTTPS + JWT Supabase| Agent[Assistant Service en Vercel]
    Agent -->|API| OpenAI[OpenAI]
    Agent -->|JWT del usuario + RLS| Supabase[(Supabase)]
```

Vercel construye `Dockerfile.vercel` desde la raíz del repositorio. La etapa de
build usa Java 21 y genera la distribución de `:assistant-service`; la imagen
final contiene solamente el JRE y la aplicación.

`POCKETMIND_SERVER_ONLY=true` evita configurar los targets Android e iOS
durante la construcción del contenedor. La compilación móvil normal no define
esa variable y conserva todos sus targets.

## Archivos de despliegue

- `Dockerfile.vercel`: build multietapa y ejecución como usuario sin privilegios.
- `.dockerignore`: excluye secretos, propiedades locales, cachés y artefactos.
- `services/assistant/.env.example`: inventario sin valores reales.

No debe existir una clave real en ninguno de esos archivos.

## Crear el proyecto

1. En Vercel, seleccionar **Add New → Project**.
2. Importar el repositorio GitHub `Sebastian-jimenez30/PocketMind`.
3. Nombrar el proyecto `pocketmind-assistant`.
4. Mantener **Root Directory** en la raíz del repositorio.
5. Vercel detectará `Dockerfile.vercel`.
6. Configurar las variables antes del primer despliegue.

No se debe seleccionar `services/assistant` como Root Directory: Docker necesita
también `apps/mobile/shared`, el wrapper y el catálogo Gradle.

## Variables de entorno

Configurar en **Project → Settings → Environment Variables**:

| Variable | Valor | Protección |
|---|---|---|
| `APP_ENV` | `production` | normal |
| `SERVICE_VERSION` | versión desplegada, por ejemplo `assistant-v1` | normal |
| `SUPABASE_URL` | URL HTTPS del proyecto PocketMind | normal |
| `SUPABASE_PUBLISHABLE_KEY` | clave pública de Supabase | normal |
| `SUPABASE_AUTH_TIMEOUT_MS` | `5000` | normal |
| `OPENAI_API_KEY` | clave nueva y exclusiva del proyecto | **Sensitive** |
| `POCKETMIND_AGENT_MODEL` | `gpt-4o-mini` | normal |
| `POCKETMIND_FALLBACK_MODEL` | `gpt-4o` | normal |
| `POCKETMIND_PROMPT_VERSION` | `assistant-v3` | normal |
| `POCKETMIND_TOOL_SCHEMA_VERSION` | `1` | normal |

No configurar `PORT`: Vercel lo entrega al contenedor.

Las variables deben aplicarse a Production. Para previews, usar una clave
OpenAI diferente y, cuando sea posible, un proyecto Supabase de staging.

## Validación previa

El usuario ejecuta desde `apps/mobile`:

```powershell
.\gradlew.bat :assistant-service:test
.\gradlew.bat :shared:testAndroidHostTest
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:assembleDebug
```

La construcción equivalente al contenedor, sin Android SDK, es:

```powershell
$env:POCKETMIND_SERVER_ONLY = "true"
try {
    .\gradlew.bat :assistant-service:installDist --no-daemon
} finally {
    Remove-Item Env:POCKETMIND_SERVER_ONLY -ErrorAction SilentlyContinue
}
```

Si Docker Desktop está instalado, validar además desde la raíz:

```powershell
docker build -f Dockerfile.vercel -t pocketmind-assistant:local .
```

## Despliegue

Después de fusionar el PR, Vercel desplegará automáticamente `main`. También se
puede iniciar un despliegue desde el Dashboard. No se deben subir archivos
locales con secretos mediante la CLI.

Cada cambio en variables de entorno requiere un nuevo despliegue.

## Verificación remota

Reemplazar el dominio por el asignado a producción:

```powershell
$assistantUrl = "https://pocketmind-assistant.vercel.app"
Invoke-RestMethod "$assistantUrl/health"
```

La respuesta debe incluir:

```json
{
  "status": "ok",
  "service": "pocketmind-assistant"
}
```

La prueba autenticada requiere un access token vigente de Supabase:

```powershell
$headers = @{ Authorization = "Bearer ACCESS_TOKEN_TEMPORAL" }
Invoke-RestMethod "$assistantUrl/v1/session" -Headers $headers
```

No guardar el token en scripts, historial compartido ni documentación.

## Conectar Android

En `apps/mobile/local.properties`:

```properties
ASSISTANT_BASE_URL=https://DOMINIO_PRODUCCION
```

Después:

1. Ejecutar **Sync Project with Gradle Files**.
2. Instalar la aplicación en el teléfono.
3. Iniciar sesión.
4. Abrir **Inicio → Asistente**.
5. Enviar un gasto, ingreso o transferencia.
6. Verificar que aparezca una propuesta marcada **Aún no está guardado**.

## Diagnóstico

- Si `/health` falla, revisar Build Logs y Runtime Logs de Vercel.
- Si responde `401`, renovar la sesión Supabase del dispositivo.
- Si responde `ASSISTANT_UNAVAILABLE`, comprobar `OPENAI_API_KEY`, modelos y
  disponibilidad del proveedor.
- Si no encuentra productos, esperar la sincronización Room → Supabase y
  comprobar `finance_sync_records`.
- Si el build intenta solicitar Android SDK, verificar que
  `POCKETMIND_SERVER_ONLY=true` esté presente en la etapa build del Dockerfile.

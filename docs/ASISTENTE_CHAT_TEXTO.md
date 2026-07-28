# Chat de texto del asistente

## Objetivo

La primera capacidad interactiva de IA convierte lenguaje natural en una
propuesta financiera revisable. No escribe en Room ni en Supabase y no
presenta una propuesta como si ya estuviera registrada.

El mismo chat también admite saludos, preguntas sobre sus capacidades y
consultas de lectura sobre las finanzas del usuario. Estas respuestas usan el
estado `conversation` y nunca se confunden con una propuesta de escritura.

## Alcance actual

Se soportan tres intenciones:

| Intención | Datos mínimos |
|---|---|
| Ingreso | monto y producto líquido |
| Gasto | monto y producto líquido |
| Transferencia | monto, origen líquido y destino líquido |

La fecha es opcional y toma la hora del servicio cuando el usuario no la
indica. Comercio, nota y categoría también son opcionales. COP y USD son las
monedas admitidas por el dominio actual.

Cada turno recibe los productos activos y el historial reciente completo. Una
respuesta corta como `20000` debe completar los datos ya mencionados, no iniciar
una interpretación nueva. Una referencia parcial como `Bancolombia` se resuelve
automáticamente únicamente cuando existe un producto compatible inequívoco.
Las transferencias son movimientos entre dos productos propios; enviar dinero
a una persona o comercio desde una cuenta se interpreta como gasto.

Tarjetas de crédito, préstamos, ahorros, CDT, creación de productos, edición y
eliminación pertenecen a la fase de paridad completa.

## Flujo seguro

```mermaid
sequenceDiagram
    participant U as Usuario
    participant A as Android
    participant S as Servicio
    participant K as Koog/OpenAI
    participant DB as Supabase

    U->>A: Describe un movimiento
    A->>S: POST /v1/assistant/turn + JWT
    S->>DB: Recupera conversación y contexto con RLS
    S->>K: Historial + productos activos + herramientas de lectura
    K-->>S: Decisión estructurada
    S->>S: Revalida monto, moneda, fecha y productos
    S->>DB: Guarda mensaje y borrador proposed
    S-->>A: Respuesta + vista previa
    A-->>U: Tarjeta "Aún no está guardado"
```

El modelo nunca recibe una herramienta de escritura. La futura confirmación
revalidará el estado financiero y ejecutará el mismo `FinancialCommand` usado
por las pantallas manuales.

## Contrato HTTP

Ruta autenticada:

```text
POST /v1/assistant/turn
Authorization: Bearer <supabase_access_token>
Content-Type: application/json
```

Ejemplo:

```json
{
  "conversationId": null,
  "clientMessageId": "11111111-1111-4111-8111-111111111111",
  "content": "Gasté 35000 en almuerzo con Bancolombia",
  "locale": "es-CO",
  "timeZoneId": "America/Bogota"
}
```

`clientMessageId` debe reutilizarse cuando el cliente reintenta el mismo envío.
Una respuesta puede ser `conversation`, `clarification`, `proposal` o
`unsupported`.

## Configuración Android

En `apps/mobile/local.properties`:

```properties
ASSISTANT_BASE_URL=https://URL_PUBLICA_DEL_SERVICIO
```

La URL debe ser HTTPS y accesible desde el teléfono. `localhost` en un
dispositivo físico apunta al propio teléfono, no al computador. Para desarrollo
se necesita desplegar el servicio o exponerlo mediante un túnel HTTPS confiable.

Cambiar esta propiedad o cualquier archivo Gradle requiere **Sync Project with
Gradle Files** antes de ejecutar la app.

## Reglas de privacidad y operación

- La clave de OpenAI nunca se agrega a `local.properties`, BuildConfig ni al APK.
- El usuario se deriva del JWT; el body no acepta `userId`.
- Supabase vuelve a aplicar RLS en cada consulta.
- Se envían los nombres, tipos, monedas, identificadores y alias de los
  productos activos para resolver referencias en un solo turno. Movimientos,
  saldos detallados y demás registros solo se consultan mediante herramientas
  cuando son necesarios.
- Los logs no contienen mensajes, tokens ni payloads financieros.
- Los borradores expiran a los 15 minutos.
- Una ambigüedad de producto siempre genera una pregunta.

## Validación de esta fase

1. Gasto completo produce una propuesta y no un movimiento guardado.
2. Ingreso sin producto solicita el producto.
3. Transferencia ambigua solicita nombres más precisos.
4. Monedas distintas impiden una transferencia.
5. Un producto archivado, tarjeta, ahorro o préstamo no se usa como líquido.
6. Reintentar el mismo `clientMessageId` no duplica el turno ni el borrador.
7. JWT ausente o vencido devuelve error autenticado.
8. Tema claro y oscuro conservan contraste, teclado y desplazamiento.
9. `Mandé 20mil a mi novia desde Bancolombia` produce un gasto cuando existe
   un único producto líquido Bancolombia.
10. Una respuesta corta conserva intención, monto y producto de turnos previos.
11. `Hola` y `¿qué puedes hacer?` producen respuestas conversacionales.

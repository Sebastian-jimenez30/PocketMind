# Núcleo financiero manual

> Estado: base funcional previa a automatización, recordatorios y lectura de
> notificaciones.

Este documento define las reglas del producto manual de PocketMind. Es la
referencia para Android y para la futura implementación equivalente en iOS.

## Alcance

La persona puede:

- registrar varias cuentas bancarias, efectivo, préstamos, tarjetas de crédito
  y productos de ahorro;
- editar la información principal de cada producto;
- registrar ingresos, gastos y transferencias manuales;
- registrar compras de tarjeta a una o varias cuotas;
- registrar pagos de tarjeta y relacionarlos con el producto desde el que salen;
- registrar aportes y retiros en ahorros, y editar sus condiciones desde el
  producto;
- registrar pagos y abonos de préstamos con tasa, cuota mensual y día de pago;
- consultar el crecimiento diario estimado de ahorros con rendimiento E.A.;
- consultar un panorama general, el detalle por producto y el historial
  filtrado;
- consultar un análisis mensual básico por categoría.

La lectura de notificaciones, los recordatorios, voz, OCR y sincronización
financiera remota quedan fuera de este incremento. Se construirán sobre este
libro local sin cambiar sus reglas contables.

## Modelo

```mermaid
erDiagram
    ACCOUNT ||--o| CREDIT_CARD_PROFILE : especializa
    ACCOUNT ||--o| SAVINGS_PROFILE : especializa
    ACCOUNT ||--o| LOAN_PROFILE : especializa
    ACCOUNT ||--o{ FINANCIAL_TRANSACTION : origina
    ACCOUNT ||--o{ FINANCIAL_TRANSACTION : recibe
    ACCOUNT ||--o{ INSTALLMENT_PURCHASE : financia
    ACCOUNT ||--o{ CREDIT_CARD_PAYMENT : recibe
    ACCOUNT ||--o{ SAVINGS_MOVEMENT : registra
    ACCOUNT ||--o{ LOAN_PAYMENT : recibe

    CREDIT_CARD_PROFILE {
        string accountId
        long creditLimit
        int annualInterestBasisPoints
        int statementClosingDay
        int paymentDueDay
    }

    INSTALLMENT_PURCHASE {
        string id
        string accountId
        long principal
        int installmentCount
        int annualInterestBasisPoints
        long purchasedAt
        long firstPaymentAt
    }

    SAVINGS_PROFILE {
        string accountId
        string type
        int annualYieldBasisPoints
        long openedAt
        long maturityAt
    }

    LOAN_PROFILE {
        string accountId
        int annualInterestBasisPoints
        long monthlyPayment
        int paymentDueDay
        long openedAt
    }
```

`FinancialAccount` mantiene la identidad compartida. Los detalles que solo
aplican a una tarjeta, ahorro o préstamo viven en perfiles especializados. Las
compras, pagos, aportes, retiros y abonos conservan historial; no se reemplazan por un saldo
editable sin origen.

## Reglas contables

### Cuentas y efectivo

```text
saldo = saldo inicial
      + ingresos
      - gastos
      + transferencias recibidas
      - transferencias enviadas
```

### Panorama

```text
activos = efectivo + cuentas bancarias + ahorros proyectados
pasivos = tarjetas de crédito + préstamos
patrimonio estimado = activos - pasivos
```

El ingreso y gasto mensual excluye transferencias, aportes a ahorro y pagos de
deuda para evitar duplicar flujos internos.

### Tarjetas

- La deuda inicial es el saldo registrado al crear la tarjeta.
- Cada compra conserva principal, cantidad de cuotas y una copia de la tasa
  vigente para mantener su cálculo histórico.
- La persona no escribe la tasa ni la primera cuota en cada compra. PocketMind
  toma la tasa, día de corte y día de pago del perfil de la tarjeta y calcula
  automáticamente la primera fecha de pago según la fecha de compra.
- Si cambian las condiciones de la tarjeta, se actualizan una sola vez desde
  Editar producto y aplican a las compras posteriores.
- Para tasa cero, la cuota es `principal / cuotas`.
- Con tasa, se usa una cuota fija calculada con la tasa mensual equivalente de
  la tasa efectiva anual.
- La deuda actual es deuda inicial más valores financiados menos pagos.
- Los pagos se distribuyen cronológicamente entre cuotas pendientes. Cuando
  todas las cuotas de una compra quedan cubiertas, se muestra como pagada.
- El cupo disponible nunca puede quedar por debajo de cero y una compra que
  supera el cupo se rechaza.

Los valores son estimaciones de organización personal. El extracto oficial del
banco prevalece porque pueden existir seguros, impuestos, avances o reglas
contractuales no registradas.

### Ahorros

- `SIMPLE`: saldo sin necesidad de rendimiento.
- `POCKET`: ahorro flexible con tasa modificable.
- `TERM_DEPOSIT`: ahorro con tasa y vencimiento.
- Aportes y retiros son eventos inmutables.
- Un cambio de tasa aplica desde su fecha y no altera el rendimiento histórico.
- La proyección usa capitalización diaria equivalente a la tasa efectiva anual.
- Un retiro mayor al saldo proyectado se rechaza.

El rendimiento mostrado es estimado; no sustituye el saldo certificado por la
entidad financiera.

### Transferencias

Una transferencia es un único movimiento con cuenta de origen y destino. Reduce
una cuenta y aumenta la otra, pero no se suma como ingreso ni gasto. Pagos de
tarjeta y aportes de ahorro usan esta regla cuando la persona selecciona una
cuenta relacionada.

### Préstamos

Un préstamo conserva la deuda inicial, tasa efectiva anual, cuota mensual, día
de pago y fecha de apertura. PocketMind aplica la tasa E.A. de forma equivalente
al tiempo transcurrido, descuenta cada pago o abono en su fecha y muestra deuda,
interés estimado y próximo pago. Es una estimación organizativa; el extracto de
la entidad sigue siendo la fuente contractual.

## Experiencia

- Inicio muestra primero patrimonio, ingresos y gastos del mes.
- El carrusel contiene panorama general y un resumen por producto.
- Cada producto abre su detalle.
- Las acciones rápidas de gasto, ingreso, productos e historial navegan a una
  experiencia real.
- El registro global permite elegir ingreso, gasto, compra o pago de tarjeta,
  aporte o retiro de ahorro y pago o abono de préstamo.
- Todas esas operaciones comparten una única pantalla dinámica que muestra solo
  el formulario correspondiente a la opción seleccionada.
- La barra inferior abre siempre la raíz de Inicio, Movimientos, Análisis o
  Perfil; no conserva un formulario interno al volver a pulsar su destino.
- Los filtros de Movimientos se abren desde el icono dentro de la búsqueda.
- Formularios usan etiquetas persistentes, ejemplos, validación recuperable y
  una acción primaria.
- Toda pantalla con escritura usa `adjustResize`, contenido desplazable e
  `imePadding`; el teclado no debe ocultar el campo activo ni el botón de
  guardado.

## Arquitectura

- Los modelos y cálculos puros viven en `shared/commonMain` para ser reutilizados
  por Android e iOS.
- Room es la fuente local de verdad de la interfaz Android.
- Los ViewModels exponen `StateFlow` inmutable y coordinan repositorios/casos de
  uso.
- Las pantallas Compose reciben estado y eventos; no ejecutan cálculos
  financieros ni consultas.
- La migración Room `2 → 3` conserva datos previos y convierte ahorros y deudas
  del onboarding en productos visibles.
- La migración Room `3 → 4` incorpora perfiles y pagos de préstamo sin eliminar
  los productos existentes.
- Las futuras notificaciones crearán candidatos revisables que, después de la
  confirmación, escribirán en los mismos contratos del dominio.

## Pruebas mínimas

| Componente | Casos |
|---|---|
| Cuotas | tasa cero; tasa positiva; varias compras; compra finalizada |
| Tarjeta | deuda inicial; cupo; pago parcial; pago total; exceso de cupo |
| Ahorro | sin tasa; rendimiento anual; progreso diario; aporte; retiro; cambio de tasa |
| Préstamo | tasa; interés acumulado; cuota; abono parcial; pago total |
| Transferencia | origen disminuye; destino aumenta; no altera ingresos/gastos |
| Room | migraciones 2→3 y 3→4; claves foráneas; datos del onboarding conservados |
| UI | vacío, contenido, error, texto ampliado, oscuro y teclado abierto |

## Límites conocidos antes de automatización

- Los cálculos dependen de los datos ingresados por la persona.
- No se concilia todavía contra extractos bancarios.
- No hay recordatorios ni generación automática de cuotas mensuales como
  movimientos; el calendario es una proyección visible.
- Los productos financieros siguen almacenados localmente. La sincronización con
  Supabase debe conservar IDs, historial y prioridad de edición manual.

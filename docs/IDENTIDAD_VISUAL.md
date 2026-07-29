# PocketMind — Identidad visual y experiencia de usuario

> **Estado:** identidad v1 aprobada para el desarrollo inicial
>
> **Alcance:** Android, iOS, prototipos, documentación y material de producto
>
> **Fuente de verdad:** este documento
>
> **Skill obligatoria:** `.agents/skills/pocketmind-design-system/SKILL.md`

Este documento define cómo debe verse, sentirse y comportarse PocketMind. Toda
pantalla nueva y toda modificación visual deben respetarlo. Si una decisión de
diseño cambia, este archivo y los tokens de las plataformas se actualizan en el
mismo pull request.

## 1. Dirección de producto

PocketMind no es un banco ni pretende reemplazar sus aplicaciones. Es una capa
personal, clara y automática sobre las finanzas dispersas del usuario.

La experiencia debe transmitir:

- **Control sin esfuerzo:** la aplicación organiza antes de pedir trabajo manual.
- **Claridad:** los datos importantes se entienden en pocos segundos.
- **Confianza:** los estados, fuentes y automatizaciones son explicables.
- **Cercanía:** el lenguaje acompaña sin juzgar hábitos financieros.
- **Progreso:** cada pantalla ayuda a tomar una siguiente acción útil.

La referencia inicial es el UI Kit iBank/FinPay:

- [Prototipo interactivo](https://www.figma.com/proto/13H5se1o8veb3N0ctQfmVn/iBank---Banking---E-Money-Management-App-%7C-FinPay-%7C-Digital-%7C-Finance-Mobile-Banking-App-Ui-Kit--Community-?node-id=54-21278&p=f&t=HwVqhh8DMzEA8lW0-1&scaling=min-zoom&content-scaling=fixed&page-id=2%3A20347)
- [Archivo de diseño](https://www.figma.com/design/CRXJ0cpjcZNiWvv1sLtPP0/iBank---Banking---E-Money-Management-App-%7C-FinPay-%7C-Digital-%7C-Finance-Mobile-Banking-App-Ui-Kit--Community-?node-id=370-31570&p=f&t=ieyNcFeFdrLbUujk-0)

Se adopta su jerarquía clara, composición modular, cabecera de marca, superficies
redondeadas, accesos rápidos y navegación simple. No se copian su marca,
ilustraciones, recursos, textos ni metáforas bancarias.

## 2. Principios obligatorios de experiencia

### 2.1 Primero entender, después profundizar

La primera vista de cada pantalla presenta el resumen y la acción principal.
Detalles, filtros y configuración avanzada aparecen bajo demanda.

### 2.2 Automatización visible y reversible

Todo movimiento automático indica su origen. El usuario puede revisar, corregir
o deshacer una clasificación sin perder control.

### 2.3 Una acción primaria por contexto

Cada pantalla tiene una acción principal inequívoca. Las acciones secundarias no
compiten mediante el mismo peso, color o tamaño.

### 2.4 Estados completos

Toda experiencia contempla carga o skeleton, contenido, vacío con orientación,
error con recuperación, funcionamiento sin conexión, éxito o confirmación y
permisos rechazados o revocados cuando corresponda.

### 2.5 Finanzas sin juicio

No se usan mensajes alarmistas, culpabilizadores o que aparenten asesoría
profesional. Se describen hechos, tendencias y opciones.

### 2.6 Privacidad perceptible

Las cifras pueden ocultarse. Los permisos explican propósito, alcance y forma de
revocación antes de abrir una pantalla del sistema.

## 3. Personalidad de marca

PocketMind es:

- inteligente, pero no técnica;
- moderna, pero no experimental;
- amigable, pero no infantil;
- confiable, pero no institucional;
- motivadora, pero no invasiva.

Palabras guía: **calma, claridad, orden, acompañamiento, autonomía y progreso**.

Evitar una apariencia de banco tradicional, plataforma de trading, hoja
contable, aplicación de apuestas o asistente que promete resultados financieros.

## 4. Sistema de color

Los nombres de token son semánticos. Las pantallas nunca consumen hexadecimales
directamente.

### 4.1 Paleta de marca

| Token conceptual | Claro | Uso |
|---|---:|---|
| `brandPrimary` | `#4338CA` | cabeceras, CTA, navegación activa |
| `brandPrimaryStrong` | `#312E81` | énfasis, gradientes y estados presionados |
| `brandPrimarySoft` | `#EEF2FF` | fondos seleccionados y destacados |
| `brandSecondary` | `#0F9F8F` | automatización, progreso y ayudas |
| `brandSecondarySoft` | `#CCFBF1` | fondos informativos secundarios |
| `brandHighlight` | `#F59E0B` | recordatorios o atención no crítica |

El índigo sustituye la metáfora verde bancaria actual. El verde azulado queda
como apoyo para automatización y progreso, no como color primario general.

### 4.2 Neutros claros

| Token conceptual | Valor | Uso |
|---|---:|---|
| `background` | `#F8F8FC` | fondo general |
| `surface` | `#FFFFFF` | tarjetas y formularios |
| `surfaceVariant` | `#F1F0F7` | agrupaciones y controles discretos |
| `textPrimary` | `#1A1A2B` | contenido principal |
| `textSecondary` | `#666577` | descripciones |
| `textMuted` | `#737284` | metadatos no críticos |
| `outline` | `#D8D7E1` | bordes y divisores |

### 4.3 Semántica financiera

| Token conceptual | Claro | Significado |
|---|---:|---|
| `income` | `#16866A` | ingresos y entradas confirmadas |
| `expense` | `#C93C54` | gastos y salidas confirmadas |
| `warning` | `#A96800` | revisión o atención |
| `info` | `#1677B8` | información neutral |
| `success` | `#16866A` | operación completada |
| `error` | `#BA1A1A` | fallos y acciones destructivas |

Ingresos y gastos nunca se distinguen solo por color: incluir signo, etiqueta,
icono o texto.

### 4.4 Tema oscuro

| Token conceptual | Oscuro |
|---|---:|
| `background` | `#10111D` |
| `surface` | `#191A29` |
| `surfaceVariant` | `#242538` |
| `brandPrimary` | `#A5B4FC` |
| `brandPrimaryStrong` | `#C7D2FE` |
| `brandPrimarySoft` | `#28264D` |
| `brandSecondary` | `#5EEAD4` |
| `textPrimary` | `#F5F4FA` |
| `textSecondary` | `#C2C0CD` |
| `textMuted` | `#9896A6` |
| `outline` | `#3C3C4E` |
| `income` | `#5ED6B3` |
| `expense` | `#FF8FA3` |
| `warning` | `#F7C66A` |
| `info` | `#75C7F0` |
| `error` | `#FFB4AB` |

Todos los pares de texto y fondo deben validarse al menos con WCAG AA. No se
aprueba un color por semejanza visual sin verificar contraste.

## 5. Tipografía

La familia principal es **Poppins**, con licencia incorporada al proyecto y sin
dependencia de descarga en tiempo de ejecución.

Fallbacks:

- Android: sans serif del sistema.
- iOS: San Francisco.

| Rol | Tamaño | Peso | Interlineado | Uso |
|---|---:|---:|---:|---|
| `display` | 32 | 600 | 40 | cifras protagonistas puntuales |
| `headlineLarge` | 28 | 600 | 36 | títulos de bienvenida |
| `headline` | 24 | 600 | 32 | títulos principales |
| `titleLarge` | 20 | 600 | 28 | cabeceras y tarjetas |
| `title` | 16 | 600 | 24 | secciones |
| `bodyLarge` | 16 | 400 | 24 | contenido destacado |
| `body` | 14 | 400 | 21 | contenido general |
| `label` | 12 | 500 | 16 | metadatos y controles |

Respetar el escalado de fuente de Android y Dynamic Type de iOS. Evitar textos
esenciales en 12 sp/pt, no usar más de tres pesos en una pantalla y no convertir
texto en imágenes.

## 6. Geometría, espaciado y elevación

La unidad base es 4. La secuencia permitida es:

`4, 8, 12, 16, 20, 24, 32, 40, 48, 64`.

| Elemento | Valor objetivo |
|---|---:|
| margen horizontal compacto | 24 dp/pt |
| separación de secciones | 24–32 dp/pt |
| separación interna | 12–16 dp/pt |
| radio de campo o botón | 14–16 dp/pt |
| radio de tarjeta | 16–20 dp/pt |
| radio de superficie principal | 28–32 dp/pt |
| altura mínima de CTA | 52 dp Android / 50 pt iOS |
| objetivo táctil | 48 dp Android / 44 pt iOS |

Usar elevación baja y sombras suaves. La jerarquía depende primero de color,
espacio y agrupación; no de sombras intensas.

## 7. Iconografía e ilustración

### Iconos

- Utilizar trazos redondeados y peso visual coherente.
- Android parte de Material Symbols Rounded.
- iOS parte de SF Symbols.
- Adaptar el símbolo por plataforma cuando mejore familiaridad.
- Mantener tamaños visuales de 20, 24 o 28.
- Acompañar iconos ambiguos con etiqueta.

### Ilustraciones

Autenticación, onboarding y estados vacíos pueden usar ilustraciones abstractas,
simples y optimistas: formas geométricas suaves, pocos colores de la paleta y
metáforas de orden, detección, conversación y progreso.

No reutilizar recursos visuales del UI Kit de referencia. Los activos deben ser
propios, generados para PocketMind o contar con licencia comprobada.

### Logotipo

El distintivo de PocketMind representa una billetera con una tendencia ascendente:
organización cotidiana y progreso financiero, sin adoptar la apariencia de una
tarjeta bancaria. Los originales de las variantes aprobadas se conservan en
`public/`.

- Modo claro: `PocketMind1` — billetera índigo y tendencia turquesa.
- Modo oscuro: `PocketMind4` — billetera lavanda y tendencia turquesa.

Android empaqueta equivalentes optimizados en `res/drawable-nodpi/` y selecciona
la variante desde `PocketBrandMark` según el tema activo. Ambos recursos deben
conservar transparencia y mostrarse sin cápsula, recorte ni fondo añadido. No
sustituirlos por capturas, recursos remotos ni versiones de terceros.

## 8. Estructura general de pantalla

El shell visual preferido combina:

1. área superior de marca en índigo;
2. saludo, título o navegación contextual;
3. superficie principal clara con esquinas superiores amplias;
4. contenido modular mediante tarjetas;
5. navegación inferior estable en toda pantalla autenticada.

La cabecera puede reducirse al desplazarse. Debe respetar safe areas, barras del
sistema y teclado.

Las cabeceras contextuales usan una sola línea, títulos preferentemente de una
palabra y una fila visual de 48 dp en Android o 44 pt en iOS, además del área
segura. La propia cabecera pinta detrás de la barra de estado: el contenedor raíz
no vuelve a consumir ese inset ni deja una franja independiente. No se reserva
una zona superior amplia para descripciones que ya aparecen dentro del contenido.

Autenticación y onboarding no muestran navegación inferior. Inicio,
Movimientos, Análisis, Perfil y sus flujos secundarios autenticados conservan la
barra para permitir cambios de contexto sin reconstruir el historial.

### Navegación principal

La navegación inferior estable contiene cuatro destinos:

1. **Inicio**
2. **Movimientos**
3. **Análisis**
4. **Perfil**

El destino activo usa icono, etiqueta y una cápsula de color. El botón Atrás no
duplica destinos ni regresa a contenido autenticado después de cerrar sesión.
Al pulsar un destino se abre siempre su pantalla raíz, incluso si la persona se
encuentra dentro de un formulario o detalle perteneciente a ese mismo destino.
La barra no usa divisor, sombra ni franja de color superior.

La captura se trata como acción, no como destino. Desde Inicio abre registro
manual, voz, fotografía y opciones automáticas disponibles.

## 9. Componentes base

Los componentes deben existir en el design system y no reimplementarse por
pantalla.

### `PocketTopHeader`

Cabecera de marca o contextual. Soporta saludo, título, avatar, notificaciones,
navegación atrás y privacidad de cifras.

### `PocketContentSheet`

Superficie que se superpone visualmente a la cabecera, con radio superior de
28–32 y fondo `background` o `surface`.

### `FinancialSummaryCard`

En Inicio es la primera página de un carrusel horizontal de panorama y productos.
La primera página resume el total; las siguientes muestran cada producto con saldo,
ingresos y gastos. Son superficies informativas de PocketMind, no tarjetas
bancarias simuladas.

Elemento protagonista del inicio. Presenta balance del periodo, ingresos,
gastos y comparación. No imita una tarjeta Visa ni presupone un único banco.

### `QuickActionTile`

Acceso rápido con icono, etiqueta corta y estado disponible, próximo o
deshabilitado explicado. Se organiza en 2 o 3 columnas según ancho y legibilidad.

### `FinancialStat`

Etiqueta, valor, periodo y tendencia. El formato monetario usa configuración
regional y moneda del usuario.

### `TransactionRow`

Incluye categoría, comercio o concepto, fecha, monto, tipo, sincronización y
origen relevante: automático, manual, hablado o escaneado.

### `PocketButton`

Variantes primary, secondary, tonal, text y destructive. Incluye estados
enabled, pressed, loading y disabled. Nunca coloca dos acciones primary juntas.

### `PocketTextField`

Los formularios iniciales incluyen ejemplos breves en el placeholder, como
“Cuenta Bancolombia” o “850000”, para reducir ambigüedad sin reemplazar la
etiqueta persistente.

Los menús desplegables usan campo y superficie con radio de 16 dp/pt. No se
permiten popups cuadrados ni cuadrículas para opciones de longitud variable.

### `PocketChoiceChip`

Selector compacto para dos o tres opciones breves y simétricas. El texto no cambia al seleccionar: se
mantiene estable y se diferencia con borde índigo de 2 dp y fondo translúcido
`primaryContainer`. Las colecciones extensas o de longitud variable se muestran
en un menú de campo. Las categorías de movimientos son la excepción: usan un
carrusel horizontal de opciones con icono, etiqueta estable, borde, fondo y
marca de selección, porque deben reconocerse con rapidez sin desplegar una lista.

Incluye label persistente, valor, helper/error, leading/trailing action y
accesibilidad. El placeholder no reemplaza la etiqueta.

### `PocketBottomNavigation`

Cuatro destinos, cápsula activa y respeto del área segura.

### `PocketMessage`

Variantes inline, banner, snackbar y estado de página. Explica qué ocurrió y la
siguiente acción.

### `PrivacyAmount`

Valor monetario que puede ocultarse sin alterar el layout.

## 10. Patrones por flujo

### 10.1 Autenticación

- antes de resolver la sesión se muestra un estado de arranque con únicamente
  el logotipo de PocketMind sobre el fondo de la aplicación;
- este estado no tiene duración artificial: permanece hasta conocer la sesión,
  completar la sincronización inicial y resolver si corresponde Inicio,
  Onboarding o Login;
- Login y Onboarding nunca se usan como pantallas de carga ni deben aparecer
  fugazmente durante la restauración de una sesión existente;
- cabecera índigo con navegación contextual;
- superficie blanca redondeada;
- bienvenida breve e ilustración propia;
- formulario compacto;
- CTA de ancho completo;
- recuperación visible;
- alternativa de Google separada;
- cambio entre registro e inicio al final.

La biometría se muestra únicamente si está configurada y disponible.

### 10.2 Inicio

Orden recomendado:

1. saludo, avatar y notificaciones;
2. carrusel de panorama total seguido por productos;
3. captura rápida;
4. cuadrícula de acciones;
5. últimos movimientos;
6. insight breve y explicable.

No mostrar simultáneamente todas las capacidades futuras.

### 10.3 Movimientos

- búsqueda compacta con icono de filtros dentro del campo;
- filtros por periodo, tipo, categoría, producto y origen en una hoja bajo demanda;
- lista agrupada por fecha;
- totales coherentes con los filtros;
- edición y corrección accesibles;
- estado automático identificable sin ruido visual.

El registro manual es una sola pantalla dinámica. Primero agrupa la intención en
Gasto, Ingreso o Transferencia y después permite elegir el tipo específico. Las
categorías aparecen como un carrusel horizontal identificable mediante icono,
texto y estado seleccionado. El producto y los campos siguientes cambian dentro
de la misma pantalla y solo presentan los datos necesarios para la elección.
Cambiar la opción no abre un segundo formulario ni conserva campos irrelevantes.
El comercio no es obligatorio ni tiene campo propio; una descripción opcional
recoge cualquier contexto adicional. Las tasas, fechas de corte y fechas de pago
pertenecen al producto y nunca se solicitan nuevamente al registrar una compra o
un pago.

### 10.4 Análisis

- pregunta o conclusión como encabezado;
- cifra principal y gráfica sencilla;
- desglose accionable y comparación temporal;
- explicación en lenguaje natural.

Las gráficas tienen etiquetas y alternativa textual. No usar visualizaciones 3D
ni depender exclusivamente de leyendas por color.

### 10.5 Perfil y preferencias

La portada muestra avatar, nombre y correo. Las preferencias se agrupan en datos
personales, preferencias financieras, productos, automatización,
seguridad, privacidad, apariencia e idioma y cierre de sesión.

Evitar un formulario único excesivamente largo.

### 10.6 Permiso de notificaciones en Android

Explicar qué se leerá, para qué se usará, qué no se almacena, cómo revisar
registros y cómo revocar el acceso. La negativa no bloquea el registro manual.
iOS no presenta este flujo.

### 10.7 Confirmaciones y acciones sensibles

Cambios de contraseña, eliminación, desvinculación y operaciones irreversibles
usan confirmación explícita. La biometría invoca la experiencia nativa.

### 10.8 Asistente

- La cabecera contextual usa `PocketContextTopBar`: una fila accesible de
  48 dp en Android o 44 pt en iOS, además del área segura, con título de una
  línea y sin doble inset superior.
- El mensaje del usuario aparece y el compositor se limpia inmediatamente al
  enviar; la respuesta remota no bloquea ese feedback.
- El progreso se expresa con una línea pequeña como “Analizando…”, nunca con
  una tarjeta de estado que desplace la conversación.
- La introducción desaparece después del primer mensaje.
- Un envío fallido conserva el mensaje y muestra junto a él un icono de
  advertencia, una explicación breve y la acción de reintentar.
- El compositor queda unido visualmente al teclado. Mientras el teclado está
  visible se oculta la navegación inferior para no crear una franja vacía.
- El compositor no muestra un texto de ejemplo permanente: inicia en una sola
  línea compacta y crece con el contenido hasta un máximo de tres líneas.
- Editar una propuesta ocurre dentro de su propia tarjeta mediante campos
  deterministas; no convierte la corrección en otro prompt para el modelo.
- Cancelar retira de inmediato la propuesta visible y nunca presenta un estado
  de guardado.

## 11. Voz y contenido

El idioma inicial es español claro y cercano.

Preferir:

- “Registramos este gasto desde una notificación de Bancolombia.”
- “No pudimos identificar la categoría. Puedes elegirla ahora.”
- “Este mes gastaste más en restaurantes que el mes pasado.”
- “Revisa estos datos antes de guardar.”

Evitar:

- “Mala decisión financiera.”
- “Estás gastando demasiado.”
- “Debes invertir este dinero.”
- “La IA sabe lo que necesitas.”
- mensajes como “Error 400” sin recuperación.

Los botones comienzan con verbos: “Guardar movimiento”, “Revisar”, “Intentar de
nuevo”, “Cerrar sesión”.

## 12. Accesibilidad y adaptación

Requisitos:

- contraste WCAG AA;
- navegación completa con TalkBack y VoiceOver;
- orden de foco coherente;
- etiquetas accesibles;
- estados no comunicados solo por color;
- escalado de fuente, modo oscuro y reducción de movimiento;
- layouts compactos, medianos y expandidos;
- contenido usable con teclado abierto;
- el campo que recibe foco se desplaza a una zona visible después de abrirse el
  teclado;
- orientación y tamaños soportados.

### 12.1 Teclado en Android

- `MainActivity` conserva `android:windowSoftInputMode="adjustResize"`.
- El `Scaffold` raíz consume únicamente las barras del sistema; nunca usa
  `safeDrawing` para el borde inferior porque ese inset también incluye el IME
  y duplicaría el espacio aplicado por la pantalla.
- El chat aplica `imePadding()` a su contenedor, oculta temporalmente la
  navegación inferior y vuelve a desplazar la lista cuando cambia el inset del
  IME para mantener visible el último mensaje.
- Todo formulario usa un contenedor desplazable con `verticalScroll()` o
  `LazyColumn`, más `imePadding()`.
- Cada campo editable aplica `pocketBringIntoViewOnFocus()`. Este componente
  vuelve a solicitar visibilidad cuando cambia la altura real del teclado, no
  solamente cuando recibe el foco.
- Los campos de menú de solo lectura no abren el teclado y no necesitan este
  comportamiento.

Las cantidades se formatean por locale, inicialmente `es-CO`, y nunca se
construyen concatenando símbolos manualmente.

## 13. Movimiento y respuesta

| Tipo | Duración orientativa |
|---|---:|
| respuesta de control | 100–150 ms |
| transición local | 200–250 ms |
| cambio de pantalla o expansión | 300–400 ms |

Usar easing nativo, respetar reducción de movimiento y ofrecer feedback
inmediato en toda acción asíncrona.

## 14. Adaptación Android e iOS

La identidad y jerarquía son compartidas; la implementación es nativa.

### Android

- Jetpack Compose y Material 3.
- Tokens centralizados en el tema.
- Navigation Compose con back stack explícito.
- Material Symbols Rounded cuando corresponda.
- Targets mínimos de 48 dp.

### iOS

- SwiftUI y componentes del sistema.
- Tokens equivalentes.
- NavigationStack y presentación nativa.
- SF Symbols y patrones familiares de iOS.
- Targets mínimos de 44 pt.

No se fuerza paridad pixel a pixel. Se exige paridad de intención, jerarquía,
marca, contenido y capacidades disponibles.

## 15. Reglas técnicas

- No hardcodear colores, tipografía, radios, espaciados ni textos en pantallas.
- Centralizar tokens y componentes reutilizables.
- Mantener estado y eventos fuera de componentes puramente visuales.
- Crear previews de estados clave.
- Probar carga, vacío, error, contenido y tamaños de fuente.
- No incorporar assets remotos efímeros de Figma.
- No añadir una librería para reproducir un componente sencillo.
- Documentar desviaciones temporales y su deuda.

Ubicaciones objetivo:

```text
Android:
  ui/theme/        color, typography, shape, spacing
  ui/components/   componentes PocketMind

iOS:
  DesignSystem/Tokens/
  DesignSystem/Components/

Figma:
  Foundations
  Components
  Patterns
  Screens
```

## 16. Antipatrones

No aprobar:

- dashboard que simule una única tarjeta bancaria;
- pantalla inicial saturada con funcionalidades futuras;
- formularios sin labels persistentes;
- íconos ambiguos sin etiqueta;
- sombras fuertes o gradientes excesivos;
- rojo o verde como única señal;
- textos de bajo contraste;
- diálogos para información que cabe inline;
- acciones destructivas con apariencia primaria normal;
- UI copiada literalmente del kit;
- convenciones diferentes entre plataformas sin justificación.

## 17. Checklist de revisión

- [ ] La pantalla expresa una acción primaria.
- [ ] Usa tokens y componentes del sistema.
- [ ] Respeta jerarquía, espaciado y radios.
- [ ] Funciona en tema claro y oscuro.
- [ ] Incluye carga, vacío, error y recuperación cuando aplican.
- [ ] Tiene contraste, targets y etiquetas accesibles.
- [ ] Soporta texto ampliado sin cortar contenido esencial.
- [ ] Usa lenguaje cercano, preciso y no juzga.
- [ ] Protege cifras y permisos sensibles.
- [ ] Mantiene la intención entre Android e iOS.
- [ ] No copia recursos del UI Kit.
- [ ] Cuenta con previews, pruebas o evidencia visual proporcional.

## 18. Gobierno de la identidad

Este documento es normativo. Los prototipos y el código lo implementan; no lo
reemplazan.

Todo cambio de color, tipografía, navegación, componente base, estructura de una
pantalla principal, voz o accesibilidad actualiza este documento en el mismo PR.
Si Figma y código difieren, se documenta la decisión y se corrige la fuente
desactualizada.

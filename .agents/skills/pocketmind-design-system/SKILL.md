---
name: pocketmind-design-system
description: "Preserva y aplica la identidad visual, experiencia, componentes, navegación, accesibilidad y voz de PocketMind. Usar en cualquier tarea que cree, modifique, revise o documente UI/UX de PocketMind en Jetpack Compose, SwiftUI o Figma; también al trabajar con temas, colores, tipografía, iconos, ilustraciones, pantallas, flujos, previews, textos de interfaz o pruebas visuales."
---

# PocketMind Design System

## Fuente de verdad

Leer completamente
[`docs/IDENTIDAD_VISUAL.md`](../../../docs/IDENTIDAD_VISUAL.md) antes de tomar
decisiones o editar archivos. Tratar ese documento como norma vigente.

No duplicar sus tokens o especificaciones dentro de esta skill. Si una decisión
cambia, actualizar la guía y la implementación en el mismo cambio.

## Flujo obligatorio

1. Identificar la plataforma, flujo y estado de UI solicitados.
2. Leer la guía visual completa y revisar los tokens y componentes existentes.
3. Separar intención compartida de convenciones nativas Android/iOS.
4. Reutilizar componentes; crear uno nuevo solo si representa un patrón repetible.
5. Implementar carga, contenido, vacío, error, offline, permisos y éxito según
   corresponda.
6. Verificar accesibilidad, privacidad, texto ampliado, tema oscuro y adaptación.
7. Añadir previews, pruebas o evidencia visual proporcional al cambio.
8. Informar desviaciones y actualizar la guía si representan una decisión
   aprobada.

## Reglas no negociables

- Comunicar automatización, control y bienestar financiero; no aparentar ser un
  banco, una plataforma de trading o una hoja contable.
- Inspirarse en la estructura del UI Kit sin copiar marca, assets,
  ilustraciones, textos ni pantallas literalmente.
- Usar tokens semánticos; no hardcodear valores visuales en pantallas.
- Mantener una acción primaria por contexto y navegación inferior estable.
- No usar una tarjeta bancaria como resumen principal de PocketMind.
- No distinguir ingresos, gastos o errores únicamente mediante color.
- Explicar permisos y automatizaciones antes de invocar APIs del sistema.
- Preservar la intención entre Compose y SwiftUI sin exigir paridad pixel a pixel.
- Usar lenguaje claro, cercano, no juzgador y sin promesas financieras.

## Decisiones por tipo de tarea

### Nueva pantalla

Partir del patrón de la guía, definir jerarquía y estados, y mapear cada elemento
a un componente existente antes de crear código.

### Modificación de pantalla existente

Acercarla progresivamente al sistema sin ampliar el alcance innecesariamente. No
introducir una segunda convención visual temporal.

### Figma

Trabajar en un archivo propio de PocketMind. Usar el kit externo solo como
referencia. Nombrar páginas `Foundations`, `Components`, `Patterns` y `Screens`.

### Revisión

Aplicar el checklist de la guía y reportar incumplimientos con archivo, pantalla,
impacto y corrección concreta.

## Criterio de finalización

No considerar terminada una experiencia hasta que sea coherente con la guía,
usable con tecnologías de asistencia y verificable en los estados y tamaños
relevantes.

# Documentación de PocketMind

Este directorio es la fuente de contexto técnico y de producto del proyecto.
Toda decisión que modifique el alcance, la arquitectura o las reglas descritas
aquí debe actualizarse en el mismo cambio que la implemente.

## Documentos

- [Plan de implementación](PLAN_IMPLEMENTACION.md): visión, alcance, reglas
  obligatorias, arquitectura objetivo, fases y estrategia de calidad.
- [Especificación de producto](ESPECIFICACION_PRODUCTO.md): oportunidad,
  requisitos, casos de uso, historias de usuario, arquitectura e inventario de
  pruebas.
- [Checklist de inicio](INICIO_DESARROLLO.md): decisiones y prerequisitos para
  comenzar el primer incremento Android/iOS.
- [Identidad visual y UX](IDENTIDAD_VISUAL.md): principios de marca, tokens,
  componentes, patrones de pantalla, accesibilidad y criterios de revisión.
- [Núcleo financiero manual](NUCLEO_FINANCIERO_MANUAL.md): modelo de cuentas,
  tarjetas, cuotas, ahorros, transferencias, cálculos y reglas previas a la
  automatización.
- [Asistente de IA](ASISTENTE_IA_GUIA_IMPLEMENTACION.md): decisiones de
  arquitectura, comandos compartidos con la operación manual, Koog, modelos
  GPT-4, seguridad, memoria, fases y estrategia de pruebas.
- [Memoria del asistente](ASISTENTE_MEMORIA.md): esquema Supabase, RLS,
  ciclo de borradores, checkpoints de Koog, retención y contratos HTTP.
- [Sincronización de datos](SINCRONIZACION_DATOS.md): operación offline-first,
  estados, validación multidispositivo y diagnóstico.
- [ADR-001](architecture/ADR-001-SINCRONIZACION-OFFLINE-FIRST.md): decisión,
  seguridad, conflictos y límites de la arquitectura Room/Supabase.
- [Contrato sync v2](architecture/CONTRATO-SYNC-V2.md): payloads canónicos,
  unidades, compatibilidad y reglas para Android/iOS.
- [Integración Firebase](INTEGRACION_FIREBASE.md): responsabilidad frente a
  Supabase, Crashlytics, privacidad y servicios futuros.

## Convenciones

- El idioma principal de la documentación es español.
- Las decisiones relevantes se documentan antes de codificarse o en el mismo PR.
- Los requisitos de seguridad, privacidad y pruebas son criterios de entrega,
  no tareas opcionales de una fase posterior.
- Toda interfaz nueva o modificada debe seguir `IDENTIDAD_VISUAL.md` y la skill
  local `pocketmind-design-system`.

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

## Convenciones

- El idioma principal de la documentación es español.
- Las decisiones relevantes se documentan antes de codificarse o en el mismo PR.
- Los requisitos de seguridad, privacidad y pruebas son criterios de entrega,
  no tareas opcionales de una fase posterior.
- Toda interfaz nueva o modificada debe seguir `IDENTIDAD_VISUAL.md` y la skill
  local `pocketmind-design-system`.

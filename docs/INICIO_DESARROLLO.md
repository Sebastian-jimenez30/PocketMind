# PocketMind — Checklist de inicio de desarrollo

> Esta lista prepara el primer incremento; no sustituye la planificación ni
> autoriza usar secretos reales en el repositorio.

## Decisiones ya tomadas

- Producto Android e iOS con interfaz nativa: Jetpack Compose y SwiftUI.
- Kotlin Multiplatform comparte dominio, validaciones, cálculos y política de
  sincronización; no se comparte la UI inicialmente.
- Room es la persistencia local Android; iOS usa SwiftData o Core Data.
- Supabase es la plataforma de autenticación, PostgreSQL y sincronización.
- La lectura de notificaciones bancarias se implementa solo para Android.
- OpenAI se consume más adelante desde Supabase Edge Functions, nunca desde la
  aplicación cliente.

## Prerrequisitos de equipo

- [ ] Android Studio actualizado, Android SDK y emulador Android configurados.
- [ ] Mac con Xcode actualizado, simulador iOS y cuenta de Apple Developer para
  pruebas en dispositivo y distribución cuando corresponda.
- [ ] JDK, Gradle y Kotlin Multiplatform compatibles con el proyecto.
- [ ] Proyecto Supabase separado para desarrollo y staging; RLS activado.
- [ ] Firebase configurado para Crashlytics, Analytics y distribución de QA.
- [ ] MCP de Supabase autenticado y Firebase CLI autenticado en el equipo que lo
  necesite.
- [ ] GitHub Actions con secretos solo en el almacén de secretos, nunca en Git.

## Primer incremento recomendado

**Objetivo:** un usuario puede abrir ambas aplicaciones, autenticarse con correo,
ver un dashboard vacío y conservar la sesión de forma segura.

Entregables:

1. Raíz KMP con módulos `shared/core`, `shared/data`, `androidApp` e `iosApp`.
2. Modelos de dominio mínimos: `UserProfile`, `Transaction`, `Category` y
   `AppResult`.
3. Supabase Auth para correo/contraseña en Android e iOS.
4. Pantallas nativas de bienvenida, inicio de sesión y dashboard vacío.
5. Persistencia de sesión por plataforma y manejo de carga/error.
6. Pruebas unitarias de validación de correo/contraseña y de `SignInUseCase`.
7. CI que compile Android e iOS, ejecute análisis estático y pruebas compartidas.

## Criterios para empezar la automatización Android

- [ ] El modelo `Transaction` contiene origen, confianza, cuenta y estado de
  revisión.
- [ ] Existe deduplicación y cola de sincronización probadas.
- [ ] Onboarding explica el acceso a notificaciones y su revocación.
- [ ] Hay fixtures anonimizados de Bancolombia con compras, ingresos, reversos y
  formatos desconocidos.
- [ ] Se probará en un dispositivo Android físico, no solo emulador.

## Riesgos que deben revisarse antes de release

- Restricciones OEM de Android para listeners y trabajo en segundo plano.
- Políticas de privacidad de Play Store y App Store sobre datos financieros,
  permisos, analítica y eliminación de cuenta.
- RLS, borrado de cuenta y exportación/retención de datos en Supabase.
- Diferencias de autenticación y deep links entre Android e iOS.
- Precisión de los parsers: baja confianza nunca crea un gasto definitivo.

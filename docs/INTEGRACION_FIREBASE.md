# Integración de Firebase

## Responsabilidad en PocketMind

Firebase es una plataforma de Google con servicios para operar, medir, probar y
distribuir aplicaciones móviles. En PocketMind **no reemplaza a Supabase**.

| Necesidad | Tecnología responsable |
|---|---|
| Autenticación y sesión | Supabase Auth |
| Base financiera y sincronización | Room + Supabase PostgreSQL |
| Archivos y funciones backend | Supabase Storage + Edge Functions |
| Diagnóstico de fallos Android | Firebase Crashlytics |
| Pruebas en dispositivos remotos | Firebase Test Lab |
| Distribución interna futura | Firebase App Distribution |
| Push futuro | Firebase Cloud Messaging |

Mantener esta separación evita dos sistemas de autenticación, dos bases remotas
y conflictos sobre cuál contiene el dato financiero oficial.

## Configuración Android actual

- Proyecto Firebase: `pocketmind-4620261b`.
- Paquete registrado: `com.pocketmind`.
- Configuración: `apps/mobile/app/google-services.json`.
- Firebase Android BoM: `34.16.0`.
- Google Services Gradle plugin: `4.5.0`.
- Crashlytics Gradle plugin: `3.0.7`.
- SDK activo: `firebase-crashlytics`.

Los plugins y dependencias se declaran mediante
`apps/mobile/gradle/libs.versions.toml`; no se escriben versiones directamente
en el módulo.

## Política de privacidad y ambientes

Crashlytics está desactivado en builds `debug` y activado en `release`. Esto
evita enviar a Firebase errores y actividad de desarrollo local.

Reglas obligatorias:

- nunca adjuntar montos, comercios, correos, tokens, notas o contenido bancario
  como claves, logs o excepciones de Crashlytics;
- usar identificadores técnicos no reversibles cuando sea necesario
  correlacionar un fallo;
- documentar Crashlytics en la política de privacidad antes de publicar;
- no habilitar Analytics hasta definir consentimiento, eventos permitidos y
  retención;
- no enviar eventos con nombres o parámetros financieros;
- no añadir Firebase Auth, Firestore o Realtime Database mientras Supabase sea
  el sistema de identidad y persistencia.

## Servicios recomendados por fase

### Ahora: Crashlytics

Reporta cierres inesperados, errores no fatales y ANR de builds de producción.
La consola comenzará a mostrar información después de instalar una build
`release` configurada y registrar el primer evento.

### Antes de beta: App Distribution y Test Lab

App Distribution permite entregar APK/AAB a testers. Test Lab ejecuta pruebas
en dispositivos alojados por Google. Ninguno requiere añadir otro SDK al APK.

### Con recordatorios remotos: Cloud Messaging

FCM servirá para avisos generados por un entorno confiable. El envío debe
realizarse desde una Edge Function o backend, nunca desde el APK. Antes de
implementarlo se requieren:

- permiso de notificaciones en Android 13+;
- almacenamiento seguro y rotación del token FCM por usuario/dispositivo;
- revocación del token al cerrar sesión;
- preferencias y consentimiento;
- payloads sin información financiera sensible;
- pruebas de restricciones de batería en fabricantes como Samsung y Xiaomi.

### Opcional: Analytics y Performance

Solo se incorporarán con un plan de telemetría y consentimiento. En una
aplicación financiera se prefieren métricas agregadas y técnicas, nunca
movimientos, categorías, saldos o textos introducidos por el usuario.

## Archivos y seguridad

`google-services.json` contiene identificadores de configuración del cliente,
no credenciales administrativas. Puede versionarse para este único ambiente.
No se deben incluir:

- cuentas de servicio;
- claves privadas;
- archivos de Firebase Admin;
- secretos de CI;
- tokens OAuth o PAT.

Si se crean ambientes `debug`, `staging` y `release`, cada uno deberá usar un
proyecto Firebase y un archivo de configuración independiente.

## Verificación manual

Como esta integración cambia plugins y dependencias, ejecutar primero
`File > Sync Project with Gradle Files` en Android Studio.

Desde `apps/mobile`:

```powershell
.\gradlew.bat :app:processDebugGoogleServices
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

La build debug debe compilar con Firebase inicializado, pero no enviar reportes
automáticos a Crashlytics.

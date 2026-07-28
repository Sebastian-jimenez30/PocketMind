# Integración continua y despliegues selectivos

## Objetivo

PocketMind valida automáticamente cada cambio publicado y evita reconstruir el
servicio del asistente cuando un commit no afecta su código ni sus
dependencias.

La automatización no reemplaza las pruebas exploratorias en un teléfono físico,
pero sí elimina la necesidad de ejecutar manualmente la suite repetitiva antes
de abrir cada pull request.

## Pipeline de GitHub Actions

El workflow `.github/workflows/continuous-integration.yml` se ejecuta:

- después de cada `push` a cualquier rama;
- manualmente desde **Actions → PocketMind CI → Run workflow**.

Los pushes posteriores sobre una misma rama cancelan la ejecución anterior para
no validar código obsoleto.

### Trabajo `Unit tests, build and lint`

Ejecuta en Linux con Java 21 y Android API 37, disponibles en la imagen oficial
del runner:

```text
:shared:testAndroidHostTest
:assistant-service:test
:app:testDebugUnitTest
:app:compileDebugAndroidTestKotlin
:app:assembleDebug
:app:lintDebug
```

Los valores incluidos en `local.properties` durante CI son marcadores públicos
sin acceso a Supabase, OpenAI o el servicio real. Ningún secreto es necesario
para compilar o ejecutar las pruebas aisladas.

### Trabajo `Android instrumented tests`

Se ejecuta únicamente si el trabajo anterior termina correctamente. Inicia un
emulador Android API 35 y ejecuta:

```text
:app:connectedDebugAndroidTest
```

Los reportes de ambos trabajos se conservan como artefactos durante 14 días.

## Flujo de publicación

Para cada cambio:

1. actualizar `main` desde `origin/main`;
2. crear una rama nueva para un único alcance;
3. implementar y realizar validaciones estáticas locales;
4. crear un único commit;
5. hacer push y abrir el PR inmediatamente;
6. observar `PocketMind CI` en GitHub;
7. si CI falla, corregir en la misma rama;
8. ejecutar `git commit --amend --no-edit`;
9. publicar la reparación con `git push --force-with-lease`;
10. fusionar únicamente cuando todos los trabajos obligatorios estén verdes.

No se agregan commits de reparación a una rama existente. Si el alcance cambia,
se crea otra rama.

## Protección recomendada de `main`

Esta configuración requiere una acción manual única en GitHub:

1. abrir **Settings → Branches** o **Settings → Rules → Rulesets**;
2. crear una regla para `main`;
3. exigir pull request antes del merge;
4. exigir que pasen los status checks;
5. seleccionar:
   - `Unit tests, build and lint`;
   - `Android instrumented tests`;
6. impedir el merge mientras la rama esté desactualizada, si el equipo desea
   validar siempre contra el último `main`.

El workflow funciona sin esta protección, pero GitHub permitiría fusionar un PR
fallido si la regla no está activada.

## Despliegues selectivos de Vercel

`vercel.json` configura:

```json
{
  "ignoreCommand": "bash scripts/vercel-ignore-build.sh"
}
```

Vercel interpreta el resultado de forma inversa a muchos pipelines:

- código `0`: cancela el build;
- código `1`: continúa el build y el despliegue.

El script permite desplegar cuando cambia alguno de estos elementos:

- `services/assistant/**`;
- `apps/mobile/shared/**`;
- wrapper, catálogo o configuración Gradle usados por el servidor;
- `Dockerfile.vercel`;
- `.dockerignore`;
- `vercel.json`;
- el propio script de decisión.

Cambios exclusivos en Android, documentación, recursos visuales, Firebase,
skills o workflows generan una entrada cancelada en Vercel, pero no construyen
ni despliegan el contenedor.

### Redespliegue manual

Un cambio de variables de entorno no crea un commit. En ese caso se debe usar
**Redeploy** desde Vercel y desactivar **Use project's Ignore Build Step** para
forzar la construcción aunque el código no haya cambiado.

### Limitación conocida

El Ignored Build Step se evalúa después de que Vercel recibe el evento del
commit. Por eso puede aparecer un deployment con estado `Canceled`; lo que se
evita es el build costoso y la publicación innecesaria.

## Diagnóstico

### CI no inicia

- comprobar que el commit contiene el workflow en la rama;
- revisar que GitHub Actions esté habilitado para el repositorio;
- abrir **Actions → PocketMind CI**.

### CI falla al compilar Android

- abrir el trabajo `Unit tests, build and lint`;
- descargar `validation-reports-*`;
- corregir el mismo commit mediante amend y `--force-with-lease`.

### Falla el emulador

- revisar primero si fue un fallo de la aplicación o del arranque del AVD;
- reejecutar el trabajo una vez si la infraestructura del runner falló;
- si se reproduce, corregirlo como un fallo real de la rama.

### Vercel omite un cambio relevante

- revisar la salida de `scripts/vercel-ignore-build.sh`;
- agregar la nueva dependencia del servidor a `relevant_paths`;
- usar un redespliegue manual mientras se corrige la regla.

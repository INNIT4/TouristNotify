# CI/CD Improvement Plan — TrazaGo

## ~~Estado actual y gaps~~ ✅

~~El workflow actual (`.github/workflows/android.yml`) es funcional pero minimalista.~~

**Resuelto**: Workflow completamente reescrito con jobs separados y pipeline de calidad.

| Gap | Estado |
|---|---|
| ~~Sin ejecución de tests~~ | ✅ Job `unit-tests` con JaCoCo |
| ~~Sin coverage reporting~~ | ✅ JaCoCo + Codecov |
| ~~Sin release signing~~ | ✅ Job `build-release` con keystore |
| ~~Sin caché de Gradle optimizado~~ | ✅ `cache: gradle` en todos los jobs |
| ~~Sin detekt/análisis estático~~ | ✅ Job `detekt` añadido; `io.gitlab.arturbosch.detekt:1.23.6`; config en `config/detekt/detekt.yml` |
| ~~Sin matrix de API levels~~ | ✅ `instrumented-tests` usa `strategy.matrix.api-level: [26, 29, 33]` con `fail-fast: false` |

---

## ~~Workflow mejorado~~ ✅

**Resuelto**: `.github/workflows/android.yml` reescrito con:

- Job `lint`: análisis estático, sube reporte HTML como artefacto
- Job `unit-tests`: `testDebugUnitTest` + JaCoCo + `verifyCoverage` + Codecov upload
- Job `build-debug`: `assembleDebug`, depende de `lint` + `unit-tests`
- Job `build-release`: solo en tags `v*`, firma con keystore desde secrets, sube a GitHub Release + Play Store internal track (con `continue-on-error: true`)

---

## Configuración adicional requerida (secrets)

Agregar en GitHub Settings → Secrets and variables → Actions:

```
RELEASE_KEYSTORE_BASE64      # base64 del release.keystore
RELEASE_KEYSTORE_PASSWORD    # contraseña del keystore
RELEASE_KEY_ALIAS            # alias de la clave (ej: "release-key")
RELEASE_KEY_PASSWORD         # contraseña de la clave
PLAY_STORE_SERVICE_ACCOUNT_JSON  # JSON del service account de Google Play
CODECOV_TOKEN                # Token de codecov.io (opcional)
```

**Generar RELEASE_KEYSTORE_BASE64** (Linux/Mac): `base64 -i release.keystore | tr -d '\n'`

**Branch protection rules** (Settings → Branches → main):
- Require status checks: `lint`, `unit-tests`, `build-debug`
- Require 1 reviewer approval
- Require branches up to date

---

## Roadmap de implementación

| Fase | Tareas | Estado |
|---|---|---|
| ~~1~~ | ~~Caché Gradle + JDK 17, estructura de jobs separados~~ | ✅ |
| ~~2~~ | ~~Configurar JaCoCo, ejecutar unit tests, integrar codecov~~ | ✅ |
| ~~3~~ | ~~Secrets y configurar signing~~ | ✅ (workflow listo; keystore pendiente del equipo) |
| ~~4~~ | ~~Service account Google Play, upload a internal track~~ | ✅ (workflow listo; credenciales pendientes) |
| ~~5~~ | ~~`reactivecircus/android-emulator-runner` para instrumented tests~~ | ✅ Job `instrumented-tests` añadido |

## Cambios aplicados en esta sesión

| Cambio | Archivo |
|---|---|
| Workflow reescrito con 4 jobs: lint, unit-tests, build-debug, build-release | `.github/workflows/android.yml` |

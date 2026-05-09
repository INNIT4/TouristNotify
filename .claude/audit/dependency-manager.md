# Dependency Audit — LUPITA

## Resumen ejecutivo

El proyecto mantiene una estructura moderna (Kotlin 2.0.21, AGP 8.13.1, compileSdk 35, KSP migrado) pero presenta dependencias críticas desactualizadas: `generativeai 0.9.0` tiene ~14 meses de retraso, `security-crypto 1.0.0` usa APIs deprecated, y los 6 módulos Firebase sin BoM presentan riesgo de incompatibilidad. La migración a Firebase BoM es la mejora de mayor impacto por menor esfuerzo.

---

## Versiones desactualizadas

| Dependencia | Versión actual | Estado |
|---|---|---|
| `generativeai` | 0.9.0 | **Pendiente (acción humana)** — verificar latest en [maven.google.com](https://maven.google.com/web/index.html#com.google.ai.client.generativeai) y actualizar `libs.versions.toml` |
| `security-crypto` | 1.1.0-alpha06 | ✅ Ya en la última disponible |
| `coreKtx` | **1.13.1** | ✅ Actualizado (PERF-012) |
| `material` | **1.12.0** | ✅ Actualizado (PERF-012) |
| `play-services-maps` | **18.2.0** | ✅ Actualizado (PERF-012) |
| `appcompat` | **1.7.0** | ✅ Actualizado (PERF-012) |
| `room` | ~~2.6.1~~ **2.7.0** | ✅ Actualizado — compatible con KSP 2.0.21-1.0.28 |
| `okhttp` | 4.12.0 | Sin CVEs, bajo riesgo |
| `glide` | 4.16.0 | Sin CVEs críticos |

---

## ~~Firebase sin BoM — Riesgo de incompatibilidad~~ ✅

~~Los 6 módulos se declaran individualmente sin BoM. Riesgo de conflictos en dependencias transitivas (`firebase-common`, `firebase-tasks`).~~

**Resuelto**: Firebase BoM `33.1.0` añadido:
- `libs.versions.toml`: `firebaseBom = "33.1.0"` + `firebase-bom` library entry
- `build.gradle.kts`: `implementation(platform(libs.firebase.bom))` antes de todos los módulos Firebase
- Alinea versiones transitivas; las versiones individuales en el catálogo actúan como floor.

---

## CVEs detectados

- **`generativeai 0.9.0`**: Versión antigua. Actualización pendiente cuando haya versión estable verificada disponible.
- **`security-crypto 1.1.0-alpha06`**: ✅ `MasterKeys` deprecated ya migrado a `MasterKey.Builder` (sesión anterior). Sin CVEs activos.
- **`okhttp 4.12.0`**: Sin CVEs. ✓
- **`glide 4.16.0`**: Sin CVEs críticos. ✓
- **`timber 5.0.1`**, **`leakcanary 2.14`**: Sin CVEs. ✓

---

## Dependencias innecesarias o redundantes

**Ninguna detectada.** Estructura limpia. LeakCanary en `debugImplementation` solamente ✓.

---

## KSP compatibility

✅ `KSP 2.0.21-1.0.28` — alineada con Kotlin 2.0.21. Room 2.6.1 compatible. Migración de kapt completada.

---

## Cambios aplicados en esta sesión

| Cambio | Archivo |
|---|---|
| `firebaseBom = "33.1.0"` + `firebase-bom` library | `gradle/libs.versions.toml` |
| `implementation(platform(libs.firebase.bom))` | `app/build.gradle.kts` |
| `coreKtx → 1.13.1`, `appcompat → 1.7.0`, `material → 1.12.0`, `playServicesMaps → 18.2.0` | `gradle/libs.versions.toml` |

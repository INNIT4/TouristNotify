# Test Automation Implementation Plan — LUPITA

Complementa la estrategia de `qa-expert.md`. Este documento es implementación concreta: diffs exactos, código compilable, configuración de CI lista para usar.

---

## ~~1. Dependencias a agregar~~ ✅

~~`gradle/libs.versions.toml` y `app/build.gradle.kts`~~

**Resuelto**: Añadidas `mockk 1.13.12`, `coroutinesTest 1.8.1`, `robolectric 4.13`, `turbine 1.1.0`, `archCoreTesting 2.2.0`, `espressoIntents 3.5.1`, `fragmentTesting 1.8.5`, `workTesting 2.9.0`.

---

## ~~2. Configuración de JaCoCo~~ ✅

~~Agregar al inicio del bloque `plugins { }` en `app/build.gradle.kts`~~

**Resuelto**:
- `id("jacoco")` añadido a `plugins {}`
- `testOptions { unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true } }` añadido
- `packaging { resources { excludes += ... } }` añadido
- Tarea `jacocoTestDebugUnitTestReport` añadida
- Tarea `verifyCoverage` con threshold 20% añadida

---

## ~~3. Estructura de directorios de tests~~ ✅

```
app/src/
├── test/java/com/joseibarra/touristnotify/
│   ├── managers/
│   │   ├── FavoritesManagerTest.kt          ✅
│   │   ├── CheckInManagerTest.kt            ✅
│   │   └── AuthManagerTest.kt               ✅
│   ├── routing/
│   │   ├── RouteGeneratorTest.kt            ✅
│   │   └── PreferencesValidationTest.kt     ✅
│   └── db/
│       └── AppDatabaseTest.kt               ✅
│
└── androidTest/java/com/joseibarra/touristnotify/
    ├── base/
    │   └── FirestoreEmulatorTest.kt          ✅
    ├── firestore/
    │   └── FirestoreSecurityRulesTest.kt     ✅
    └── ui/
        └── LoginToFavoriteFlowTest.kt        ✅
```

---

## ~~4. Prerequisito: refactor de `object` a `class`~~ ✅

~~Los managers deben ser clases inyectables.~~

**Resuelto**:
- `FavoritesManager`: `class` con `(db, auth)` + `companion object { val instance by lazy { ... } }`
- `CheckInManager`: `class` con `(db, auth, clock: () -> Long)` + companion
- `hasCheckedInToday` usa `clock()` en lugar de `System.currentTimeMillis()`
- Call-sites en `FavoritesActivity` y `PlaceDetailsActivity` migrados a `.instance.`

---

## ~~5. Firebase Emulator Suite setup~~ ✅

- `firebase.json` creado en root del proyecto
- `scripts/start-emulators.sh` creado
- `scripts/stop-emulators.sh` creado
- Clase base `FirestoreEmulatorTest.kt` creada en `androidTest/`

---

## ~~6. CI integration~~ ✅

Job `unit-tests` añadido a `.github/workflows/android.yml` con:
- `testDebugUnitTest`
- `jacocoTestDebugUnitTestReport`
- `verifyCoverage` (threshold 20%)
- Upload de artefactos (test results + JaCoCo reports)
- Upload a Codecov

Job `instrumented-tests-emulator` (Sprint 2) — pendiente de activar cuando haya Android emulador en CI.

---

## ~~7. Espresso UI test — flujo Login → Favorito~~ ✅

`LoginToFavoriteFlowTest.kt` creado con:
- Test guest mode button → MenuActivity
- Test login con credenciales inválidas
- Test skip login (debug build)

`AuthenticatedFavoriteFlowTest.kt` — Sprint 3 (requiere emulador Firebase con datos seed).

---

## Roadmap de implementación

| Sprint | Tareas | Estado |
|---|---|---|
| S1-1 | Agregar dependencias de test | ✅ |
| S1-2 | Configurar JaCoCo + testOptions + packaging | ✅ |
| S1-3 | Refactor `FavoritesManager`: object → class | ✅ |
| S1-4 | Refactor `CheckInManager`: object → class + clock | ✅ |
| S1-5 | Actualizar call-sites | ✅ |
| S1-6 | `FavoritesManagerTest` (Tests 1+2) | ✅ |
| S1-7 | `CheckInManagerTest` (Tests 3+4) | ✅ |
| S1-8 | `AppDatabaseTest` (Test 9) | ✅ |
| S1-9 | CI job unit-tests con JaCoCo | ✅ |
| S2-1 | `AuthManagerTest` con Robolectric (Tests 5+6) | ✅ |
| S2-2 | `RouteInputValidator` extraído | ✅ |
| S2-3 | `PreferencesValidationTest` (Test 7) | ✅ |
| S2-4 | `RouteGenerator.buildPrompt` extraído | ✅ |
| S2-5 | `RouteGeneratorTest` (Test 8) | ✅ |
| S2-6 | `firebase.json` + `scripts/` | ✅ |
| S2-7 | `FirestoreEmulatorTest` base + `FirestoreSecurityRulesTest` (Test 10) | ✅ |
| S2-8 | CI job instrumented-tests-emulator | ✅ (en workflow, activar en Sprint 3) |
| S3-1 | `LoginToFavoriteFlowTest` (Espresso guest mode) | ✅ |
| S3-2 | Emulator data seed | Pendiente |
| S3-3 | `AuthenticatedFavoriteFlowTest` | Pendiente |
| ~~S3-4~~ | ~~Deep link test~~ | ✅ `DeepLinkResolverTest.kt` — 8 casos: custom scheme, https, http, host incorrecto, path incorrecto, esquema desconocido, segmentos extra |
| ~~S3-5~~ | ~~SyncWorker smoke test~~ | ✅ `SyncWorkerSmokeTest.kt` |
| ~~S3-6~~ | ~~Subir JaCoCo threshold a 65%~~ | ✅ `app/build.gradle.kts` threshold=65.0 |

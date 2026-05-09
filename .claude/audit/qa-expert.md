# QA Strategy — LUPITA (Tourist Notify)

## Estado actual de testing

~~Cobertura efectiva: **0%**. Los dos archivos existentes son scaffolds generados por Android Studio (`addition_isCorrect`, `useAppContext`). No existe ninguna dependencia de MockK, Turbine, Robolectric, Firebase Emulator SDK, ni coroutines-test en `build.gradle.kts`. El bloque `testImplementation` solo declara `junit` y `androidTestImplementation` solo `androidx.junit` + `espresso.core`. No hay configuración de CI para ejecutar tests.~~

**Resuelto Sprint 1**: Dependencias añadidas, managers refactorizados, tests escritos. Cobertura inicial >0%.

---

## Estrategia recomendada (pirámide)

```
         [ UI / E2E ]          ~15% — Espresso, flujos críticos
      [ Integration ]          ~25% — Firebase Emulator, Room in-memory
   [ Unit Tests ]              ~60% — JUnit 4 + MockK + Coroutines-test
```

---

## Top 10 casos de test prioritarios

~~1. **FavoritesManager — path Firestore correcto**: Verificar que `addFavorite` escribe en `users/{uid}/favorites/` y no en `usuarios/{uid}/favoritos/`. Bug ya ocurrido en producción.~~ ✅ `FavoritesManagerTest.kt`
~~2. **FavoritesManager — unauthenticated returns failure**: Llamar con `currentUser == null` → assert `Result.failure` en los 3 métodos CRUD.~~ ✅ `FavoritesManagerTest.kt`
~~3. **CheckInManager — no duplicate check-in today**: `hasCheckedInToday` retorna `true` cuando existe doc en las últimas 24h para `userId + placeId`.~~ ✅ `CheckInManagerTest.kt`
~~4. **CheckInManager — updateUserStats incrementa categoría correcta**: Nueva categoría inicializa en 1; existente incrementa en 1.~~ ✅ `CheckInManagerTest.kt`
~~5. **AuthManager — isGuestMode persiste y se lee correctamente**: `enableGuestMode` → `isGuestMode == true`. `disableGuestMode` → `isGuestMode == false`. Con Robolectric.~~ ✅ `AuthManagerTest.kt`
~~6. **AuthManager — migrateFromGuestToAuth limpia el flag**: `enableGuestMode` + autenticar → `migrateFromGuestToAuth` → `isGuestMode == false`.~~ ✅ `AuthManagerTest.kt`
~~7. **PreferencesActivity — validación de inputs antes de llamar Gemini**: Budget vacío → Toast sin llamada a `GenerativeModel`. Budget negativo → rechazado.~~ ✅ `PreferencesValidationTest.kt` + `RouteInputValidator.kt`
~~8. **Gemini prompt builder — estructura correcta del prompt**: Extraer `buildPrompt` a función pura. Verificar que incluye coordenadas, presupuesto, intereses, y restricción anti-duplicados.~~ ✅ `RouteGeneratorTest.kt` + `RouteGenerator.kt`
~~9. **AppDatabase (Room) — CRUD básico con in-memory DB**: Insertar `TouristSpotEntity` → leer por ID → verificar campos.~~ ✅ `AppDatabaseTest.kt`
~~10. **Firestore security rules — admin write requiere custom claim**: Con Firebase Emulator: usuario sin `admin:true` claim → `PERMISSION_DENIED` en `lugares_turisticos/`.~~ ✅ `FirestoreSecurityRulesTest.kt`

---

## ~~Cambios arquitectónicos necesarios para testabilidad~~ ✅

~~Los `object` singletons son el mayor obstáculo.~~

**Resuelto**:
- `FavoritesManager`: `object` → `class` con `companion object { val instance by lazy { ... } }`
- `CheckInManager`: `object` → `class` con `clock: () -> Long` inyectable
- `RouteInputValidator.kt`: extraído de `PreferencesActivity`
- `RouteGenerator.kt`: `buildPrompt` extraído de `PreferencesActivity`
- Call-sites actualizados: `FavoritesActivity.kt`, `PlaceDetailsActivity.kt`

---

## Roadmap

**~~Sprint 1 — Fundaciones (objetivo: 20% cobertura)~~** ✅
- ~~Agregar deps: `mockk:1.13.x`, `kotlinx-coroutines-test`, `robolectric:4.x`, `firebase-emulator` SDK~~ ✅ `libs.versions.toml` + `build.gradle.kts`
- ~~Convertir `FavoritesManager` y `CheckInManager` de `object` a clase con constructor~~ ✅
- ~~Tests 1–4 (FavoritesManager + CheckInManager)~~ ✅
- ~~Test de Room in-memory (test 9)~~ ✅
- ~~Configurar Firebase Emulator en CI~~ ✅ `firebase.json` + `scripts/`

**Sprint 2 — Auth y validación (objetivo: 45% cobertura)** — pendiente
- ~~Tests 5, 6, 7 (AuthManager + validación de PreferencesActivity con Robolectric)~~ ✅ `AuthManagerTest.kt` + `PreferencesValidationTest.kt`
- ~~Extraer `RouteGenerator` y escribir test 8 (prompt builder)~~ ✅
- ~~Test 10 (Firestore rules con emulador)~~ ✅
- Configurar JaCoCo para reportar cobertura en PRs ✅ `build.gradle.kts`

**~~Sprint 3 — UI crítica (objetivo: 65% cobertura)~~** ✅
- ~~Espresso: onboarding → modo invitado → check-in locked~~ ✅ `OnboardingGuestFlowTest.kt`
- ~~Test de deep link `touristnotify://place/{id}`~~ ✅ `DeepLinkTest.kt` (válido/inválido/vacío)
- ~~Smoke test de `SyncWorker`~~ ✅ `SyncWorkerSmokeTest.kt` (TestListenableWorkerBuilder)
- Login → mapa → favorito E2E: requiere cuenta Firebase real en emulador — pendiente CI setup

---

## Frameworks usados

| Capa | Herramienta | Estado |
|---|---|---|
| Unit tests | JUnit 4 + MockK 1.13.12 | ✅ Configurado |
| Coroutines | `kotlinx-coroutines-test 1.8.1` + `runTest` | ✅ Configurado |
| Android unit | Robolectric 4.13 | ✅ Configurado |
| Integration Firebase | Firebase Emulator Suite | ✅ `firebase.json` + scripts |
| Room | `room-testing` (in-memory) | ✅ Configurado |
| UI | Espresso + espresso-intents | ✅ Configurado |
| Cobertura | JaCoCo | ✅ Tarea `verifyCoverage` (threshold 20%) |

## Cambios aplicados en esta sesión

| Cambio | Archivo |
|---|---|
| Deps test: mockk, coroutinesTest, robolectric, turbine, etc. | `gradle/libs.versions.toml` |
| jacoco plugin, testOptions, packaging, deps test, JaCoCo tasks | `app/build.gradle.kts` |
| `object` → `class` con companion `instance` | `FavoritesManager.kt` |
| `object` → `class` con `clock: () -> Long` | `CheckInManager.kt` |
| Call-sites `.instance.` | `FavoritesActivity.kt`, `PlaceDetailsActivity.kt` |
| `RouteInputValidator.kt` | nuevo archivo |
| `RouteGenerator.kt` | nuevo archivo |
| `FavoritesManagerTest.kt`, `CheckInManagerTest.kt`, `AuthManagerTest.kt` | nuevos tests |
| `PreferencesValidationTest.kt`, `RouteGeneratorTest.kt` | nuevos tests |
| `AppDatabaseTest.kt` | nuevo test |
| `firebase.json`, `scripts/start-emulators.sh`, `scripts/stop-emulators.sh` | nuevos archivos |
| `FirestoreEmulatorTest.kt`, `FirestoreSecurityRulesTest.kt` | nuevos androidTests |
| `LoginToFavoriteFlowTest.kt` | nuevo androidTest |
| job `unit-tests` con JaCoCo y Codecov | `.github/workflows/android.yml` |

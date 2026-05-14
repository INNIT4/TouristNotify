# Architecture Review — TrazaGo

## Resumen ejecutivo

TrazaGo funciona, pero su stack minimalista (sin ViewModel, sin DI, sin Navigation Component) ha cruzado el umbral donde empieza a costar más de lo que ahorra. Hay **inconsistencias críticas en nombres de colecciones Firestore** entre módulos que están causando que la sincronización offline lea datos distintos a la fuente real. El patrón offline-first es unidireccional (no bidireccional como documenta CLAUDE.md) y sin resolución de conflictos. Recomendación: **no migrar big-bang**, pero sí introducir ViewModel + Repository incremental y corregir P0 de colecciones antes de cualquier otra cosa.

---

## Análisis del stack actual

**Sin ViewModel**: `MapsActivity.kt` retiene estado crítico en campos (`allSpots`, `touristSpotMarkers`, `currentRouteSpots`, `markerGeneration`, `polylineAnimator`). En rotación de pantalla se pierde todo y se re-lee Firestore. `MenuActivity.kt` llama `loadWeather()` en cada `onCreate`. Costo concreto: **re-consultas innecesarias a Firestore/OpenWeather, y estado de UI que parpadea**.

**Sin DI**: Singletons `object` (`FavoritesManager`, `CheckInManager`, `AuthManager`) son imposibles de testear unitariamente.

**Sin Navigation Component**: 22 Activities con `startActivity` puro. Backstack impredecible al entrar por deep link vs mapa vs favoritos.

---

## Hallazgos

### ~~P0 — Inconsistencia de nombres de colecciones Firestore (bug de datos silencioso)~~ ✅
- ~~`OfflineManager.kt:108` sincroniza `"events"` mientras `EventsActivity.kt:50` lee `"eventos"`. `ProximityNotificationManager.kt:63` usa `"lugares_turisticos"` mientras el resto usa `"lugares"`.~~
- **Resuelto**: `FirestoreCollections.kt` creado como fuente única de verdad. `OfflineManager` usa `FirestoreCollections.EVENTS`. `ProximityNotificationManager` usa `FirestoreCollections.PLACES`. `FavoritesManager` migrado a usar constantes.

### ~~P0 — Offline writes no se reflejan en Room~~ ✅
- ~~`FavoritesManager.addFavorite` escribía solo a Firestore. Room quedaba desactualizado hasta el siguiente SyncWorker (hasta 6h).~~
- **Resuelto**: `FavoritesManager.addFavorite()` y `removeFavorite()` aceptan `context: Context?` y hacen write-through a `FavoriteDao` inmediatamente después del éxito en Firestore. `deleteFavoriteByPlaceId()` agregado al DAO.

### ~~P1 — `MapsActivity` 1048 líneas viola SRP (`MapsActivity.kt` completo)~~ ✅
- **Resuelto Sprint 3**: `MarkerRenderer`, `RoutePolylineManager`, `RouteNavigationController`, `PlaceDataRepository` extraídos. `MapsActivity` reducido de 1048 → ~340 líneas. Ver `.claude/audit/refactoring-specialist.md`.

### P1 — `SyncWorker` no testeable (`SyncWorker.kt:15`)
- `SyncWorker` instancia `OfflineManager` directamente. Pendiente cuando se introduzca `WorkerFactory`.

### ~~P1 — `OfflineManager.syncFromFirebase` ignora flag `offline_mode_enabled` si se llama directamente~~ ✅
- **Resuelto**: `syncFromFirebase()` ahora incluye `if (!isOfflineModeEnabled()) return Result.success(Unit)` al inicio. El check en `SyncWorker` se mantiene como early exit para evitar instanciar `OfflineManager` innecesariamente.

### P1 — `AuthManager.requireAuth` return value
- ~~Retorna Boolean que las Activities ignoran.~~ ✅ El patrón actual usa callback lambda (`onAuthConfirmed`) — el return value es redundante pero no produce bugs. Las Activities usan correctamente la lambda.

### ~~P2 — `AppDatabase.fallbackToDestructiveMigration()` ausente~~ ✅
- **Resuelto**: `AppDatabase.kt` incluye `fallbackToDestructiveMigration()` y `fallbackToDestructiveMigrationOnDowngrade()`.

### ~~P2 — `applicationScope` usa `Dispatchers.Main` para trabajo de Remote Config~~ ✅
- ~~`TrazaGoApplication.kt:23` lanzaba `ConfigManager.initialize()` (I/O) en `Dispatchers.Main`.~~
- **Resuelto**: Cambiado a `Dispatchers.IO`.

### P2 — Duplicación de 7 Intent extras de place en 4+ Activities
- Pendiente. Crear `PlaceSummary : Parcelable` centraliza los extras.

---

## Roadmap de mejoras

**Sprint 1 — P0 fixes** ✅ Completado

**Sprint 2 — Testabilidad básica (3-5 días)**
- Introducir `Repository` layer: `PlacesRepository`, `FavoritesRepository`, `CheckInRepository` como interfaces.
- Convertir singletons `object` con Firestore en `class` con constructor que recibe dependencias.
- Empezar tests unitarios con fakes.

**Sprint 3 — ViewModel piloto (1 semana)**
- Migrar **solo `MapsActivity`** a `MapsViewModel` + StateFlow. Objetivo: Activity < 400 líneas.

**Sprint 4+ — Hilt opcional cuando haya ≥5 ViewModels**
- No antes.

**Lo que NO recomiendo**: Jetpack Compose (reescritura de 50+ layouts), Navigation Component (requiere reescribir Activities → Fragments). Mantener Activities + deep links está bien a esta escala.

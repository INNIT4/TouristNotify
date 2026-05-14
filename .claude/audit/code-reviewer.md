# Code Review — Cambios Pendientes TrazaGo

## Resumen de cambios (qué se modificó)

46 archivos modificados (+1365 / -642). Principales ejes:

- **Build**: `app/build.gradle.kts` migra de `kapt` a `ksp`, sube `compileSdk`/`targetSdk` a 35, alinea deps con `gradle/libs.versions.toml`.
- **Seguridad**: `AuthManager.kt` introduce `EncryptedSharedPreferences`, `network_security_config.xml`, `firestore.rules` endurecido, `android:exported="false"` en Activities.
- **Constantes**: `AppConstants.kt` centraliza PREFS, timeouts (AI_TIMEOUT_MS=60s, SEARCH_DEBOUNCE_MS=400ms), geofence.
- **Maps**: `MapsActivity.kt` convierte `markerGeneration` a `AtomicInteger`, añade `isActivityAlive`, cancela `polylineAnimator` en `onDestroy()`, mueve `OkHttpClient` a `companion object`, limita query a `.limit(200)`.
- **PlaceDetails**: Migra de callbacks a `await()`, `coerceIn(0.0, 5.0)` para rating, añade `sharePlaceLink()` con deep link.
- **IA**: `PreferencesActivity.kt` añade `withTimeout(60_000L)`, flag `isGenerating`, limpieza de `Handler`/`Runnable` en `onDestroy()`.
- **OfflineManager**: Corrige paths de Firestore (`users/{uid}/favorites` y `checkIns`).
- **RecyclerView**: `EventsActivity`, `FavoritesActivity` migran a `ListAdapter` + `submitList()` con `DiffUtil`.
- **i18n**: Masiva extracción de strings hardcodeados a `strings.xml`.

---

## Riesgos de regresión detectados

### P0 — Bloqueantes antes de commit

~~**[CR-001] `AppDatabase.kt:49` — eliminación de `fallbackToDestructiveMigration()` sin migración**~~ ✅
- **Resuelto**: `AppDatabase.kt` incluye `fallbackToDestructiveMigration()` y `fallbackToDestructiveMigrationOnDowngrade()`. Version = 1 sin cambios pendientes de schema.

~~**[CR-002] `AuthManager.kt:9,27` — regresión a `MasterKeys` API deprecada**~~ ✅
- **Resuelto**: `AuthManager` usa `MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)`.

~~**[CR-003] `firestore.rules:43-47` — `visitCount`/`rating` modificables sin validación de delta**~~ ✅
- **Resuelto**: `firestore.rules` ya tiene `visitCount == resource.data.get('visitCount', 0) + 1`, `rating in [0,5]`, `reviewCount` solo +1 o igual.

### P1 — Altos

~~**[CR-004] `MapsActivity.kt:795` — `.limit(200)` silencioso**~~ ✅
- **Resuelto**: Añadido `Log.w(TAG, "resultado truncado a 200 documentos")` cuando `documents.size() == 200`. También corregido literal `"lugares"` → `FirestoreCollections.PLACES`.

~~**[CR-005] OkHttp callbacks sin verificar `isActivityAlive` antes de tocar `binding`**~~ ✅
- **Resuelto**: `calculateAndDrawRoute` catch ahora tiene `if (isActivityAlive) withContext(Dispatchers.Main) { ... }`.

**[CR-006] `OfflineManager.kt:134-136` — sin migración del path anterior de `favorites`**
- Pendiente. Dato de usuarios con historial offline en path viejo se perderá silenciosamente en el primer sync. Requiere decisión de negocio.

~~**[CR-007] `PreferencesActivity.kt:289` — `handler` reinstanciado sin limpiar el anterior**~~ ✅
- **Resuelto**: `cleanupProgress()` ahora asigna `handler = null` y `progressRunnable = null` tras `removeCallbacks`.

### P2 — Menores

- **`MapsActivity.kt`**: `uiVisible` sigue declarado entre métodos. Pendiente de refactor SRP (Sprint 3).
- ~~**`FavoritesActivity.kt:114-117`**: Muestra `R.string.error_generic` sin loguear el detalle.~~ ✅ Añadido `Log.e("FavoritesActivity", ...)`.
- ~~**`BlogActivity.kt:303-305`**: `addOnFailureListener` de like no loguea el error `e`.~~ ✅ Añadido `Log.e("BlogActivity", ...)`.
- **Imports no usados** en `AppDatabase.kt`: Verificado — no hay imports de `android.util.Log` o `SupportSQLiteDatabase` en el archivo actual.
- ~~**Strings residuales hardcodeados en `AuthManager.requireAuth()`**~~ ✅ Migrados a `R.string.login_button`, `R.string.auth_required_for_action`, `R.string.later`.
- ~~**`AppConstants.PREFS_NAME` duplica `AuthManager.PREFS_NAME`**~~ ✅ `AuthManager` ahora usa `AppConstants.PREFS_NAME`.

---

## Verificación de fixes recientes

| Commit | Estado |
|---|---|
| `3539d58` Marcadores duplicados IA | ✅ Fix intacto y mejorado. `markerGeneration.incrementAndGet()` + `AtomicInteger` refuerza thread-safety. |
| `386bbe2` EncryptedSharedPreferences | ✅ Usa `MasterKey.Builder` moderno. |
| `386bbe2` Coordenadas centralizadas | ✅ `AppConstants.ALAMOS_LAT=27.0275`, `ALAMOS_LNG=-108.94` correcto para Álamos, Sonora. |
| `386bbe2` Deep links | ✅ `sharePlaceLink()` usa `TrazaGo://place/$id`, consistente con manifest. |
| `910b688` 6 correcciones bugs/datos | ✅ Paths Firestore corregidos en `OfflineManager`. Sigue correcto. |

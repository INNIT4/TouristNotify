# Kotlin Quality Review — TrazaGo

## Resumen ejecutivo

La base de código muestra calidad general aceptable para una app Android XML-based sin Compose. Las capas de datos (DAOs, entidades, modelos) son sólidas y razonablemente idiomáticas. Los problemas más graves están en el manejo del ciclo de vida de coroutines en `PreferencesActivity` y en patrones de concurrencia en los singletons `object`. No se detectó uso de `GlobalScope`. Se identificaron 3 hallazgos P0, 5 P1 y 4 P2.

---

## Hallazgos P0 (críticos — bugs latentes)

### ~~[KT-001] Race condition en `Handler`/`Runnable` — `PreferencesActivity.kt:273-283`~~ ✅
- ~~`progressRunnable!!` en línea 283 lanza `NullPointerException` si `cleanupProgress()` fue llamado concurrentemente~~
- **Resuelto**: Reemplazado `handler?.postDelayed(progressRunnable!!, 1500)` por `progressRunnable?.let { handler?.postDelayed(it, 1500) }`.

### ~~[KT-002] Callbacks Firestore no ligados al lifecycle — `PreferencesActivity.kt:133-165`~~ ✅
- ~~`addOnSuccessListener`/`addOnFailureListener` directos sin binding al lifecycle~~
- **Resuelto**: Convertido a `lifecycleScope.launch` + `task.await()`. Verificación `if (!isDestroyed)` añadida antes de Toast.

### ~~[KT-003] `OfflineManager.syncUserData` usa path Firestore potencialmente inconsistente~~ ✅
- **Resuelto**: `PreferencesActivity` usa `FirestoreCollections.PLACES`. `OfflineManager` ya usaba `FirestoreCollections.*`. Centralización completa via `FirestoreCollections.kt`.

---

## Hallazgos P1 (calidad / mantenibilidad)

### ~~[KT-004] Singletons `object` con Firestore no permiten testing — `FavoritesManager.kt:9-10`, `CheckInManager.kt:9-10`~~ ✅
- **Resuelto**: Ambas clases ya usan inyección en constructor con defaults (`db: FirebaseFirestore = FirebaseFirestore.getInstance()`). Tests unitarios pueden pasar fakes/mocks directamente al constructor. `companion object { val instance by lazy {...} }` mantiene el punto de acceso singleton para producción. Adicionalmente se reemplazaron strings literales en `CheckInManager` (`"checkIns"`, `"lugares"`, `"users"`) por constantes `FirestoreCollections.*`.

### ~~[KT-005] Coroutines de generación IA continúan en background cuando usuario sale~~ ✅
- **Resuelto**: Añadido `if (isDestroyed) return@launch` al inicio de cada bloque de UI post-coroutine en `generateRouteWithAI` (success, timeout, y catch genérico).

### ~~[KT-006] `Converters.gson` instancia nueva por cada uso — `RoomEntities.kt:18`~~ ✅
- **Resuelto**: `private val gson = Gson()` movido a `companion object` en `Converters`.

### ~~[KT-007] `OfflineManager` usa `SharedPreferences` sin cifrar — `OfflineManager.kt:17-19`~~ ✅
- **Resuelto**: `OfflineManager` ahora usa `EncryptedSharedPreferences` (AES256_GCM) con fallback a plain prefs si el Keystore no está disponible. Dependencia `libs.security.crypto` añadida a `build.gradle.kts`.

### ~~[KT-008] `SyncWorker` hace `Result.retry()` en cualquier excepción~~ ✅
- **Resuelto**: Distingue errores fatales (`SecurityException`, `IllegalArgumentException`, `IllegalStateException`) → `Result.failure()`, resto → `Result.retry()`.

---

## Hallazgos P2 (idiomaticidad / estilo)

### ~~[KT-009] `getSelectedInterests()` usa `ArrayList` mutable explícita~~ ✅
- **Resuelto**: Reemplazado con `buildList { if (binding.chkX.isChecked) add(...) }`.

### ~~[KT-010] `foundPlaceNames` `ArrayList` mutable innecesario~~ ✅
- **Resuelto**: Simplificado a `ArrayList(knownPlaceNames.mapNotNull { ... }.sortedBy { it.second }.distinctBy { it.first }.map { it.first })`. Eliminada la variable `placeWithIndex` separada.

### ~~[KT-011] `BlogPost` implementa `java.io.Serializable` en lugar de `@Parcelize`~~ ✅
- **Resuelto**: `BlogPost` es ahora `@Parcelize` con `DateParceler` para `Date?`. Plugin `kotlin-parcelize` añadido a `build.gradle.kts`. `BlogPostDetailActivity` usa `getParcelableExtra`.

### ~~[KT-012] `else` en `getSelectedTravelType/Pace/Mobility` devuelve strings no localizados~~ ✅
- **Resuelto**: Todos los valores ahora usan `getString(R.string.travel_type_unspecified)`, `getString(R.string.pace_moderate)`, `getString(R.string.mobility_walking)`. Recursos añadidos en `strings.xml`.

---

## Buenas prácticas detectadas

- **`ConnectivityObserver.kt`**: `callbackFlow` correcto con `awaitClose { unregisterNetworkCallback(callback) }`, `distinctUntilChanged()`, verifica `NET_CAPABILITY_INTERNET` + `NET_CAPABILITY_VALIDATED` (crítico para portales cautivos).
- **`RoomDAOs.kt`**: Ofrece tanto `Flow<List<T>>` para observación reactiva como `suspend fun` para lecturas puntuales.
- **`SyncWorker.kt`**: Usa `CoroutineWorker` (no `Worker` bloqueante).
- **`CheckInManager.updateUserStats`**: Usa `db.runTransaction {}` para actualizar estadísticas atómicamente.
- **`AuthManager.requireAuth`**: Patrón callback con trailing lambda idiomático.
- **`Models.kt`**: Todos los data classes completamente inmutables (`val` en todos los campos), con valores por defecto para deserialización de Firestore. Sin `var` innecesario.

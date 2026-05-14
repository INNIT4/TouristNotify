# Plan de Limpieza — Eliminar Modo Offline + Estadísticas

**Para la AI ejecutora**: Sigue este plan en orden. Cada checkbox es atómico. Al final hay verificación con `./gradlew assembleDebug`. NO inventes pasos extra. NO refactorices código fuera del scope.

**Contexto de la decisión**: Firestore SDK ya tiene cache offline persistente (`PersistenceEnabled = true` en `TrazaGoApplication.kt`). La capa Room + SyncWorker es redundante. `StatsActivity` muestra info que `ProfileActivity` ya cubre + un pie chart que no aporta. Se borran ambas features completas.

---

## TAREA 1 — Eliminar Modo Offline (Room + SyncWorker + SQLCipher)

### 1.1 Borrar archivos completos

```
app/src/main/java/com/joseibarra/TrazaGo/OfflineManager.kt
app/src/main/java/com/joseibarra/TrazaGo/OfflineSettingsActivity.kt
app/src/main/java/com/joseibarra/TrazaGo/SyncWorker.kt
app/src/main/java/com/joseibarra/TrazaGo/SyncWorkerFactory.kt
app/src/main/java/com/joseibarra/TrazaGo/ConnectivityObserver.kt
app/src/main/java/com/joseibarra/TrazaGo/AppDatabase.kt
app/src/main/java/com/joseibarra/TrazaGo/RoomDAOs.kt
app/src/main/java/com/joseibarra/TrazaGo/RoomEntities.kt
app/src/main/java/com/joseibarra/TrazaGo/DatabasePassphraseManager.kt
app/src/main/res/layout/activity_offline_settings.xml
app/src/test/java/com/joseibarra/TrazaGo/db/AppDatabaseTest.kt
app/src/androidTest/java/com/joseibarra/TrazaGo/worker/SyncWorkerSmokeTest.kt
```

Y la carpeta entera de schemas Room: `app/schemas/` (si existe).

### 1.2 Modificar `app/src/main/java/com/joseibarra/TrazaGo/MenuActivity.kt`

- Eliminar el `MenuItemData` con `id = MenuItemId.OFFLINE` (aprox líneas 154-162). Es el bloque completo `MenuItemData(...)` con `iconEmoji = "📴"`.
- Eliminar el case `MenuItemId.OFFLINE -> { ... }` del `when` de navegación (líneas ~224-227).
- Eliminar la entrada `OFFLINE` del enum `MenuItemId` (busca `enum class MenuItemId` y borra ese valor).

### 1.3 Modificar `app/src/main/java/com/joseibarra/TrazaGo/TrazaGoApplication.kt`

- Quitar `Configuration.Provider` de la firma de la clase: `class TrazaGoApplication : Application()` (sin la coma + interface).
- Borrar la propiedad `workManagerConfiguration` (líneas ~25-29).
- Borrar la llamada `schedulePeriodSync()` en `onCreate()` (línea ~74) y la función `private fun schedulePeriodSync() { ... }` completa (líneas ~77-91).
- Borrar imports ya no usados:
  - `androidx.work.Configuration`
  - `androidx.work.Constraints`
  - `androidx.work.ExistingPeriodicWorkPolicy`
  - `androidx.work.NetworkType`
  - `androidx.work.PeriodicWorkRequestBuilder`
  - `androidx.work.WorkManager`
  - `java.util.concurrent.TimeUnit` (si solo lo usaba schedulePeriodSync; OkHttp también lo usa, déjalo si sigue referenciado).

### 1.4 Modificar `app/src/main/AndroidManifest.xml`

- Borrar el bloque `<activity android:name=".OfflineSettingsActivity" ... />` (líneas ~216-219).

### 1.5 Modificar `app/src/main/java/com/joseibarra/TrazaGo/AccountManager.kt`

- En la línea ~46, borrar la línea: `AppDatabase.getDatabase(context.applicationContext).clearAllTables()`.
- Si esa línea queda sola en una corutina/withContext que se vuelve vacía, simplifica el bloque.
- Borrar el import `com.joseibarra.TrazaGo.AppDatabase` si quedó huérfano.

### 1.6 Modificar `app/src/main/java/com/joseibarra/TrazaGo/FavoritesManager.kt`

Eliminar los dos bloques write-through a Room:

- Líneas ~85-96 (dentro de `addFavorite`): borrar todo el bloque que arranca con `// Write-through: mantener Room sincronizado sin esperar al SyncWorker` hasta el cierre del `context?.let { ... }` (incluido).
- Líneas ~115-118 (dentro de `removeFavorite`): borrar el bloque `// Write-through: refleja borrado en Room inmediatamente` + `context?.let { ... }`.
- Si el parámetro `context: Context?` queda sin uso en `addFavorite`/`removeFavorite`, **déjalo** — los callers lo siguen pasando. No cambies firmas.
- Borrar imports huérfanos: `AppDatabase`, `FavoriteEntity`, `Context` (solo si ya no se usa en el archivo).

### 1.7 Modificar `app/src/main/java/com/joseibarra/TrazaGo/GlobalSearchActivity.kt`

Reemplazar la búsqueda Room por Firestore. En la función `performSearch(query: String)`, líneas ~110-127:

**Antes**:
```kotlin
val places = withContext(Dispatchers.IO) {
    AppDatabase.getDatabase(this@GlobalSearchActivity).touristSpotDao().searchSpots(query)
}
places.forEach { entity ->
    searchResults.add(SearchResult(
        id = entity.id,
        title = entity.nombre,
        subtitle = "📍 ${entity.categoria} - ${entity.descripcion}",
        type = SearchResultType.PLACE
    ))
}
```

**Después**:
```kotlin
val placesDeferred = async {
    runCatching {
        db.collection(FirestoreCollections.PLACES).limit(100).get().await()
    }.getOrNull()
}
placesDeferred.await()?.forEach { doc ->
    val nombre = doc.getString("nombre") ?: ""
    val categoria = doc.getString("categoria") ?: ""
    val descripcion = doc.getString("descripcion") ?: ""
    if (nombre.contains(query, true) || descripcion.contains(query, true) || categoria.contains(query, true)) {
        searchResults.add(SearchResult(
            id = doc.id,
            title = nombre,
            subtitle = "📍 $categoria - $descripcion",
            type = SearchResultType.PLACE
        ))
    }
}
```

- Borrar el comentario `// PERF-001: Room search — 0 lecturas Firestore para lugares (caché local)`.
- Borrar imports huérfanos: `AppDatabase`, `kotlinx.coroutines.Dispatchers` (si solo lo usaba ese `withContext`), `kotlinx.coroutines.withContext` (idem).

### 1.8 Modificar `app/build.gradle.kts`

- Borrar la línea `arg("room.schemaLocation", "$projectDir/schemas")` (~línea 89). Si el bloque `ksp { arg(...) }` queda vacío, borra el bloque entero.
- Borrar las 3 deps de Room (~líneas 126-129):
  - `implementation(libs.room.runtime)`
  - `implementation(libs.room.ktx)`
  - `ksp(libs.room.compiler)`
  - + comentario `// Room para base de datos local (modo offline)`
- Borrar las deps de SQLCipher (~líneas 143-145):
  - `implementation(libs.sqlcipher.android)`
  - + comentario `// SQLCipher: cifrar la DB Room (P2-5).`
- Borrar `testImplementation(libs.room.testing)` (~línea 159).
- Borrar `androidTestImplementation(libs.room.testing)` (~línea 173).

### 1.9 Modificar `gradle/libs.versions.toml`

- Borrar versiones: `room = "2.7.0"` y `sqlcipher = "4.5.4"`.
- Borrar entradas en `[libraries]`:
  - `room-runtime = ...`
  - `room-ktx = ...`
  - `room-compiler = ...`
  - `room-testing = ...`
  - `sqlcipher-android = ...`

### 1.10 Modificar strings (`app/src/main/res/values/strings.xml` + `app/src/main/res/values-en/strings.xml`)

Borrar de **ambos** archivos:
- `<!-- Offline -->` y todas las strings bajo ese comentario hasta `offline_data_cleared` (líneas ~180-194 en `values/strings.xml`).
- `<string name="menu_card_offline">...</string>` (~línea 330).
- `<string name="a11y_card_offline">...</string>` (~línea 343).

### 1.11 Limpiar `app/proguard-rules.pro`

- Buscar reglas para `androidx.room`, `net.zetetic` (SQLCipher), `org.sqlite` y borrarlas si están presentes.

---

## TAREA 2 — Eliminar Mis Estadísticas

### 2.1 Borrar archivos completos

```
app/src/main/java/com/joseibarra/TrazaGo/StatsActivity.kt
app/src/main/res/layout/activity_stats.xml
```

### 2.2 Modificar `app/src/main/java/com/joseibarra/TrazaGo/MenuActivity.kt`

- Borrar el `MenuItemData` con `id = MenuItemId.STATS` (líneas ~121-129, el del icon `📊`).
- Borrar el case `MenuItemId.STATS -> AuthManager.requireAuth(...)` del `when` (~líneas 208-211).
- Borrar la entrada `STATS` del enum `MenuItemId`.

### 2.3 Modificar `app/src/main/AndroidManifest.xml`

- Borrar el bloque `<activity android:name=".StatsActivity" ... />` (~líneas 165-168).

### 2.4 Modificar `app/src/main/java/com/joseibarra/TrazaGo/Models.kt`

- Borrar el `data class UserStats(...)` (líneas ~53 en adelante; busca `data class UserStats` y borra hasta el cierre `}` correspondiente).

### 2.5 Modificar `app/src/main/java/com/joseibarra/TrazaGo/CheckInManager.kt`

- Borrar la llamada `updateUserStats(currentUser.uid, placeCategory)` en línea ~51.
- Borrar la función completa `private suspend fun updateUserStats(userId: String, category: String) { ... }` (líneas ~107 en adelante hasta su `}`).
- Borrar imports huérfanos relacionados a UserStats si los hay.
- **Nota**: El test `app/src/test/java/com/joseibarra/TrazaGo/managers/CheckInManagerTest.kt` puede tener referencias a `updateUserStats` o `UserStats`. Si las hay, borrar esos casos de test.

### 2.6 Modificar `app/src/main/java/com/joseibarra/TrazaGo/AuthManager.kt`

- Borrar la línea `const val VIEW_STATS = "ver estadísticas personales"` (~línea 129).

### 2.7 Modificar `app/build.gradle.kts`

- Borrar `implementation(libs.mpandroidchart)` (~línea 117) + comentario `// MPAndroidChart para gráficas de estadísticas`.

### 2.8 Modificar `gradle/libs.versions.toml`

- Borrar `mpandroidchart = "..."` en `[versions]`.
- Borrar `mpandroidchart = { group = "com.github.PhilJay", ... }` en `[libraries]`.

### 2.9 Modificar strings (`app/src/main/res/values/strings.xml` + `app/src/main/res/values-en/strings.xml`)

Borrar de **ambos** archivos:
- `<string name="stats_load_error">...</string>` (~línea 201).
- `<!-- Stats -->` y la string `stats_chart_center` (~líneas 228-230).
- `<string name="menu_card_stats">...</string>` (~línea 326).
- `<string name="a11y_card_stats">...</string>` (~línea 339).
- Cualquier otra string que empiece con `stats_`, `total_check_ins_`, `total_favorites_`, `places_visited_`, `no_badges_yet`, `category_chart_*` que solo se usaba en activity_stats.xml o StatsActivity. Si dudas: si después de la limpieza `./gradlew assembleDebug` se queja de string huérfano sin uso, no es error — solo elimina la que ya no esté referenciada.

---

## TAREA 3 — Verificación

Ejecutar **en orden** y resolver cualquier error antes de continuar:

```bash
./gradlew clean
./gradlew assembleDebug
```

Si compila sin errores: ✅ listo.

Si falla, los errores típicos serán:
- **"Unresolved reference: AppDatabase / OfflineManager / SyncWorker / UserStats / FavoriteEntity"** → quedó un import o llamada que el plan no listó. Borrar la línea ofensora.
- **"Unresolved reference: VIEW_STATS / OFFLINE / STATS"** → quedó una referencia al enum/const eliminado. Borrar.
- **String resource not found** → eliminar la referencia en el layout XML correspondiente.

### 2.10 Actualizar `CLAUDE.md`

Al final, actualizar la documentación:
- En la tabla "Project Structure", **borrar** las filas de: `OfflineManager.kt`, `SyncWorker.kt`, `ConnectivityObserver.kt`, `AppDatabase.kt`, `RoomEntities.kt`, `RoomDAOs.kt`.
- En la sección "Tech Stack", **borrar** las menciones de Room, SQLCipher, MPAndroidChart, WorkManager.
- En "Firestore Collections", la tabla queda igual.
- Quitar la línea de Room en "Build" y la mención a kapt/KSP for Room.

---

## NO HACER

- ❌ No tocar la persistencia de Firestore (`PersistenceEnabled = true` en `TrazaGoApplication.kt`). Esa es la capa offline real que se conserva.
- ❌ No borrar `UserProfileRepository.UserStatsData` — es otra clase distinta usada por `ProfileActivity` y NO se va.
- ❌ No tocar `FavoritesManager`/`CheckInManager` más allá de lo indicado. La lógica Firestore se queda.
- ❌ No hacer commit. Solo dejar los cambios staged.
- ❌ No agregar features nuevas, refactors, ni "limpieza extra".

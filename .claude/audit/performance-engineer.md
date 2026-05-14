# Performance Audit — TrazaGo

## Resumen ejecutivo

La app tiene buenos patrones en varias áreas (paginación en Blog, debounce en búsqueda, coroutines para I/O, `DiffUtil` en adapters). Sin embargo existen tres problemas críticos P0: búsqueda global con full-scan de Firestore (~800 lecturas por búsqueda), N+1 queries en Favoritos, y manipulación de Bitmaps en el main thread al cargar marcadores del mapa.

---

## Hallazgos P0 (impacto directo en usuario / OOM / ANR)

### ~~[PERF-001] GlobalSearchActivity — Full-table scan en Firestore (client-side search)~~ ✅
- **Archivo**: `GlobalSearchActivity.kt:112-183`
- ~~`performSearch()` descarga hasta **500 docs de `lugares`**, 200 de `blog_posts`, 100 de `eventos`, y filtra en memoria. Hasta **800 lecturas Firestore por búsqueda**. Escala linealmente con la DB. Riesgo de OOM en gama baja.~~
- **Resuelto**: `performSearch()` usa `AppDatabase.getDatabase().touristSpotDao().searchSpots(query)` (Room) para lugares — 0 lecturas Firestore. Blog y eventos reducidos a `limit(50)` con `async`/`await` paralelo.

### ~~[PERF-002] FavoritesActivity — N+1 sequential Firestore reads~~ ✅
- **Archivo**: `FavoritesActivity.kt:86-97`
- ~~`loadPlaceDetails()` itera sobre favoritos y ejecuta un `db.collection("lugares").document(id).get().await()` **secuencial** por cada ítem. 20 favoritos = 20 roundtrips serializados = 2–6 segundos de spinner.~~
- **Resuelto**: Reemplazado por `.whereIn(FieldPath.documentId(), chunk)` con `chunked(30)`.

### ~~[PERF-003] MapsActivity — Bitmap manipulation en Main thread durante carga de marcadores~~ ✅
- **Archivo**: `MapsActivity.kt:407-418, 967-1006`
- ~~`onResourceReady` y `createCircularBitmapWithBorder`/`createCircularBitmapWithNumber` crean nuevos `Bitmap` con `Canvas` en el **main thread**. Para ~200 marcadores, 200 operaciones de canvas en el UI thread durante el scroll inicial → jank/ANR en gama baja.~~
- **Resuelto**: `onResourceReady` usa `lifecycleScope.launch(Dispatchers.Default)` para crear el bitmap; `withContext(Dispatchers.Main)` solo para `addMarkerToMap()`.

---

## Hallazgos P1 (latencia notable)

### ~~[PERF-004] `clearAllMarkers()` llama `marker.remove()` individualmente — `MapsActivity.kt:816-824`~~ ✅
- ~~Con 200 marcadores → 200 llamadas a Maps API en main thread por cada cambio de filtro. `map.clear()` es órdenes de magnitud más rápido.~~
- **Resuelto**: `clearAllMarkers()` usa `mMap.clear()` con guard `if (::mMap.isInitialized)`.

### ~~[PERF-005] WeatherManager — `URL(url).readText()` sin OkHttp — `WeatherManager.kt:39`~~ ✅
- ~~Sin timeout configurable, sin connection pool, sin caché HTTP. Si OpenWeatherMap tarda, la coroutine queda bloqueada hasta 30s. `MenuActivity` llama `loadWeather()` en cada `onCreate` (incluyendo rotación). OkHttp ya está disponible como dependencia.~~
- **Resuelto**: `WeatherManager` usa `OkHttpClient` con `connectTimeout(10s)` / `readTimeout(15s)` a través de `fetchUrl()`. Instancia compartida en el `object`.

### ~~[PERF-006] EventsActivity — Ordenamiento client-side post-fetch — `EventsActivity.kt:75-79`~~ ✅
- ~~Trae hasta 50 eventos sin `orderBy` y luego `sortWith` en memoria. Firestore puede devolver resultados ya ordenados via índice compuesto (`isFeatured DESC, startDate ASC`).~~
- **Resuelto**: Query usa `orderBy("isFeatured", DESC).orderBy("startDate", ASC)`. Se eliminó `sortWith`. Requiere índice compuesto en Firebase Console.

### ~~[PERF-007] Polyline animator crea nueva `List` por cada frame — `MapsActivity.kt:679-692`~~ ✅
- ~~`ValueAnimator` a 60fps durante 1200ms → ~72 callbacks, cada uno construye nueva `mutableListOf` parcial y asigna a `polyline.points`. GC pressure con objetos de corta vida.~~
- **Resuelto**: `partial` se crea como `ArrayList<LatLng>(fullPath.size)` antes del `ValueAnimator` y se reutiliza vía `partial.clear()` en cada frame.

### ~~[PERF-008] `applicationScope` con `Dispatchers.Main` para Remote Config — `TrazaGoApplication.kt:23,53-55`~~ ✅
- ~~Scope de Application usa `Dispatchers.Main`. Si `fetchAndActivate` falla con backoff, el tiempo de espera ocurre en el startup path. Usar `Dispatchers.IO`.~~
- **Resuelto**: `TrazaGoApplication` ya usa `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.

---

## Hallazgos P2 (optimizaciones de buena práctica)

### ~~[PERF-009] `AnimationUtils.loadAnimation` en cada `onBindViewHolder` — `EventsAdapter.kt:61-62`~~ ✅
- ~~Cargar animación desde recursos en cada bind. Cargar una vez en `onCreateViewHolder`.~~
- **Resuelto**: `fadeAnimation` cargado una vez como campo del `EventViewHolder`.

### ~~[PERF-010] `loadBlogPosts()` se invoca tras cada like — `BlogActivity.kt:301`~~ ✅
- ~~Reinicia la paginación completa por cada like en lugar de actualizar solo el ítem afectado.~~
- **Resuelto**: `toggleLike` usa actualización optimista: `adapter.submitList(adapter.currentList.map { if (it.id == post.id) it.copy(likes = newLikes) else it })`.

### ~~[PERF-011] `SimpleDateFormat` instanciado por llamada en `formatDate` — `BlogPostAdapter.kt:83`~~ ✅
- ~~Nueva instancia por cada ítem de RecyclerView. Mover a `companion object`.~~
- **Resuelto**: `DATE_FORMAT` movido a `companion object` de `BlogPostAdapter`.

### ~~[PERF-012] Dependencias desactualizadas con mejoras de rendimiento — `gradle/libs.versions.toml:6,29`~~ ✅
- ~~`coreKtx 1.10.1`, `material 1.10.0`, `playServicesMaps 18.1.0` — versiones más recientes incluyen optimizaciones de rendering.~~
- **Resuelto**: `coreKtx → 1.13.1`, `appcompat → 1.7.0`, `material → 1.12.0`, `playServicesMaps → 18.2.0`.

---

## Buenas prácticas detectadas

- **Glide con `override(120,120)` y `diskCacheStrategy(DiskCacheStrategy.ALL)`** en marcadores: limita tamaño de bitmap y aprovecha caché.
- **`markerGeneration` como `AtomicInteger`**: previene race conditions al limpiar marcadores.
- **Debounce de 400ms** en búsqueda global.
- **Paginación con cursor** en `BlogActivity` (PAGE_SIZE=15, `startAfter`, scroll listener).
- **`ListAdapter` con `DiffUtil`** en todos los adapters revisados.
- **Firebase Offline Persistence** habilitada.
- **WorkManager con `ExistingPeriodicWorkPolicy.KEEP`**: evita trabajo duplicado.
- **Transacciones Firestore** en submit/update de reviews.

---

## Estimación de impacto por área

| Área | Severidad | Estado |
|---|---|---|
| GlobalSearch full-scan (PERF-001) | P0 | ✅ Resuelto |
| FavoritesActivity N+1 (PERF-002) | P0 | ✅ Resuelto |
| Bitmap canvas en main thread (PERF-003) | P0 | ✅ Resuelto |
| `marker.remove()` individual (PERF-004) | P1 | ✅ Resuelto |
| WeatherManager sin timeout (PERF-005) | P1 | ✅ Resuelto |
| Sort client-side eventos (PERF-006) | P1 | ✅ Resuelto |
| Polyline animator allocations (PERF-007) | P1 | ✅ Resuelto |

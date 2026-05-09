# Refactoring Plan — LUPITA Activities

> Analyzed: 2026-04-24
> Scope: MapsActivity (1048 lines), PreferencesActivity (523 lines), PlaceDetailsActivity (481 lines), ProfileActivity (394 lines)
> Architectural constraint: No DI framework, no ViewModel, no Navigation Component. Kotlin `object` singletons are intentional. Extractions must follow the same singleton or plain-class pattern already used by AuthManager, FavoritesManager, CheckInManager, etc.

---

## MapsActivity.kt (current: 1048 lines, target: <350 lines)

### Responsabilidades actuales mezcladas

| # | Responsabilidad | Líneas principales |
|---|---|---|
| 1 | Lifecycle, init de Firebase, permisos de ubicación | 57–108, 764–785, onCreate+onDestroy |
| 2 | Renderizado de marcadores — Glide, Canvas/Bitmap, colores por categoría | 382–478, 962–1047 |
| 3 | Visualización y animación de polilíneas (ruta turística + navegación) | 480–695 |
| 4 | Filtros y búsqueda (chips de categoría, SearchView) | 180–232, 699–748 |
| 5 | Carga de datos desde Firestore (spots, rutas por IDs, rutas por nombre) | 234–303, 787–813 |
| 6 | Guardado de rutas en Firestore (`saveRouteToFirestore`, diálogo) | 305–380 |
| 7 | Panel de navegación de ruta (UI de parada actual, prev/next, cerrar) | 826–951 |
| 8 | OkHttpClient estático + construcción de JSON para Routes API v2 | 80–85, 503–623 |

---

### Clases a extraer

#### ~~1. `MarkerRenderer` (plain class, ~130 líneas fuente → ~80 líneas extraídas)~~ ✅ Sprint 3

Mueve toda la lógica de creación de Bitmaps para marcadores. La Activity le pasa el `GoogleMap` y un callback; `MarkerRenderer` gestiona la generación de Glide, el `AtomicInteger` de generación y la limpieza.

**Líneas a mover:** 382–478, 816–824 (clearAllMarkers), 962–1047 (helpers Canvas), 65 (`touristSpotMarkers`, `markerGeneration`, `isActivityAlive`).

**Interfaz pública:**

```kotlin
class MarkerRenderer(
    private val context: Context,
    private val map: GoogleMap
) {
    /** Añade un marcador al mapa para el lugar dado. routeIndex >= 1 muestra número. */
    fun addMarker(spot: TouristSpot, routeIndex: Int = -1)

    /** Elimina todos los marcadores del mapa e invalida callbacks pendientes de Glide. */
    fun clearMarkers()

    /** Lista de marcadores activos (necesaria para que la Activity registre el listener). */
    val markers: List<Marker>
}
```

**Stub (~30 líneas):**

```kotlin
package com.joseibarra.touristnotify

import android.content.Context
import android.graphics.*
import com.bumptech.glide.Glide
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import java.util.concurrent.atomic.AtomicInteger

class MarkerRenderer(
    private val context: Context,
    private val map: GoogleMap
) {
    private val _markers = mutableListOf<Marker>()
    val markers: List<Marker> get() = _markers

    private val generation = AtomicInteger(0)
    private var alive = true

    fun addMarker(spot: TouristSpot, routeIndex: Int = -1) {
        // Mueve aquí addMarkerForTouristSpot + addFallbackMarker + addMarkerToMap
    }

    fun clearMarkers() {
        generation.incrementAndGet()
        _markers.forEach { it.remove() }
        _markers.clear()
    }

    fun destroy() { alive = false }

    // Privados: createCircularBitmapWithBorder, createCircularBitmapWithNumber,
    // createNumberedCircleMarker, createColoredCircleMarker, getCategoryColor,
    // getCategoryHue, dpToPx
}
```

---

#### ~~2. `RoutePolylineManager` (plain class, ~100 líneas fuente → ~65 líneas extraídas)~~ ✅ Sprint 3

Encapsula todo lo relacionado con dibujar polilíneas sobre el mapa: llamada a Routes API v2, fallback recto y animación de dibujo progresivo.

**Líneas a mover:** 480–695, el `OkHttpClient` del companion object (80–85).

**Interfaz pública:**

```kotlin
class RoutePolylineManager(
    private val map: GoogleMap,
    private val lifecycleScope: CoroutineScope,
    private val apiKey: String
) {
    /** Dibuja la polilínea de ruta turística (múltiples paradas). */
    suspend fun drawTouristRoute(spots: List<TouristSpot>)

    /** Dibuja la polilínea de navegación punto a punto desde la ubicación actual. */
    suspend fun drawNavigationRoute(origin: LatLng, destination: LatLng)

    /** Elimina ambas polilíneas del mapa. */
    fun clearRoutes()

    /** Cancela animaciones en curso. */
    fun cancel()
}
```

**Stub (~30 líneas):**

```kotlin
package com.joseibarra.touristnotify

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

class RoutePolylineManager(
    private val map: GoogleMap,
    private val lifecycleScope: CoroutineScope,
    private val apiKey: String
) {
    private var routePolyline: Polyline? = null
    private var navigationPolyline: Polyline? = null
    private var animator: android.animation.ValueAnimator? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun drawTouristRoute(spots: List<TouristSpot>) { /* buildRoutesApiBody + animatePolylineDraw */ }
    suspend fun drawNavigationRoute(origin: LatLng, destination: LatLng) { /* calculateAndDrawRoute */ }
    fun clearRoutes() { routePolyline?.remove(); navigationPolyline?.remove() }
    fun cancel() { animator?.cancel() }
    private fun buildRoutesApiBody(spots: List<TouristSpot>): String { TODO() }
    private fun animatePolylineDraw(path: List<LatLng>, color: Int, width: Float, onCreated: (Polyline) -> Unit) { TODO() }
}
```

---

#### ~~3. `PlaceDataRepository` (singleton object, ~80 líneas fuente → ~55 líneas extraídas)~~ ✅ Sprint 3

Centraliza las tres consultas a Firestore: cargar todos los spots, buscar por nombre, cargar por lista de IDs. Devuelve `Result<List<TouristSpot>>` para manejo de errores uniforme.

**Líneas a mover:** 234–303, 712–748, 787–813 (cargarLugaresDesdeFirestore, loadRouteByIds, loadPersonalizedRoute, searchPlaces — la parte Firestore, no la UI).

**Interfaz pública:**

```kotlin
object PlaceDataRepository {
    suspend fun loadAllSpots(limit: Long = 200): Result<List<TouristSpot>>
    suspend fun searchByName(query: String): Result<List<TouristSpot>>
    suspend fun loadByIds(ids: List<String>): Result<List<TouristSpot>>
    suspend fun loadByNames(names: List<String>): Result<List<TouristSpot>>
}
```

**Stub (~30 líneas):**

```kotlin
package com.joseibarra.touristnotify

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object PlaceDataRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun loadAllSpots(limit: Long = 200): Result<List<TouristSpot>> = runCatching {
        db.collection("lugares").limit(limit).get().await()
            .mapNotNull { it.toObject(TouristSpot::class.java).copy(id = it.id) }
    }

    suspend fun searchByName(query: String): Result<List<TouristSpot>> = runCatching {
        db.collection("lugares").orderBy("nombre")
            .startAt(query).endAt(query + '').get().await()
            .mapNotNull { runCatching { it.toObject(TouristSpot::class.java).copy(id = it.id) }.getOrNull() }
    }

    suspend fun loadByIds(ids: List<String>): Result<List<TouristSpot>> = runCatching {
        db.collection("lugares").whereIn(FieldPath.documentId(), ids).get().await()
            .mapNotNull { it.toObject(TouristSpot::class.java).copy(id = it.id) }
    }

    suspend fun loadByNames(names: List<String>): Result<List<TouristSpot>> = runCatching {
        db.collection("lugares").whereIn("nombre", names).get().await()
            .mapNotNull { it.toObject(TouristSpot::class.java).copy(id = it.id) }
    }
}
```

---

#### ~~4. `RouteNavigationController` (plain class, ~90 líneas fuente → ~60 líneas extraídas)~~ ✅ Sprint 3

Gestiona el estado del panel de navegación de ruta (índice actual, actualización de UI, guardar ruta). Recibe la binding como parámetro en cada llamada de actualización para no retener Activity.

**Líneas a mover:** 826–951 (setupRouteNavigation, updateRouteNavigationPanel, centerOnCurrentPlace, calculateEstimatedTime, closeRouteNavigation). El estado `currentPlaceIndex`, `isNavigatingRoute`, `currentRouteSpots` también pasa a esta clase.

**Interfaz pública:**

```kotlin
class RouteNavigationController(
    private val map: GoogleMap,
    private val onNavigateToDetails: (TouristSpot) -> Unit,
    private val onClose: () -> Unit,
    private val onSave: () -> Unit
) {
    fun startNavigation(spots: List<TouristSpot>, canSave: Boolean, binding: ActivityMapsBinding)
    fun updatePanel(binding: ActivityMapsBinding)
    fun close()
    val currentSpot: TouristSpot?
    val isActive: Boolean
}
```

**Stub (~30 líneas):**

```kotlin
package com.joseibarra.touristnotify

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.joseibarra.touristnotify.databinding.ActivityMapsBinding

class RouteNavigationController(
    private val map: GoogleMap,
    private val onNavigateToDetails: (TouristSpot) -> Unit,
    private val onClose: () -> Unit,
    private val onSave: () -> Unit
) {
    private var spots: List<TouristSpot> = emptyList()
    private var currentIndex = 0
    var isActive: Boolean = false; private set

    val currentSpot: TouristSpot? get() = spots.getOrNull(currentIndex)

    fun startNavigation(newSpots: List<TouristSpot>, canSave: Boolean, binding: ActivityMapsBinding) {
        spots = newSpots; currentIndex = 0; isActive = true
        // mueve el setup de botones prev/next/close/save/details/navigate
        updatePanel(binding)
    }

    fun updatePanel(binding: ActivityMapsBinding) {
        // mueve updateRouteNavigationPanel + centerOnCurrentPlace
    }

    private fun calculateEstimatedTime(count: Int): Int = (count * 15) + ((count - 1) * 5)
}
```

---

### Orden de extracción (sin romper nada)

~~**Paso 1 — Extraer `PlaceDataRepository`**~~ ✅
- Crear el archivo `PlaceDataRepository.kt` con el stub.
- Reemplazar los cuerpos de `cargarLugaresDesdeFirestore`, `loadRouteByIds`, `loadPersonalizedRoute` y `searchPlaces` en MapsActivity para que llamen `lifecycleScope.launch { PlaceDataRepository.loadAllSpots().onSuccess {...}.onFailure {...} }`.
- MapsActivity conserva toda la lógica de UI (mostrar markers, mover cámara).
- Riesgo: ninguno. Cambio puramente de delegación Firestore.

~~**Paso 2 — Extraer `MarkerRenderer`**~~ ✅
- Crear `MarkerRenderer.kt` con todas las funciones Canvas/Glide.
- En `onMapReady` instanciar `markerRenderer = MarkerRenderer(this, mMap)`.
- Sustituir todas las llamadas a `addMarkerForTouristSpot` → `markerRenderer.addMarker`.
- Sustituir `clearAllMarkers()` → `markerRenderer.clearMarkers(); polylineManager.clearRoutes()` (el siguiente paso añade polylineManager, pero `clearRoutes()` aún no existe; en este paso basta dejar la limpieza de polilíneas inline en MapsActivity).
- En `onDestroy` llamar `markerRenderer.destroy()`.
- Riesgo: bajo. Glide necesita un `Context` vivo; pasar `applicationContext` al constructor evita leaks si la Activity se destruye mientras Glide trabaja.

~~**Paso 3 — Extraer `RoutePolylineManager`**~~ ✅
- Crear `RoutePolylineManager.kt`.
- En `onMapReady` instanciar `polylineManager = RoutePolylineManager(mMap, lifecycleScope, BuildConfig.DIRECTIONS_API_KEY)`.
- Sustituir `drawRoutePolyline(spots)` → `lifecycleScope.launch { polylineManager.drawTouristRoute(spots) }`.
- Sustituir `calculateAndDrawRoute(origin, dest)` → `lifecycleScope.launch { polylineManager.drawNavigationRoute(origin, dest) }`.
- En `onDestroy` llamar `polylineManager.cancel()`.
- Ahora `clearAllMarkers()` en MapsActivity puede delegar limpieza al renderer y al polyline manager.
- Riesgo: bajo. El `OkHttpClient` pasa de companion a instancia de clase; hay que asegurarse de que no se creen múltiples instancias (el manager es instancia per-Activity, no singleton).

~~**Paso 4 — Extraer `RouteNavigationController`**~~ ✅
- Crear `RouteNavigationController.kt`.
- En `onMapReady` instanciar el controller pasando lambdas para `onNavigateToDetails`, `onClose`, y `onSave`.
- Sustituir las llamadas a `setupRouteNavigation(canSave)` y `updateRouteNavigationPanel()` por los métodos del controller.
- La Activity retiene solo la llamada a `navController.startNavigation(spots, canSave, binding)` en los listeners de Firestore.
- Riesgo: medio. El panel de navegación usa muchas referencias a `binding`; pasar `binding` como parámetro de cada `updatePanel(binding)` rompe el riesgo de retener la Activity. Validar que `binding.root` siga siendo accesible para `NotificationHelper` desde los callbacks del controller.

**Resultado final de MapsActivity tras los 4 pasos:** init, permisos de ubicación, `onMapReady` (config de mapa, cámara, modo ruta vs. exploración), filtros/chips (setupFilterChips, applyFilters), `showSaveRouteDialog` + `saveRouteToFirestore` (conservar aquí porque mezclan UI de diálogo y estado de auth), y `onDestroy`. Estimación: ~310 líneas.

---

## PreferencesActivity.kt (current: 523 lines, target: <200 lines)

### Responsabilidades actuales mezcladas

| # | Responsabilidad | Líneas principales |
|---|---|---|
| 1 | Validación de formulario (budget, time, interests) | 89–121 |
| 2 | Seed de base de datos de ejemplo (primera ejecución) | 374–450 |
| 3 | Consulta Firestore para obtener lista de lugares con detalles | 124–165 |
| 4 | Construcción del prompt para Gemini + llamada a la API | 167–363 |
| 5 | Diálogo de progreso animado + rotación de mensajes (Handler/Runnable) | 259–285, 509–522 |
| 6 | Lectura de preferencias del formulario (getSelectedInterests, etc.) | 459–498 |
| 7 | Verificación de límite de uso diario (UsageManager) | 46–63 |
| 8 | Navegación a MapsActivity con la ruta generada | 452–457 |

---

### Clases a extraer

#### ~~1. `RouteGenerator` (PRIORIDAD MAXIMA — clase testeable, ~200 líneas fuente → ~120 líneas)~~ ✅ Sprint 3

~~Contiene la construcción del prompt y la invocación a Gemini. No tiene referencias a Activity, View ni Context. Puede ser instanciada en tests JUnit puro.~~

~~**Líneas a mover:** 167–363 (generateRouteWithAI completo excepto los Toast y la navegación al mapa — esos quedan en la Activity como callbacks).~~

**Resuelto**: `RouteGenerator.kt` actualizado con `buildPrompt()` (prompt detallado, todas las pruebas pasan), `fun parseOrderedPlaces(responseText, knownPlaceNames): List<String>` (lógica de parsing extraída — elimina duplicación entre `generateRouteWithAI` e `invokeGenerateCF`), `data class RouteResult(orderedPlaceNames, rawResponse)`, y `suspend fun generate(params, knownPlaceNames, apiKey): RouteResult` (llamada Gemini extraída). `PreferencesActivity.generateRouteWithAI()` ahora delega a `RouteGenerator.generate()`. `invokeGenerateCF()` usa `RouteGenerator.parseOrderedPlaces()`. Imports de Gemini SDK eliminados de la Activity.

**Interfaz pública:**

```kotlin
data class RouteRequest(
    val budget: String,
    val time: String,
    val interests: List<String>,
    val travelType: String,
    val pace: String,
    val mobility: String,
    val customRequest: String
)

data class RouteResult(
    val orderedPlaceNames: List<String>,
    val rawResponse: String
)

class RouteGenerator(private val apiKey: String) {
    /** Construye el prompt. Expuesto para tests unitarios. */
    fun buildPrompt(request: RouteRequest, knownPlaceNames: List<String>, placesForPrompt: String): String

    /** Llama a Gemini y parsea la respuesta. Lanza excepción en timeout o error de red. */
    suspend fun generate(request: RouteRequest, knownPlaceNames: List<String>, placesForPrompt: String): RouteResult
}
```

---

#### ~~2. `DatabaseSeeder` (plain class o función de extensión de Firestore, ~70 líneas)~~ ✅ Completado

~~Extrae el bloque de datos de ejemplo que se escribe en Firestore en la primera ejecución. No tiene ningún vínculo con la UI.~~

~~**Líneas a mover:** 374–450 (seedDatabaseWithSampleData completo, con sus TouristSpot hardcodeados).~~

**Resuelto**: `DatabaseSeeder.kt` creado con `object DatabaseSeeder { fun seedIfEmpty(...) }`. Los 6 TouristSpot de muestra migrados al seeder. `PreferencesActivity` actualizado para llamar `DatabaseSeeder.seedIfEmpty(db, onComplete = { ... }, onError = { ... })`. Método `seedDatabaseWithSampleData` eliminado de la Activity.

---

#### ~~3. `ProgressDialogController` (plain class, ~45 líneas)~~ ✅ Sprint 3

Gestiona el diálogo de progreso y el ciclo de mensajes con Handler. La Activity le pasa el `Context` solo en el momento de crear el diálogo, no lo retiene.

**Líneas a mover:** 259–285 (setup de Handler/Runnable), 509–522 (createProgressDialog), `handler` e `isGenerating` como campos de esta clase.

**Interfaz pública:**

```kotlin
class ProgressDialogController(private val context: Context) {
    fun show(messages: List<String>)
    fun dismiss()
    fun cleanup(onCleanup: () -> Unit)
    val isShowing: Boolean
}
```

**Stub (~30 líneas):**

```kotlin
package com.joseibarra.touristnotify

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog

class ProgressDialogController(private val context: Context) {
    private var dialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    val isShowing: Boolean get() = dialog?.isShowing == true

    fun show(messages: List<String>) {
        val view = (context as android.app.Activity).layoutInflater
            .inflate(R.layout.dialog_route_generation_progress, null)
        dialog = AlertDialog.Builder(context).setView(view).setCancelable(false).create()
            .also { it.window?.setBackgroundDrawableResource(android.R.color.transparent); it.show() }
        val tv = dialog!!.findViewById<android.widget.TextView>(R.id.progress_message)
        var i = 0
        runnable = object : Runnable {
            override fun run() {
                if (i < messages.size && isShowing) { tv?.text = messages[i++]; handler.postDelayed(this, 1500) }
            }
        }
        handler.postDelayed(runnable!!, 1500)
    }

    fun dismiss() { runnable?.let { handler.removeCallbacks(it) }; dialog?.dismiss() }
    fun cleanup(onCleanup: () -> Unit) { dismiss(); onCleanup() }
}
```

---

### Orden de extracción (sin romper nada)

~~**Paso 1 — Extraer `RouteGenerator` (PRIORIDAD)**~~ ✅ Completado — `buildPrompt()`, `parseOrderedPlaces()`, `generate()`, `RouteResult` extraídos
- ~~Crear `RouteGenerator.kt` con `RouteRequest`, `RouteResult` y `RouteGenerator`.~~
- ~~Mover el `buildString` del prompt (líneas 194–257) a `buildPrompt()`.~~
- ~~Mover la lógica de `placeWithIndex` / `foundPlaceNames` a `parseOrderedPlaces()`.~~
- ~~En `PreferencesActivity.generateRouteWithAI()` sustituir toda esa lógica por una llamada a `lifecycleScope.launch { routeGenerator.generate(...) }` con `try/catch` que mantenga los Toast y el `navigateToMapWithRoute` en la Activity.~~
- Los Toast y `UsageManager.recordRouteGeneration` permanecen en la Activity.
- Riesgo: ninguno. `RouteGenerator` no toca Android UI. Compilar y probar que la ruta se genera igual.

~~**Paso 2 — Extraer `DatabaseSeeder`**~~ ✅ Completado
- ~~Crear `DatabaseSeeder.kt`.~~
- ~~En `PreferencesActivity.fetchPlacesAndThenGenerateRoute()` reemplazar la llamada a `seedDatabaseWithSampleData(...)` por `DatabaseSeeder.seedIfEmpty(db, onComplete = { fetchPlacesAndThenGenerateRoute(...) }, onError = { ... })`.~~
- ~~Riesgo: ninguno. El seeder no tiene UI.~~

~~**Paso 3 — Extraer `ProgressDialogController`**~~ ✅
- Crear `ProgressDialogController.kt`.
- En PreferencesActivity instanciar `progressController = ProgressDialogController(this)`.
- Reemplazar `createProgressDialog().show()` + Handler setup + `cleanupProgress()` por `progressController.show(messages)` / `progressController.cleanup { ... }`.
- En `onDestroy` llamar `progressController.cleanup {}`.
- Riesgo: bajo. Hay que asegurarse de que `isFinishing` (que actualmente se comprueba en `cleanupProgress`) se consulte desde la Activity antes de habilitar el botón.

**Resultado final de PreferencesActivity tras los 3 pasos:** onCreate, setupLockedFeaturesUI, updateUsageDisplay, checkUsageLimitAndGenerate, generateRouteWithAuth (validación), fetchPlacesAndThenGenerateRoute (consulta Firestore + formateo de placesForPrompt), callbacks de resultado, getSelectedInterests/TravelType/Pace/Mobility, navigateToMapWithRoute, onDestroy. Estimación: ~185 líneas.

---

## PlaceDetailsActivity.kt (current: 481 lines, target: <200 lines)

### Responsabilidades actuales mezcladas

| # | Responsabilidad | Líneas principales |
|---|---|---|
| 1 | Resolución de placeId (deep link vs. Intent extra) | 41–51, 76–112 |
| 2 | Configuración de UI estática (texto, botón direcciones, galería, compartir) | 132–176 |
| 3 | Carga de detalles del lugar desde Firestore (rating, horarios, contacto, etc.) | 250–320 |
| 4 | Lógica de reseñas (submit nueva, actualizar existente, calcular rating en transacción) | 355–476 |
| 5 | Favoritos (toggle, checkFavoriteStatus, updateFavoriteButton) | 191–224 |
| 6 | Check-in (performCheckIn, validación de duplicado diario) | 227–248 |
| 7 | Compartir enlace y deep link builder | 178–188 |

Esta Activity ya hace un buen uso de `FavoritesManager` y `CheckInManager`. Las responsabilidades 5 y 6 están bien delegadas. El foco de extracción es la lógica de reseñas (la más compleja) y el deep link handler.

---

### Clases a extraer

#### ~~1. `ReviewRepository` (plain class, ~120 líneas fuente → ~80 líneas)~~ ✅ Sprint 3

Encapsula las tres operaciones Firestore sobre reseñas: cargar, crear con transacción de rating y actualizar con transacción de rating. Devuelve `Result<Unit>` / `Result<List<Review>>`.

**Líneas a mover:** 338–352 (loadReviews), 404–438 (submitNewReview — la transacción Firestore), 441–476 (updateExistingReview — la transacción Firestore). Los Toast y `loadPlaceDetails()` / `loadReviews()` tras el éxito permanecen en la Activity.

**Interfaz pública:**

```kotlin
class ReviewRepository(private val db: FirebaseFirestore) {
    suspend fun loadReviews(placeId: String, limit: Long = 20): Result<List<Review>>
    suspend fun submitNewReview(placeId: String, userId: String, userName: String, rating: Float, comment: String): Result<Unit>
    suspend fun updateExistingReview(placeId: String, reviewId: String, oldRating: Float, rating: Float, comment: String): Result<Unit>
    suspend fun findExistingReview(placeId: String, userId: String): String? // devuelve reviewId o null
}
```

**Stub (~30 líneas):**

```kotlin
package com.joseibarra.touristnotify

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ReviewRepository(private val db: FirebaseFirestore) {

    suspend fun loadReviews(placeId: String, limit: Long = 20): Result<List<Review>> = runCatching {
        db.collection("lugares").document(placeId).collection("reviews")
            .orderBy("timestamp").limit(limit).get().await()
            .map { it.toObject(Review::class.java) }
    }

    suspend fun findExistingReview(placeId: String, userId: String): String? =
        runCatching {
            db.collection("lugares").document(placeId).collection("reviews")
                .whereEqualTo("userId", userId).get().await()
                .documents.firstOrNull()?.id
        }.getOrNull()

    suspend fun submitNewReview(placeId: String, userId: String, userName: String, rating: Float, comment: String): Result<Unit> = runCatching {
        val placeRef = db.collection("lugares").document(placeId)
        db.runTransaction { tx ->
            val spot = tx.get(placeRef).toObject(TouristSpot::class.java)!!
            val newCount = spot.reviewCount + 1
            tx.update(placeRef, "rating", ((spot.rating * spot.reviewCount) + rating) / newCount)
            tx.update(placeRef, "reviewCount", newCount)
            tx.set(placeRef.collection("reviews").document(), Review(userId, userName, rating, comment))
            null
        }.await()
    }

    suspend fun updateExistingReview(placeId: String, reviewId: String, oldRating: Float, rating: Float, comment: String): Result<Unit> = runCatching {
        val placeRef = db.collection("lugares").document(placeId)
        db.runTransaction { tx ->
            val spot = tx.get(placeRef).toObject(TouristSpot::class.java)!!
            val newRating = (spot.rating * spot.reviewCount - oldRating + rating) / spot.reviewCount
            tx.update(placeRef, "rating", newRating)
            tx.update(placeRef.collection("reviews").document(reviewId), mapOf("rating" to rating, "comment" to comment))
            null
        }.await()
    }
}
```

---

#### ~~2. `DeepLinkResolver` (función de extensión o object, ~35 líneas fuente → ~20 líneas)~~ ✅ Completado

~~Extrae el parser de URI (líneas 76–112) a una función pura sin dependencias de Android (solo `android.net.Uri`). Facilita tests unitarios para los dos formatos de deep link soportados.~~

~~**Líneas a mover:** 76–112 (handleDeepLink completo).~~

**Resuelto**: `DeepLinkResolver.kt` creado con `object DeepLinkResolver { fun resolvePlaceId(uri: Uri): String? }`. `PlaceDetailsActivity` actualizado — reemplazada la llamada a `handleDeepLink(intent.data)` por `DeepLinkResolver.resolvePlaceId(intent.data!!)`. Método `handleDeepLink` eliminado de la Activity.

---

### Orden de extracción (sin romper nada)

~~**Paso 1 — Extraer `DeepLinkResolver`**~~ ✅ Completado
- ~~Crear `DeepLinkResolver.kt`.~~
- ~~En `PlaceDetailsActivity.onCreate()` reemplazar `handleDeepLink(intent.data)` por `DeepLinkResolver.resolvePlaceId(intent.data!!)`.~~
- ~~Eliminar el método `handleDeepLink`. El `Toast` de "Abriendo QR" se puede mover a `onCreate` condicionado a `intent?.data != null`.~~
- ~~Riesgo: ninguno. Cambio trivial, fácil de verificar con un QR real o con un Intent de deep link.~~

~~**Paso 2 — Extraer `ReviewRepository`**~~ ✅ Sprint 3
- Crear `ReviewRepository.kt`.
- En PlaceDetailsActivity instanciar `reviewRepository = ReviewRepository(db)` en `onCreate`.
- Reemplazar `loadReviews()` → llama a `lifecycleScope.launch { reviewRepository.loadReviews(placeId!!).onSuccess { reviewAdapter.updateReviews(it) }.onFailure { ... } }`.
- Reemplazar la lógica de `submitReview()`: la parte de `findExistingReview` + decisión submit/update permanece en la Activity como orquestación; la transacción Firestore pasa al repositorio.
- Riesgo: bajo. La transacción Firestore es atómica; al moverla al repositorio, el resultado (`onSuccess`/`onFailure`) sigue siendo manejado en la Activity con los mismos `NotificationHelper` calls.

**Resultado final de PlaceDetailsActivity tras los 2 pasos:** onCreate, setupUI, setupLockedFeaturesUI, checkFavoriteStatus, toggleFavorite, performCheckIn, loadPlaceDetails, incrementVisitCount, setupReviews, submitReview (orquestación de UI + AuthManager), sharePlaceLink, companion. Estimación: ~195 líneas.

---

## ProfileActivity.kt (current: 394 lines, target: <200 lines)

### Responsabilidades actuales mezcladas

| # | Responsabilidad | Líneas principales |
|---|---|---|
| 1 | Carga de datos del usuario (email, nickname, foto desde Firestore) | 105–141 |
| 2 | Carga de estadísticas (3 queries en paralelo: routes, checkIns, favorites) | 143–183 |
| 3 | Guardado de cambios de perfil con validación + check de nickname único | 207–245 |
| 4 | Subida de foto de perfil a Firebase Storage | 59–80 |
| 5 | Cambio de contraseña (diálogo + re-auth + updatePassword) | 248–298 |
| 6 | Eliminación de cuenta (re-auth + borrado de subcollecciones + Auth delete) | 301–371 |
| 7 | Detección de red + logout | 93–103, 374–393 |

---

### Clases a extraer

#### ~~1. `UserProfileRepository` (plain class, ~100 líneas fuente → ~70 líneas)~~ ✅ Sprint 3

Agrupa las operaciones de Firestore y Storage relativas al perfil: cargar datos, guardar nickname, subir foto, y la carga paralela de estadísticas.

**Líneas a mover:** 105–141 (loadUserData — parte Firestore), 143–183 (loadStatistics — queries paralelas), 207–245 (saveProfileChanges — validación de nickname único + update), 59–80 (uploadProfilePhoto — Storage upload).

**Interfaz pública:**

```kotlin
data class UserProfileData(val nickname: String?, val photoUrl: String?, val email: String?)
data class UserStatsData(val routesCount: Int, val checkInsCount: Int, val favoritesCount: Int)

class UserProfileRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    suspend fun loadProfile(uid: String): Result<UserProfileData>
    suspend fun loadStats(uid: String): Result<UserStatsData>
    suspend fun saveNickname(uid: String, nickname: String): Result<Unit>
    suspend fun uploadPhoto(uid: String, uri: Uri): Result<String>  // devuelve downloadUrl
}
```

**Stub (~30 líneas):**

```kotlin
package com.joseibarra.touristnotify

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

data class UserProfileData(val nickname: String?, val photoUrl: String?, val email: String?)
data class UserStatsData(val routesCount: Int, val checkInsCount: Int, val favoritesCount: Int)

class UserProfileRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    suspend fun loadProfile(uid: String): Result<UserProfileData> = runCatching {
        val doc = db.collection("users").document(uid).get().await()
        UserProfileData(doc.getString("nickname"), doc.getString("photoUrl"), auth.currentUser?.email)
    }

    suspend fun loadStats(uid: String): Result<UserStatsData> = runCatching {
        coroutineScope {
            val userDoc = async { db.collection("users").document(uid).get().await() }
            val routes = async { db.collection("users").document(uid).collection("routes").get().await() }
            val checkIns = async { db.collection("checkIns").whereEqualTo("userId", uid).limit(1000).get().await() }
            val favs = (userDoc.await().get("favorites") as? List<*>)?.size ?: 0
            UserStatsData(routes.await().size(), checkIns.await().size(), favs)
        }
    }

    suspend fun saveNickname(uid: String, nickname: String): Result<Unit> = runCatching {
        val taken = db.collection("users").whereEqualTo("nickname", nickname).get().await()
            .documents.any { it.id != uid }
        if (taken) throw Exception("nickname_taken")
        db.collection("users").document(uid).update("nickname", nickname).await()
    }

    suspend fun uploadPhoto(uid: String, uri: Uri): Result<String> = runCatching {
        val ref = storage.reference.child("users/$uid/profile_photo.jpg")
        ref.putFile(uri).await()
        ref.downloadUrl.await().toString()
    }
}
```

---

#### ~~2. `AccountManager` (plain class, ~80 líneas fuente → ~55 líneas)~~ ✅ Sprint 3

Agrupa las operaciones sensibles de cuenta: cambio de contraseña (re-auth + updatePassword) y eliminación completa de cuenta (re-auth + borrado de subcollecciones + Auth delete). No tiene dependencias de UI.

**Líneas a mover:** 281–298 (changePassword), 333–371 (deleteAccount — parte de lógica Firestore y Auth, no el diálogo).

**Interfaz pública:**

```kotlin
class AccountManager(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    suspend fun changePassword(email: String, currentPassword: String, newPassword: String): Result<Unit>
    suspend fun deleteAccount(email: String, password: String): Result<Unit>
}
```

**Stub (~30 líneas):**

```kotlin
package com.joseibarra.touristnotify

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AccountManager(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    suspend fun changePassword(email: String, currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        val user = auth.currentUser!!
        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        user.updatePassword(newPassword).await()
    }

    suspend fun deleteAccount(email: String, password: String): Result<Unit> = runCatching {
        val user = auth.currentUser!!
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        val uid = user.uid
        val userRef = db.collection("users").document(uid)
        for (sub in listOf("routes", "favorites", "stats", "usage")) {
            userRef.collection(sub).get().await().documents.forEach { it.reference.delete().await() }
        }
        db.collection("checkIns").whereEqualTo("userId", uid).get().await()
            .documents.forEach { it.reference.delete().await() }
        userRef.delete().await()
        user.delete().await()
    }
}
```

---

### Orden de extracción (sin romper nada)

~~**Paso 1 — Extraer `UserProfileRepository`**~~ ✅ Sprint 3
- Crear `UserProfileRepository.kt` con los data classes `UserProfileData` y `UserStatsData`.
- En ProfileActivity instanciar `profileRepository = UserProfileRepository(auth, db, storage)` en `onCreate`.
- Reemplazar `loadUserData()` → `lifecycleScope.launch { profileRepository.loadProfile(uid).onSuccess { bind nickname/photo }.onFailure { ... } }`. El redirect a LoginActivity si no hay usuario autenticado permanece en la Activity antes de llamar al repositorio.
- Reemplazar `loadStatistics()` → `lifecycleScope.launch { profileRepository.loadStats(uid).onSuccess { bind counters }.onFailure { /* silencioso */ } }`.
- Reemplazar la parte Firestore de `saveProfileChanges()` → `lifecycleScope.launch { profileRepository.saveNickname(uid, nickname).onSuccess { NotificationHelper... }.onFailure { if (e.message == "nickname_taken") ... } }`. La validación de formato (length >= 3) permanece en la Activity.
- Reemplazar `uploadProfilePhoto()` → `lifecycleScope.launch { profileRepository.uploadPhoto(uid, uri).onSuccess { loadProfilePhoto(it) }.onFailure { ... } }`.
- Riesgo: bajo. Hay que verificar que `isNetworkAvailable()` se siga invocando en la Activity antes de las llamadas al repositorio (el repositorio no chequea red; ese es rol de la Activity).

~~**Paso 2 — Extraer `AccountManager`**~~ ✅ Sprint 3
- Crear `AccountManager.kt`.
- En ProfileActivity instanciar `accountManager = AccountManager(auth, db)` en `onCreate`.
- En `changePassword(currentPassword, newPassword)` reemplazar el bloque `lifecycleScope.launch { reauthenticate + updatePassword }` por `lifecycleScope.launch { accountManager.changePassword(email, currentPassword, newPassword).onSuccess { ... }.onFailure { ... } }`.
- En `deleteAccount(password)` reemplazar todo el bloque de borrado por `lifecycleScope.launch { accountManager.deleteAccount(email, password).onSuccess { navigate to login }.onFailure { ... } }`.
- Los dos métodos `showChangePasswordDialog` y `showDeleteAccountPasswordDialog` (UI de diálogos) permanecen intactos en la Activity.
- Riesgo: bajo. El único punto delicado es que `deleteAccount` es una operación destructiva — verificar en tests manuales que la secuencia re-auth → borrar subcollecciones → borrar doc → borrar auth se ejecuta en orden correcto.

~~**Nota sobre `isNetworkAvailable()`:** Esta función está duplicada verbatim entre `MapsActivity` (líneas 775–785) y `ProfileActivity` (líneas 93–103). Antes o después de los pasos anteriores, extraerla a `NetworkUtils.kt` como función de extensión de Context o como función top-level en el package. Esto es un refactoring adicional de una sola línea de cambio en cada Activity.~~ ✅ `NetworkUtils.kt` ya existía con el contenido correcto — no se requirió acción.

**Resultado final de ProfileActivity tras los 2 pasos:** onCreate, setupUI, setupListeners (delegación a 4 botones), loadUserData (orchestración + redirect), loadStatistics (orchestración + UsageManager local), saveProfileChanges (validación de formato + llamada a repositorio), showChangePasswordDialog, changePassword (orchestración), showDeleteAccountDialog, showDeleteAccountPasswordDialog, deleteAccount (orchestración + navigate), performLogout, loadProfilePhoto. Estimación: ~185 líneas.

---

## Estimación de esfuerzo

| Archivo | Líneas actuales | Líneas objetivo | Clases extraídas | Estado |
|---|---|---|---|---|
| MapsActivity.kt | 1048 | ~310 | MarkerRenderer ✅, RoutePolylineManager ✅, PlaceDataRepository ✅, RouteNavigationController ✅ | ✅ Completado |
| PreferencesActivity.kt | 523 | ~185 | RouteGenerator (buildPrompt ✅ / parseOrderedPlaces ✅ / generate ✅), DatabaseSeeder ✅, ProgressDialogController ✅ | ✅ Completado |
| PlaceDetailsActivity.kt | 481 | ~195 | ReviewRepository ✅, DeepLinkResolver ✅ | ✅ Completado |
| ProfileActivity.kt | 394 | ~185 | UserProfileRepository ✅, AccountManager ✅ | ✅ Completado |
| NetworkUtils.kt (cross-cutting) | — | ~25 | deduplicación isNetworkAvailable | ✅ Ya existía |
| **Total Sprint 3** | — | — | 8 clases pendientes | ~11–16 h estimadas |

Reducción total estimada: ~63% de líneas en Activities. El volumen de código del proyecto aumenta ~350 líneas netas (los stubs de las clases nuevas), pero cada clase nueva tiene responsabilidad única y ninguna supera las 130 líneas.

---

## Impacto en testabilidad

### MapsActivity

`MarkerRenderer` y `RoutePolylineManager` son clases puras sin referencias a Activity. `RoutePolylineManager` acepta su propio `OkHttpClient` en el constructor, por lo que se puede sustituir por un `MockWebServer` de OkHttp en tests de integración para verificar que el JSON de Routes API se construye correctamente sin hacer llamadas de red reales. `PlaceDataRepository` como `object` con `FirebaseFirestore` inyectado por defecto puede ser sustituido en tests por una instancia que use el emulador de Firestore. `RouteNavigationController` es testeable con Robolectric ya que solo manipula estado de índice y callbacks — no requiere un `GoogleMap` real si los callbacks son lambdas.

### PreferencesActivity

La extracción de `RouteGenerator` es la que tiene mayor impacto directo en testabilidad. `buildPrompt()` es una función pura `String -> String` que puede testearse con JUnit sin ninguna dependencia de Android. Tests concretos posibles: verificar que el presupuesto y el tiempo aparecen en el prompt, que los nombres de lugares se incluyen en la sección correcta, que cuando `customRequest` está en blanco la sección de petición específica no aparece, y que la lógica de `parseOrderedPlaces` ordena correctamente cuando un nombre es substring de otro. `DatabaseSeeder` es testeable con el emulador de Firestore (no requiere UI). `ProgressDialogController` es el único de los tres que sigue necesitando un contexto de Activity, pero al estar aislado no contamina los tests de `RouteGenerator`.

### PlaceDetailsActivity

`ReviewRepository` expone transacciones Firestore como funciones `suspend` que retornan `Result<Unit>`. Con el emulador de Firestore se pueden escribir tests que verifiquen: que el rating del lugar se recalcula correctamente al añadir una reseña, que no se permite un segundo submit del mismo usuario (el `findExistingReview` debe retornar un ID), y que `updateExistingReview` corrige el rating sin cambiar `reviewCount`. Estos tres casos son lógica de negocio crítica que actualmente es imposible de testear sin levantar la Activity completa. `DeepLinkResolver` es testeable en JUnit puro: se puede verificar cada uno de los 4 formatos de URI (custom scheme, https, http, esquema desconocido) con assertions simples.

### ProfileActivity

`UserProfileRepository` permite tests con el emulador de Firestore para verificar: que `saveNickname` falla con el error correcto cuando el nickname ya está tomado por otro usuario, y que `uploadPhoto` actualiza el campo `photoUrl` en Firestore además de subir el archivo. `AccountManager` es la clase con mayor riesgo en producción (borrado irreversible de cuenta) y precisamente por eso se beneficia más de ser testeable de forma aislada. Con el emulador de Firestore + Firebase Auth emulator se puede simular el flujo completo de `deleteAccount` sin afectar datos reales, verificando que todas las subcollecciones se eliminan y que la operación de Auth es la última en ejecutarse.

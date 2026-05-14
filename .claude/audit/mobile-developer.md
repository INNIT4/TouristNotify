# Android Best Practices Review — TrazaGo

## Resumen ejecutivo

La app tiene base sólida: ViewBinding consistente, coroutines en `lifecycleScope`, `ActivityResultLauncher` moderno, ProGuard bien cubierto y Material Design 3 en el layout principal. Se identifican 2 bugs P0 (memory leak real y crash potencial), 5 hallazgos P1 de alta importancia y varios P2 de calidad. La mayor deuda técnica es la concentración de responsabilidades en `MapsActivity` y el uso de `GetContent` en lugar del Photo Picker moderno.

---

## Responsabilidades de MapsActivity (1048 líneas)

1. Inicialización y configuración del mapa (`onMapReady`, estilos, bounds, zoom)
2. Carga de datos desde Firestore (`cargarLugaresDesdeFirestore`, `loadRouteByIds`, `loadPersonalizedRoute`)
3. Renderizado de marcadores circulares con Glide + Canvas (`addMarkerForTouristSpot`, 5 métodos de bitmap)
4. Filtrado por categorías con chips (`setupFilterChips`, `applyFilters`)
5. Búsqueda de texto contra Firestore (`setupSearchView`, `searchPlaces`)
6. Solicitud y gestión de permisos de ubicación (`enableMyLocation`, `onRequestPermissionsResult`)
7. Integración con Routes API v2 via OkHttp (`calculateAndDrawRoute`, `drawRoutePolyline`, `buildRoutesApiBody`)
8. Animación de polylines (`animatePolylineDraw`, `ValueAnimator`)
9. Panel de navegación de ruta con estado (`setupRouteNavigation`, `updateRouteNavigationPanel`)
10. Guardado de rutas en Firestore con diálogo (`showSaveRouteDialog`, `saveRouteToFirestore`)
11. Lanzamiento de `PlaceDetailsActivity` con resultado (`placeDetailsLauncher`)
12. Toggle de visibilidad de UI (`setupToggleUiButton`)
13. Comprobación de conectividad de red (`isNetworkAvailable`)

**¿Se puede dividir?** Sí. Extrayendo: (a) `MarkerRenderer` para lógica de bitmaps (líneas 382-1027), (b) `RouteManager` para Routes API y polylines (líneas 480-695), (c) `RouteNavigationController` para el panel de navegación (líneas 826-951). La Activity quedaría como orquestador puro.

---

## Hallazgos P0 (bugs reales / memory leaks)

### ~~[AND-001] Memory leak: Glide `CustomTarget` con `onLoadCleared` vacío — `MapsActivity.kt:406`~~ ✅
- **Resuelto**: `onLoadCleared` ahora asigna `loadedResource = null`, permitiendo que Glide recicle el bitmap. Campo `private var loadedResource: Bitmap?` añadido al `CustomTarget` anónimo.

### ~~[AND-002] Back stack corruptor en `OnboardingActivity` — `OnboardingActivity.kt:91`~~ ✅
- **Resuelto**: `navigateToMain()` ya llama `finish()` en todos los caminos de ejecución.

---

## Hallazgos P1 (deprecaciones, malas prácticas)

### ~~[AND-003] `GetContent` en lugar de Photo Picker moderno — `ProfileActivity.kt:37`~~ ✅
- **Resuelto**: Migrado a `PickVisualMedia` con `PickVisualMediaRequest(ImageOnly)`. Elimina necesidad del permiso `READ_MEDIA_IMAGES`.

### ~~[AND-004] `BACKGROUND_LOCATION` no verificado en `hasLocationPermission()` — `ProximityNotificationManager.kt:53,142`~~ ✅
- **Resuelto**: Añadida `hasBackgroundLocationPermission()` que verifica `ACCESS_BACKGROUND_LOCATION` en API 29+. `setupGeofencesForAllPlaces` ahora requiere ambos permisos.

### ~~[AND-005] `onSaveInstanceState` ausente en `MapsActivity`~~ ✅
- **Resuelto**: Implementados `onSaveInstanceState` y `onRestoreInstanceState` que preservan `selectedCategories`, `currentPlaceIndex`, e `isNavigatingRoute` en rotación de pantalla.

### ~~[AND-006] `OkHttpClient` como `companion object` con callbacks que retienen Activity — `MapsActivity.kt:80`~~ ✅
- **Resuelto**: `OkHttpClient` movido a `TrazaGoApplication.http` (singleton a nivel de proceso). `RoutePolylineManager` ahora lo recibe como parámetro de constructor (`http: OkHttpClient`). `MapsActivity.onMapReady` pasa `(application as TrazaGoApplication).http`. Beneficios: un solo connection pool compartido por toda la app, sin riesgo de retener Activity.

### ~~[AND-007] `POST_NOTIFICATIONS` no verificado antes de `notificationManager.notify()` — `ProximityNotificationManager.kt`~~ ✅
- **Resuelto**: Añadida `hasNotificationPermission()` que verifica `POST_NOTIFICATIONS` en API 33+. Ambos métodos `sendNotification` y `sendSimpleNotification` retornan early si no hay permiso.

---

## Hallazgos P2 (mejoras de calidad)

### ~~[AND-008] Colores hardcodeados en `MapsActivity.kt:997,1029-1036`~~ ✅
- **Resuelto**: Colores de categorías y `#1A73E8` definidos en `colors.xml` como `marker_*`. `getCategoryColor()` usa `ContextCompat.getColor(this, R.color.*)`.

### ~~[AND-009] `isNetworkAvailable` duplicado en `MapsActivity.kt:775` y `ProfileActivity.kt:93`~~ ✅
- **Resuelto**: Extraído a `NetworkUtils.kt` como `object NetworkUtils { fun isNetworkAvailable(context) }`. Ambas Activities actualizadas.

### ~~[AND-010] `activity_menu.xml:13` — color hardcodeado `@color/md_theme_light_background`~~ ✅
- **Resuelto**: Cambiado a `?attr/colorSurface`.

### ~~[AND-011] `GridLayout` con posiciones manuales en `activity_menu.xml:165`~~ ✅
- **Resuelto**: Ambos `GridLayout` (primario y secundario) y la tarjeta `button_top_places` standalone eliminados. Reemplazados por `RecyclerView id="menu_recycler"` con `GridLayoutManager(spanCount=2)` y `SpanSizeLookup` dinámico. Nuevos archivos: `MenuItemData.kt`, `MenuAdapter.kt`, `item_menu_card_primary.xml` (160dp), `item_menu_card_action.xml` (100dp). `MenuActivity.kt` actualizado: eliminado `setupLockedFeaturesUI()`, añadidos `setupMenuGrid()` y `handleMenuClick(id: MenuItemId)`.

### ~~[AND-012] `deleteAccount` elimina docs en loop secuencial — `ProfileActivity.kt:347`~~ ✅
- **Resuelto**: Refactorizado a función `batchDelete()` local que agrupa en chunks de 400 ops por batch. Eliminación de check-ins, rutas, reseñas y notificaciones ahora usa `batch.commit()`.

### ~~[AND-013] `ProximityNotificationManager.kt:63` usa `"lugares_turisticos"` distinta a `"lugares"` del resto~~ ✅
- **Resuelto**: Ya usa `FirestoreCollections.PLACES` (colección consistente).

---

## Buenas prácticas detectadas

- **ViewBinding**: Todos los archivos usan `ActivityXBinding.inflate(layoutInflater)`. Sin ningún `findViewById`.
- **`ActivityResultLauncher`**: `MapsActivity.kt:87` y `ProfileActivity.kt:37` usan la API moderna.
- **Coroutines con `lifecycleScope`**: Todas las operaciones asíncronas usan `lifecycleScope.launch`.
- **`markerGeneration` con `AtomicInteger`**: Patrón correcto para invalidar callbacks de Glide tras limpiar marcadores (`MapsActivity.kt:74`).
- **`polylineAnimator?.cancel()` en `onDestroy`**: `MapsActivity.kt:955` cancela el `ValueAnimator` correctamente.
- **ProGuard**: Reglas cubren Firebase, Room, Glide, OkHttp, Coroutines, Gemini SDK con granularidad correcta.
- **`FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`** en logout: Limpieza correcta del back stack.
- **Routes API v2**: Uso de la API moderna en lugar de Directions API deprecada.
- **LeakCanary en `debugImplementation`**: Detección de leaks activa solo en debug.

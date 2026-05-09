# Database Optimization — LUPITA

## Resumen ejecutivo

La app usa Room (SQLite local) como cache offline sincronizado con Firestore cada 6h vía WorkManager. La arquitectura es funcional pero tiene tres problemas críticos de producción y varios de performance. El más grave es la búsqueda global que descarga hasta 800 documentos de Firestore y filtra en memoria. Ninguna entidad Room tiene `@Index`, hay un índice compuesto de Firestore faltante que causa `FAILED_PRECONDITION` en producción, y el sync hace full-replace sin delta incremental.

---

## Hallazgos P0 — Queries que fallarán en producción a escala

### ~~[DB-001] GlobalSearchActivity.kt:112-183 — Full collection scan + filtrado en memoria~~ ✅
- ~~`performSearch()` hace tres `.get()` sin predicado de servidor: `lugares .limit(500)`, `blog_posts .limit(200)`, `eventos .limit(100)`.~~
- **Resuelto**: Lugares ahora usan `touristSpotDao().searchSpots(query)` (Room — 0 lecturas Firestore). Blog/eventos reducidos a `limit(50)` con `async`/`await`.

### ~~[DB-002] CheckInManager.kt:88-98 — Query triple-where sin índice compuesto en Firestore~~ ✅
- ~~`hasCheckedInToday()`: `.whereEqualTo("userId").whereEqualTo("placeId").whereGreaterThan("checkInTime", yesterday)`.~~
- ~~Firestore requiere índice compuesto `(userId ASC, placeId ASC, checkInTime ASC)`. Sin él → `FAILED_PRECONDITION` en producción.~~
- **Resuelto**: Creado `firestore.indexes.json` en raíz con el índice compuesto. CheckInManager usa `FirestoreCollections.CHECK_INS`.

### ~~[DB-003] OfflineManager.kt:157-173 — Check-ins sync sin límite ni delta incremental~~ ✅
- ~~`.whereEqualTo("userId", userId).get()` sin paginación. Usuario activo puede tener cientos de check-ins históricos.~~
- **Resuelto**: OfflineManager filtra por `checkInTime > lastCheckInSync` usando timestamp guardado en `EncryptedSharedPreferences`. Primera sync descarga todo; las siguientes solo lo nuevo.

---

## Hallazgos P1 — Performance degradada

### ~~[DB-004] RoomEntities.kt:58-116, 222-244 — Índices faltantes en todas las entidades~~ ✅
- ~~Ninguna entidad tiene `@Entity(indices = [...])`. Queries afectadas: `getSpotsByCategory`, `getFavoritesFlow`, `getCheckInsFlow`, `getEventsByCategory`.~~
- **Resuelto**: Añadidos `@Index` en:
  - `tourist_spots`: `categoria`, `nombre`, `rating`
  - `events`: `category`, `startDateTimestamp`
  - `favorites`: `userId`, `(userId, placeId)` unique
  - `check_ins`: `userId`, `(userId, placeId)`, `timestampLong`

### ~~[DB-005] RoomDAOs.kt:24-25 — LIKE con wildcard izquierdo bloquea índice~~ ✅
- **Resuelto Sprint 3**: `TouristSpotFtsEntity` con `@Fts4(contentEntity = TouristSpotEntity::class)` añadida en `RoomEntities.kt`. Room auto-crea triggers `ai`/`ad`/`au` para mantener el índice en sync. `searchSpots()` reescrita: `SELECT tourist_spots.* FROM tourist_spots JOIN tourist_spots_fts ON tourist_spots.rowid = tourist_spots_fts.rowid WHERE tourist_spots_fts MATCH :query`. `AppDatabase` version bumped to 2 (fallbackToDestructiveMigration — cache se repobla desde Firestore).

### ~~[DB-006] AppDatabase.kt:53-54 — Sin migraciones ni fallbackToDestructiveMigration~~ ✅
- **Resuelto** (sesión anterior): `fallbackToDestructiveMigration()` y `fallbackToDestructiveMigrationOnDowngrade()` presentes. Versión = 1.

### ~~[DB-007] MapsActivity.kt:277 — `whereIn("nombre", placeNames)` — busca por texto en lugar de ID~~ ✅
- **Resuelto**: `PlaceData` extendido con `id = doc.id`. `fetchPlacesAndThenGenerateRoute` construye `nameToId: Map<String,String>` y lo pasa a `generateRouteWithAI` e `invokeGenerateCF`. Tras parsear la respuesta de la IA, si todos los nombres resuelven a ID, se llama `navigateToMapWithRouteIds()` que pasa `ROUTE_PLACES_IDS` → `MapsActivity.loadRouteByIds()` usa `FieldPath.documentId()`. Si algún ID no resuelve (edge case), se cae al path legacy de nombres.

### [DB-008] OfflineManager.kt:94-127 — Full replace sin delta en sync de spots/events/blog
- **Pendiente Sprint 3**: Requiere añadir campo `updatedAt` a documentos Firestore y adaptar SyncWorker. Es un cambio de esquema de datos en producción.

---

## Hallazgos P2 — Optimizaciones recomendadas

### ~~[DB-009] WAL mode no configurado explícitamente — `AppDatabase.kt`~~ ✅
- **Resuelto**: Añadido `.setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)` al builder.

### ~~[DB-010] `exportSchema = false` en producción — `AppDatabase.kt`~~ ✅
- **Resuelto**: `exportSchema = true`. Schema se exporta a `app/schemas/` vía KSP arg `room.schemaLocation`.

### ~~[DB-011] `Source.CACHE` no utilizado — `OfflineManager.kt`~~ ✅
- **Resuelto**: `syncFromFirebase()` pasa `Source.CACHE` a todas las funciones de sync privadas. En modo offline evita timeouts de red sirviendo datos desde el cache local de Firestore SDK. Cada función privada acepta `source: Source = Source.DEFAULT` como parámetro para mantener compatibilidad.

### ~~[DB-012] Sync omitido cuando `isOfflineModeEnabled == false` — `SyncWorker.kt:17-19`~~ ✅
- **Resuelto**: Eliminado el guard `if (!isOfflineModeEnabled()) return`. Room actúa como caché de rendimiento independientemente del modo offline — el sync en background siempre mejora la experiencia. `isOfflineModeEnabled` sigue controlando si se sirven datos desde Room en lugar de Firestore en las vistas.

---

## Índices sugeridos — Implementados

```kotlin
// RoomEntities.kt — aplicado en esta sesión
@Entity(tableName = "tourist_spots", indices = [
    Index(value = ["categoria"]),
    Index(value = ["nombre"]),
    Index(value = ["rating"])
])

@Entity(tableName = "favorites", indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "placeId"], unique = true)
])

@Entity(tableName = "check_ins", indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "placeId"]),
    Index(value = ["timestampLong"])
])

@Entity(tableName = "events", indices = [
    Index(value = ["category"]),
    Index(value = ["startDateTimestamp"])
])
```

```json
// firestore.indexes.json — creado en raíz del proyecto
{
  "indexes": [
    {
      "collectionGroup": "checkIns",
      "fields": [
        { "fieldPath": "userId", "order": "ASCENDING" },
        { "fieldPath": "placeId", "order": "ASCENDING" },
        { "fieldPath": "checkInTime", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "eventos",
      "fields": [
        { "fieldPath": "isFeatured", "order": "DESCENDING" },
        { "fieldPath": "startDate", "order": "ASCENDING" }
      ]
    }
  ]
}
```

---

## Buenas prácticas detectadas

- **IDs de Firestore como `@PrimaryKey`**: Evita el antipatrón de `autoGenerate = true` con IDs duplicados.
- **`OnConflictStrategy.REPLACE`**: Todos los inserts garantizan idempotencia en el sync.
- **`Flow<List<T>>` en DAOs**: Actualización reactiva de UI sin polling.
- **Debounce de 400ms** en búsqueda global.
- **Singleton con double-checked locking** en `AppDatabase.getDatabase()`: `@Volatile` + `synchronized` correcto.
- **Reglas de seguridad Firestore sólidas**: default-deny, `isOwner()`, campos limitados en updates.

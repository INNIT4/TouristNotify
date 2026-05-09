# Performance Tuning — LUPITA

## Overview

Key performance decisions already implemented in the codebase, and areas still pending improvement.

---

## Implemented Optimizations

### Database (Room)

| Optimization | Implementation | File |
|---|---|---|
| WAL journal mode | `.setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)` | `AppDatabase.kt:64` |
| Indexes on all queried columns | `@Entity(indices = [...])` | `RoomEntities.kt` |
| Reactive UI updates | `Flow<List<T>>` in DAOs — no polling | `RoomDAOs.kt` |
| Idempotent sync | `OnConflictStrategy.REPLACE` on all inserts | `RoomDAOs.kt` |

WAL mode allows concurrent reads while a write is in progress. This is critical during sync: `SyncWorker` writes while the UI reads.

### Network (OkHttp)

`WeatherManager` uses a shared `OkHttpClient` with explicit timeouts:
```kotlin
OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()
```
Previously used `URL.readText()` which has no timeout and can block indefinitely on bad connections.

The `httpClient` instance is a companion object property — reused across calls, avoiding the overhead of creating a new client per request.

### Search

`GlobalSearchActivity` debounces search input by `AppConstants.SEARCH_DEBOUNCE_MS = 400L` before querying. This prevents a Firestore read on every keypress.

Spot search (`searchSpots`) now queries Room instead of Firestore (zero network calls after initial sync).

### AI Route Generation

`AppConstants.AI_TIMEOUT_MS = 60_000L` — Gemini calls are wrapped in a coroutine with this timeout. The generate button is disabled on submission and re-enabled on completion or error to prevent multiple concurrent calls.

### Firestore Queries

- `GlobalSearchActivity`: blog/events reduced to `limit(50)` with `async`/`await` parallelism
- `CheckInManager.hasCheckedInToday()`: triple-where query backed by composite Firestore index `(userId, placeId, checkInTime)`
- Route places query: uses `FieldPath.documentId()` when all AI-returned place names resolve to IDs (avoids `whereIn("nombre")` string scan)

### Type Converters

`Gson` instance in `Converters` is shared via `companion object` — avoids repeated construction cost on every type conversion during sync batch inserts.

---

## Pending Optimizations

### DB-005: Full-Text Search (Sprint 3)

**Problem**: `searchSpots()` uses `LIKE '%' || :query || '%'` — the leading `%` prevents the B-tree index on `nombre` from being used. Every search does a full table scan.

**Solution**: FTS5 virtual table with trigger-based sync:
```sql
CREATE VIRTUAL TABLE tourist_spots_fts USING fts5(id, nombre, descripcion, content=tourist_spots);
CREATE TRIGGER tourist_spots_ai AFTER INSERT ON tourist_spots BEGIN
  INSERT INTO tourist_spots_fts(id, nombre, descripcion) VALUES (new.id, new.nombre, new.descripcion);
END;
-- UPDATE and DELETE triggers also needed
```
Room supports FTS via `@Fts4` or `@Fts5` annotations. Requires Room version bump (1 → 2) and a `Migration`.

### DB-008: Delta Sync (Sprint 3)

**Problem**: `SyncWorker` does full-replace for `tourist_spots`, `events`, and `blog_posts` — downloads everything every 6 hours.

**Solution**: Add `updatedAt` Firestore timestamp to each document. SyncWorker stores the last sync timestamp in `offline_metadata` and queries `.whereGreaterThan("updatedAt", lastSyncTimestamp)`. Already implemented for check-ins (`last_check_in_sync` in `offline_metadata`).

### Image Loading

Glide 4.16.0 handles caching automatically. No known issues. Consider enabling disk cache size limits if storage complaints arise:
```kotlin
GlideBuilder().setDiskCache(InternalCacheDiskCacheFactory(context, MAX_DISK_CACHE_BYTES))
```

### Startup Time

`TouristNotifyApplication` initializes Firestore offline persistence and Firebase services synchronously on the main thread. If startup profiling shows > 200ms, move initialization to a coroutine launched from `onCreate`.

---

## Profiling Tools

- Android Studio Profiler → CPU/Memory → record during sync or search
- `adb shell dumpsys gfxinfo com.joseibarra.touristnotify` for frame rendering stats
- Firebase Performance Monitoring (not yet configured — add `firebase-perf` dependency to track network and custom traces)

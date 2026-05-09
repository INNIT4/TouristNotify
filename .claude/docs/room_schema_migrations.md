# Room Schema & Migrations — LUPITA

## Overview

Room is used exclusively as a **read-only offline cache** of Firestore data. It is NOT the source of truth. `SyncWorker` pulls data from Firestore every 6 hours and does full-replaces via `OnConflictStrategy.REPLACE`.

**Key files**: `AppDatabase.kt`, `RoomEntities.kt`, `RoomDAOs.kt`

---

## Database Configuration

| Property | Value |
|---|---|
| Database name | `tourist_notify_database` |
| Current version | 1 |
| Journal mode | `WRITE_AHEAD_LOGGING` (WAL) — explicit, reduces write contention |
| Schema export | `true` → `app/schemas/` (via KSP arg `room.schemaLocation`) |
| Migration strategy | `fallbackToDestructiveMigration()` + `fallbackToDestructiveMigrationOnDowngrade()` |
| Encryption | SQLCipher via `SupportFactory` — passphrase stored in EncryptedSharedPreferences (Android Keystore) |

The destructive migration fallback is intentional: Room is a cache. If the schema changes, `SyncWorker` repopulates the DB from Firestore on the next sync.

---

## Entities

### tourist_spots

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT (PK) | Firestore document ID |
| `nombre` | TEXT | |
| `descripcion` | TEXT | |
| `categoria` | TEXT | Indexed |
| `latitude` | REAL | GeoPoint split into two columns |
| `longitude` | REAL | |
| `rating` | REAL | Indexed |
| `reviewCount` | INTEGER | |
| `direccion` | TEXT | |
| `telefono` | TEXT? | |
| `sitioWeb` | TEXT? | |
| `horarios` | TEXT? | |
| `imagenUrl` | TEXT? | |
| `visitCount` | INTEGER | |
| `lastSyncedAt` | INTEGER | Epoch ms, set on insert |

**Indexes**: `categoria`, `nombre`, `rating`

### events

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT (PK) | Firestore document ID |
| `title` | TEXT | |
| `description` | TEXT | |
| `category` | TEXT | Indexed |
| `location` | TEXT | |
| `placeId` | TEXT | |
| `startDateTimestamp` | INTEGER | Epoch ms — Indexed |
| `endDateTimestamp` | INTEGER? | |
| `isFeatured` | INTEGER | Boolean |
| `imageUrl` | TEXT? | |
| `organizerName` | TEXT | |
| `lastSyncedAt` | INTEGER | |

**Indexes**: `category`, `startDateTimestamp`

### blog_posts

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT (PK) | Firestore document ID |
| `title` | TEXT | |
| `content` | TEXT | |
| `category` | TEXT | |
| `authorName` | TEXT | |
| `authorId` | TEXT | |
| `imageUrl` | TEXT? | |
| `likes` | INTEGER | |
| `viewCount` | INTEGER | |
| `isFeatured` | INTEGER | Boolean |
| `publishedAtTimestamp` | INTEGER | Epoch ms |
| `lastSyncedAt` | INTEGER | |

No explicit indexes (search by category uses full scan — acceptable for small blog collections).

### favorites

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT (PK) | Composite: `{userId}_{placeId}` |
| `userId` | TEXT | Indexed |
| `placeId` | TEXT | |
| `placeName` | TEXT | |
| `category` | TEXT | |
| `createdAtTimestamp` | INTEGER | Epoch ms |
| `lastSyncedAt` | INTEGER | |

**Indexes**: `userId`; composite `(userId, placeId)` UNIQUE

### check_ins

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT (PK) | Firestore document ID |
| `userId` | TEXT | Indexed |
| `placeId` | TEXT | |
| `placeName` | TEXT | |
| `category` | TEXT | |
| `timestampLong` | INTEGER | Epoch ms — Indexed |
| `lastSyncedAt` | INTEGER | |

**Indexes**: `userId`; composite `(userId, placeId)`; `timestampLong`

### offline_metadata

| Column | Type | Notes |
|---|---|---|
| `key` | TEXT (PK) | e.g. `"last_check_in_sync"` |
| `value` | TEXT | Stored value |
| `updatedAt` | INTEGER | Epoch ms |

Used to store sync timestamps (e.g. last delta sync watermark for check-ins).

---

## Type Converters (`Converters` class)

| Kotlin type | SQLite type | Conversion |
|---|---|---|
| `GeoPoint?` | TEXT? | `"lat,lng"` string |
| `Date?` | INTEGER? | Epoch ms (`date.time`) |
| `List<String>?` | TEXT | JSON array via Gson |

`Gson` instance is shared (companion object) — Gson is thread-safe and expensive to construct.

**Note**: `tourist_spots` does NOT store `GeoPoint` as a column — `latitude` and `longitude` are separate REAL columns for query efficiency. The converter exists for other potential uses.

---

## DAOs

| DAO | Table | Notable queries |
|---|---|---|
| `TouristSpotDao` | `tourist_spots` | `searchSpots(query)` — LIKE with leading wildcard (blocks B-tree index, see DB-005) |
| `EventDao` | `events` | `getEventsByCategory(category)` — uses `category` index |
| `BlogPostDao` | `blog_posts` | `getPostsByCategory(category)` |
| `FavoriteDao` | `favorites` | `getFavoritesFlow(userId)` — reactive, uses `userId` index |
| `CheckInDao` | `check_ins` | `getCheckInsFlow(userId)`, `getCheckInsForPlace(userId, placeId)` |
| `OfflineMetadataDao` | `offline_metadata` | `getMetadata(key)` / `insertMetadata(metadata)` |

All write operations use `OnConflictStrategy.REPLACE` for idempotent sync.

---

## Migration Strategy

**Current approach**: Destructive migration (drop + recreate on version bump). Acceptable because:
- Room is a cache — no user-authored data is stored here
- `SyncWorker` repopulates from Firestore within 6 hours
- SQLCipher's `SupportFactory` may prevent Room migration helpers from functioning across cipher version upgrades

**When to bump the version**: Any additive or breaking schema change (new column, dropped column, new table, dropped table, index change). Update `AppDatabase.version` and add a `Migration` object if you want to preserve cached data across the upgrade.

**Pending (DB-005 / DB-008)**:
- FTS5 virtual table for full-text search on `tourist_spots` — requires a Migration (version 1 → 2)
- `updatedAt` field on Firestore docs + delta sync — does not affect Room schema, only `SyncWorker` logic

---

## Accessing the Database

Always use the singleton:
```kotlin
val db = AppDatabase.getDatabase(context)
db.touristSpotDao().getAllSpotsFlow().collect { spots -> ... }
```

The singleton uses double-checked locking with `@Volatile` for thread safety.

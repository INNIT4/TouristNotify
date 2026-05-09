# Architectural Patterns

## 1. Singleton Manager Pattern

All cross-cutting concerns use Kotlin singletons instead of a DI framework. Each manager owns a single responsibility and exposes `suspend` functions for async work.

**`object` singletons** (not injectable): `AuthManager`, `ConfigManager`, `WeatherManager`

**`class` with `companion object { val instance by lazy {} }`** (injectable for tests): `FavoritesManager`, `CheckInManager` — use `FavoritesManager.instance.method()` at call sites.

**Files using this pattern:**
- `AuthManager.kt` — auth state, guest mode checks
- `ConfigManager.kt` — API key resolution
- `FavoritesManager.kt` — Firestore favorites CRUD (class, injectable)
- `CheckInManager.kt` — check-in recording + stats (class, injectable + clock param)
- `WeatherManager.kt` — weather API calls
- `UsageManager.kt` — daily AI route quota
- `FirestoreErrorHandler.kt` — error message formatting
- `CategoryUtils.kt` — category emoji/labels
- `NotificationHelper.kt` — Snackbar/Toast utilities

**Convention:** Managers that need `Context` receive it as a parameter on each call rather than holding a stored reference (avoids memory leaks).

## 2. ConfigManager Fallback Chain

API keys follow a two-tier resolution: Firebase Remote Config first, then `BuildConfig` constants (injected from `local.properties`). This allows changing keys without a new release.

**Reference:** `ConfigManager.kt` — `getGeminiApiKey()`, `getDirectionsApiKey()`, `getWeatherApiKey()`

**Pattern:**
```
Remote Config (cloud) → BuildConfig (local.properties) → empty string fallback
```

The `initialize()` method fetches Remote Config with a minimum fetch interval; all getters check Remote Config first.

## 3. Guest Mode Feature Gating

`AuthManager.kt` maintains a `isGuest` flag via SharedPreferences. Activities check this before enabling features.

**Gated features** (require full auth): favorites, check-ins, reviews, stats, AI routes, photo upload, proximity notifications.

**Open features** (guests allowed): browse places, maps, blog, events, weather, QR scan, gallery, services directory.

**UI pattern:** Locked features show a lock icon overlay in `MenuActivity.kt` with a dialog prompting login. Activities call `AuthManager.requireAuth(activity)` which returns `false` and shows login prompt if guest.

## 4. Offline-First Data Strategy

Three layers of persistence work together:

| Layer | Tech | Role |
|---|---|---|
| Cloud | Firestore | Source of truth, real-time sync |
| Local cache | Room | Offline reads, structured queries |
| Firestore SDK cache | Built-in | Automatic offline persistence |

**Sync flow** (`OfflineManager.kt`):
1. `ConnectivityObserver.kt` emits network status as `Flow<Boolean>`
2. When online, `OfflineManager.syncFromFirebase()` pulls Firestore → Room (pull-only)
3. `WorkManager` schedules periodic background sync every 6h
4. Activities read from Firestore when online, Room when offline
5. `OfflineMetadata` entity tracks last sync timestamp per collection
6. **Writes always go to Firestore directly** (SDK cache handles offline writes); Room gets updated via write-through in FavoritesManager on success

**Important**: Sync is NOT bidirectional. Room is a read cache, not the write source.

**Room setup** (`AppDatabase.kt`): Singleton via `synchronized` block with `fallbackToDestructiveMigration()`. Entities: `TouristSpotEntity`, `EventEntity`, `BlogPostEntity`, `FavoriteEntity`, `CheckInEntity`, `OfflineMetadata`.

## 5. Firestore Real-Time Listeners

Activities attach `addSnapshotListener()` for live updates instead of polling. Listeners are registered in `onCreate`/`onResume` and should be removed in `onDestroy`/`onPause`.

**Common pattern across activities:**
```
listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
    if (error != null) { FirestoreErrorHandler.handle(error); return }
    val items = snapshot?.documents?.mapNotNull { it.toObject(Model::class) }
    updateUI(items)
}
```

**Files:** `MapsActivity.kt`, `EventsActivity.kt`, `BlogActivity.kt`, `FavoritesActivity.kt`, `PhotoGalleryActivity.kt`

## 6. Deep Link Handling

Two URI schemes resolve to the same handler:

| Scheme | Example |
|---|---|
| Custom | `touristnotify://place/{placeId}` |
| HTTPS | `https://touristnotify.app/place/{placeId}` |

**Handler:** `PlaceDetailsActivity.kt` — `handleDeepLink()` extracts `placeId` from intent data, fetches from Firestore, and renders the place. Declared in `AndroidManifest.xml` via `<intent-filter>` with both schemes.

**QR codes** encode these URIs. See `CODIGOS_QR_GUIA_FINAL.md` for generation details.

## 7. Activity-Based Navigation

No Navigation Component or Fragment-based navigation. Each feature is a standalone `Activity` launched via explicit `Intent`.

**Transition animations** applied consistently:
```kotlin
startActivity(intent)
overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
```

**Back navigation** uses `finish()` with reverse animation:
```kotlin
finish()
overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
```

## 8. Adapter Pattern for Lists

All list/grid UIs use `RecyclerView` with dedicated `RecyclerView.Adapter` subclasses. Each adapter:
- Takes a mutable list + click callback lambda in constructor
- Inflates XML layout via `LayoutInflater`
- Uses `Glide` for image loading in `onBindViewHolder`
- Exposes `updateData(newList)` to refresh content with `notifyDataSetChanged()`

14 adapters total, one per list type (places, events, blog posts, reviews, favorites, routes, photos, contacts, services, search results, etc.)

## 9. Coroutine Usage

All async work uses `kotlinx.coroutines` with `CoroutineScope(Dispatchers.Main)` or `lifecycleScope` in Activities.

**Firebase tasks** are bridged with `tasks.await()` from `kotlinx-coroutines-play-services`.

**OkHttp calls** (Directions API, Weather API) run on `Dispatchers.IO` and switch to `Main` for UI updates.

**No RxJava** — pure coroutines throughout.

## 10. Error Handling

`FirestoreErrorHandler.kt` centralizes Firestore error codes into user-friendly Spanish messages. Activities call it in snapshot listener error callbacks and in `.addOnFailureListener` blocks.

**Pattern:** `FirestoreErrorHandler.getErrorMessage(exception)` returns a localized string displayed via `NotificationHelper.showError(view, message)` (Snackbar) or `Toast`.

## 11. Secure Storage

Sensitive data uses `EncryptedSharedPreferences` (AES256-GCM via `androidx.security:security-crypto`):
- Auth tokens and session state
- Usage quotas (`UsageManager.kt`)

Non-sensitive preferences use standard `SharedPreferences`:
- Theme preference (dark/light)
- Onboarding completion flag
- Guest mode flag

**Reference:** `AuthManager.kt`, `UsageManager.kt` for encrypted prefs setup.

## 12. Weather with Mock Fallback

`WeatherManager.kt` calls OpenWeatherMap API when a valid API key exists. If the key is missing or the call fails, it returns mock weather data for Alamos so the UI always renders.

**Resolution:** `ConfigManager.getWeatherApiKey()` → OkHttp GET → parse JSON → `WeatherInfo`. On failure → hardcoded `WeatherInfo` with typical Alamos conditions.

This ensures the weather widget in `MenuActivity.kt` never shows an error state.

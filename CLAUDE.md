# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# TrazaGo

Tourism discovery and navigation app for Alamos, Sonora, Mexico. Lets tourists browse attractions, generate AI-powered itineraries (Gemini), check in at places, read blog/events, view weather, and get proximity notifications via geofencing. Supports guest mode with limited features.

## Tech Stack

- **Language**: Kotlin 2.0.21 (Java 11 compat)
- **UI**: XML layouts + ViewBinding + Material Design 3 (no Compose)
- **Backend**: Firebase (Auth, Firestore, Storage, Remote Config)
- **Maps**: Google Maps SDK + Places API + Directions API
- **AI**: Google Gemini (`generativeai:0.9.0`) for route generation
- **Monitoring**: Firebase Crashlytics (auto-init on release) + Firebase Analytics
- **Security**: Firebase App Check (Play Integrity, release only) + HTTPS-only via network_security_config.xml
- **Weather**: OpenWeatherMap API (with mock fallback)
- **Images**: Glide 4.16.0
- **Networking**: OkHttp3 + Kotlin Coroutines
- **Build**: Gradle 8.13.1, Version Catalog (`gradle/libs.versions.toml`), Secrets Gradle Plugin
- **Min SDK**: 24 | **Target SDK**: 35

## Project Structure

All source under `app/src/main/java/com/joseibarra/TrazaGo/`:

| Directory/Area | Purpose |
|---|---|
| `TrazaGoApplication.kt` | App class: theme init, Firestore offline persistence |
| `AuthManager.kt` | Auth state + guest mode feature gating |
| `ConfigManager.kt` | Firebase Remote Config with BuildConfig fallback |
| `MenuActivity.kt` | Main hub activity (weather widget, feature cards) |
| `MapsActivity.kt` | Google Maps with filtering, routing, markers (exploration mode only) |
| `PlaceDetailsActivity.kt` | Place view, deep links (`TrazaGo://place/{id}`), reviews, check-in |
| `FavoritesManager.kt` | Firestore favorites CRUD under `users/{userId}/favorites/` |
| `CheckInManager.kt` | Check-in recording |
| `WeatherManager.kt` | OpenWeatherMap API calls + mock data |
| `ProximityNotificationManager.kt` | Geofence setup and management |
| `FirestoreErrorHandler.kt` | Centralized user-friendly error messages |
| `Models.kt` | Data classes: Favorite, CheckIn, Event, BlogPost, etc. |
| `TouristSpot.kt` | Tourist spot data model |
| `wizard/` | 4-step route preferences wizard (`RouteWizardActivity` + `PreferencesViewModel` + 4 Step fragments) |
| `routegen/` | AI route generation pipeline V2: `RouteGenerationCoordinator` → `CandidatePreFilter` → `PromptBuilderV2` → `RouteGeneratorV2` → `RouteValidator` → `RouteOptimizer` → `RouteEnricher` |
| `result/` | Post-generation screen (`RouteResultActivity`) with 4 tabs: Map, Itinerary, Editor (drag-drop), Regen/Share |
| `AdminBlogActivity.kt` | Admin Android: CRUD de blog posts en Firestore |
| `AdminReportsActivity.kt` | Admin Android: moderación de reportes de comunidad |
| `model/` | Shared data models: `GeneratedRoute`, `RouteStop`, `RouteMetrics`, `UserRoutePreferences`, `OpeningHours` |
| `*Adapter.kt` (14 files) | RecyclerView adapters for lists/grids |
| `*Activity.kt` (18 total) | Feature screens (events, blog, photos, admin, etc.) |

Resources: `res/layout/` (50+ XMLs), `res/values/` + `res/values-night/` (dark theme), `res/anim/` (slide/fade).

## Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test              # Unit tests (JUnit 4)
./gradlew connectedAndroidTest  # Instrumented tests (Espresso)

# Clean
./gradlew clean
```

**API Keys** are injected via `local.properties` (not in git):
```properties
GEMINI_API_KEY=...
DIRECTIONS_API_KEY=...
WEATHER_API_KEY=...
MAPS_API_KEY=...          # via Secrets Gradle Plugin
```

Debug builds have `ENABLE_SKIP_LOGIN=true`. Release builds enable ProGuard/R8 minification.

## Firestore Collections

| Colección | Propósito | Campos clave |
|---|---|---|
| `lugares` | Lugares turísticos | nombre, categoria, ubicacion, rating |
| `lugares_turisticos` | Alias usado por ProximityNotificationManager | — |
| `users/{userId}` | Perfil de usuario | email, name, createdAt |
| `users/{userId}/favorites` | Favoritos del usuario | placeId, placeName, addedAt |
| `usuarios/{uid}` | Colección legacy (no usar en código nuevo) | — |
| `eventos` | Eventos | nombre, fecha, descripcion |
| `blog_posts` | Posts del blog | titulo, contenido, categoria |
| `checkIns` | Registros de check-in | userId, placeId, checkInTime |
| `lugares/{id}/reviews` | Reseñas (subcollection) | userId, userName, rating (Float 1-5), comment, timestamp |
| `publicaciones_comunidad` | Posts de comunidad | authorId, title, content, **hidden** (Bool), likeCount, reportCount |
| `reportes_publicaciones` | Reportes de posts | postId, reporterId, reason, status ("pending"/"resolved") |
| `services` | Directorio de servicios | name, phoneNumber, category (ServiceCat), priority (Int) |
| `emergency_contacts` | Contactos de emergencia | name, phoneNumber, description |
| `themed_routes` | Rutas temáticas curadas admin | name, theme, placeIds, difficulty, isFeatured |
| `place_photos` | Fotos subidas por usuarios | placeId, imageUrl, uploadedBy, likes |

**Nota**: Usar `FirestoreCollections.*` constants para evitar strings sueltos. Ver `AppConstants.kt`.

## Key Conventions

- **No DI framework**: Managers are Kotlin `object` singletons (AuthManager, ConfigManager, etc.)
- **No ViewModel** (with one exception): Activity-level state with coroutines + Firestore snapshot listeners. **Exception**: `wizard/PreferencesViewModel` uses `androidx.lifecycle.ViewModel` + `SavedStateHandle` because state must survive rotation and be shared across 4 ViewPager2 fragments. This is the only ViewModel in the codebase.
- **Route generation pipeline**: Entry point is `MenuActivity` → `RouteWizardActivity` (4-step wizard) → `RouteGenerationCoordinator` (orchestrates pre-filter, Gemini V2 JSON, optimizer, RouteEnricher) → `RouteResultActivity` (4-tab post-route UX). The old `PreferencesActivity` + `RouteGenerator` (V1) have been deleted.
- **Intent-based navigation**: No Navigation Component; all screen transitions via `startActivity(Intent(...))`
- **Spanish-first UI**: Field names in Firestore use Spanish (`nombre`, `descripcion`, `ubicacion`)
- **Dual auth model**: `AuthManager.isGuest()` gates features; guests can browse but not write
- **Deep links**: `TrazaGo://place/{id}` and `https://TrazaGo.app/place/{id}` -> PlaceDetailsActivity

## Gotchas Críticos

- **Kotlin Boolean + Firestore**: Nunca usar `val isXxx: Boolean` — el getter `isXxx()` hace que Firestore serialice el campo como `xxx` (sin prefijo `is`). Usar `val xxx: Boolean`. Afecta `whereEqualTo()`, security rules y deserialización.
- **Firestore rules deploy**: Cambiar `firestore.rules` o `firestore.indexes.json` localmente no tiene efecto hasta `firebase deploy --only firestore:rules,firestore:indexes`.
- **VectorDrawable pathData vacío**: `android:pathData=""` causa `IllegalArgumentException` fatal en runtime. Eliminar el `<path>` completo si no tiene datos.

## Companion Website

Next.js 15 marketing site en `C:\Users\josei\pagina-trazago-v2` (GitHub: `INNIT4/pagina-trazago-v2`, Vercel). Comparte el mismo proyecto Firebase. Admin web en construcción en `/admin`.

## Additional Documentation

Check these when working on related features:

| File | Topic |
|---|---|
| `.claude/docs/architectural_patterns.md` | Singleton managers, offline sync, auth gating, API key management patterns |
| `ADMIN_GUIDE.md` | Admin features and place management |
| `BLOG_ADMIN_GUIDE.md` | Blog CMS creation and management |
| `CLIMA_REAL_SETUP.md` | OpenWeatherMap API configuration |
| `CODIGOS_QR_GUIA_FINAL.md` | QR code generation and deep link scanning |
| `FEATURES_IMPLEMENTATION.md` | Feature checklist with status |
| `FIREBASE_REMOTE_CONFIG_SETUP.md` | Remote Config for API keys and feature flags |
| `FIRESTORE_ERROR_HANDLING_EXAMPLES.md` | Common Firestore error patterns |
| `FIRESTORE_SECURITY_RULES.md` | Security rules documentation |
| `MODO_INVITADO_GUIA.md` | Guest mode architecture and feature gating |
| `firestore.rules` | Actual Firestore security rules |

# API Rate Limiting & Quotas — TrazaGo

## APIs Used

| API | Provider | Auth method | Free tier limit |
|---|---|---|---|
| Gemini | Google AI | API key (`GEMINI_API_KEY`) | 15 RPM / 1M TPM (Free) |
| Directions API | Google Maps Platform | API key (`DIRECTIONS_API_KEY`) | $200/month credit (~40k requests) |
| Maps SDK for Android | Google Maps Platform | API key (`MAPS_API_KEY`) in `AndroidManifest.xml` via Secrets Plugin | $200/month credit |
| OpenWeatherMap | OpenWeatherMap | API key (`WEATHER_API_KEY`) | 60 calls/min, 1M calls/month (Free) |
| Firestore | Firebase | App Check + Auth rules | 50k reads/day, 20k writes/day (Spark) |
| Firebase Functions | Firebase | App Check | 2M invocations/month (Spark) |

---

## Gemini (Route Generation)

**Call frequency**: User-triggered only. One call per route generation request.

**Timeout**: `AppConstants.AI_TIMEOUT_MS = 60_000L` (60 seconds). Gemini pro can be slow on first token.

**Cost protection**:
- The generate button is **disabled** immediately on tap and re-enabled only after the response or error. This prevents accidental double-submissions.
- `RouteInputValidator` blocks the call if budget or time fields are blank, negative, or zero — prevents calls with invalid inputs.
- `placesForPrompt` is limited to places returned by Firestore (typically < 50 in Álamos). Shorter context = fewer tokens.

**Fallback**: If the direct Gemini SDK call fails, `invokeGenerateCF()` calls the `generateRoute` Cloud Function which has the same API key on the server side.

**Key restriction**: In the Google Cloud Console, `GEMINI_API_KEY` should be restricted to the `generativelanguage.googleapis.com` API and, for production, to the app's SHA-1/SHA-256 fingerprint.

---

## Directions API

**Call frequency**: Triggered when the user executes a generated route in `MapsActivity`. One call per route.

**Cost**: $5 per 1,000 requests (after $200 credit). A typical tourist session generates 1–2 route requests.

**Protection**: 
- Routes are computed once and displayed — not polled or refreshed automatically.
- `DIRECTIONS_API_KEY` should be restricted to `directions.googleapis.com` and the Android app's SHA fingerprint in Google Cloud Console.

---

## OpenWeatherMap

**Call frequency**: `WeatherManager.getCurrentWeather()` and `getForecast()` are called on `MenuActivity` load.

**Fallback chain** (implemented in `WeatherManager`):
```
1. ConfigManager.getWeatherApiKey() — from local.properties / Remote Config
2. If blank → getWeatherFromCF() — Cloud Function `getWeather` (hides key server-side)
3. If CF fails → getMockWeather() / getMockForecast() — dynamic mock data
```

The mock data is time-aware (temperature varies by hour of day to simulate realistic Álamos, Sonora climate patterns). Users see plausible data even with no key configured.

**Timeout**: OkHttp `connectTimeout(10s)` + `readTimeout(15s)` — prevents indefinite blocking.

**Key restriction**: `WEATHER_API_KEY` should be restricted to the OpenWeatherMap dashboard to only the allowed referrer / IP if using the Cloud Function proxy.

---

## Firestore

**Call frequency**:
- `SyncWorker` runs every 6 hours (WorkManager `PeriodicWorkRequest`)
- Snapshot listeners on `PlaceDetailsActivity` (reviews), `FavoritesActivity` (favorites flow)
- `GlobalSearchActivity`: `limit(50)` on blog and events; spots served from Room (0 Firestore reads)

**Quota risk**:
- Spark plan: 50k reads/day. With ~100 tourist spots and 6h sync, `SyncWorker` costs ~100 reads/sync × 4 syncs/day = 400 reads/day per user. At 100 daily active users: 40k reads — near the limit.
- **Mitigation (DB-008, pending)**: Delta sync using `updatedAt` timestamps would reduce per-sync reads from ~100 to near 0 on idle days.

**App Check**: Enabled for production (Play Integrity provider). Prevents quota abuse by unauthorized clients. Only `debug` builds use the debug provider.

---

## Maps SDK

**No per-request cost** for displaying the map or markers. Costs accrue on:
- `Places API` autocomplete (not used)
- `Directions API` (see above)
- `Static Maps API` (not used — dynamic map only)

**Best practice**: Avoid creating new `GoogleMap` instances unnecessarily. `MapsActivity` holds a single `mMap` reference initialized once in `onMapReady`.

---

## Key Storage & Injection

All API keys are injected at build time via the Secrets Gradle Plugin from `local.properties` (not committed to git):

```properties
GEMINI_API_KEY=...
DIRECTIONS_API_KEY=...
WEATHER_API_KEY=...
MAPS_API_KEY=...     # injected into AndroidManifest.xml meta-data
```

At runtime, `ConfigManager` reads from `BuildConfig` fields (for keys baked at compile time) with Firebase Remote Config as an override mechanism. This allows rotating keys without an app update.

See `.claude/docs/api_keys_hardening.md` for key rotation procedures and security hardening steps.

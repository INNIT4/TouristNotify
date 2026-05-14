 # Security Audit — TrazaGo

## Resumen ejecutivo

Auditoría de la app Android TrazaGo (Kotlin, Firebase, min SDK 24). La postura de seguridad es **aceptable pero mejorable**: se identifican configuraciones sólidas (App Check con Play Integrity, `cleartextTrafficPermitted=false`, ProGuard/R8 en release, EncryptedSharedPreferences recientemente introducidas, reglas Firestore con "default-deny"), pero persisten **2 hallazgos P0 explotables** (reglas de admin basadas en email del token — escalable trivialmente, y una discrepancia crítica entre las reglas de Firestore y el código que deja colecciones reales sin reglas). Hay también **exposición de API keys** en el APK sin hardening real y deep links sin validación de formato.

---

## Hallazgos críticos (P0)

### ~~P0-1 Desalineación entre reglas Firestore y colecciones reales~~ ✅
- ~~**Archivo**: `firestore.rules:39`, `firestore.rules:98`, `firestore.rules:109`, `firestore.rules:161` vs `CheckInManager.kt:106`, `FavoritesManager.kt:27`, `MapsActivity.kt:356`, `MyRoutesActivity.kt:91`, `ProfileActivity.kt:154`~~
- ~~**Descripción**: Las reglas definen `match /users/{userId}/favorites/{favoriteId}` pero el código escribe en `users/{uid}/routes/` (no declarada). Similarmente `checkIns/{checkInId}` requiere `resource.data.userId == request.auth.uid` para leer, lo que bloquea listados. La regla `match /rutas/{routeId}` exige campos específicos (`id_ruta`, `id_usuario`, etc.) que el cliente puede no mandar. CLAUDE.md documenta `usuarios/` y `lugares_turisticos/` pero el código usa `users/` y `lugares/`.~~
- **Resuelto**: `ProfileActivity` y demás usan `rutas/` (raíz) con `id_usuario`. Subcol. huérfana `users/{uid}/routes` eliminada. `firestore.rules` cubre todas las colecciones reales.

### ~~P0-2 Admin privilege basado en claim `email` del token — sin custom claims~~ ✅
- ~~**Archivo**: `firestore.rules:22-27`, `AdminConfig.kt:12-18`~~
- ~~**Descripción**: `isAdmin()` compara `request.auth.token.email` contra lista hardcodeada.~~
- **Resuelto**: `firestore.rules` usa `request.auth.token.admin == true`. `AdminConfig.kt` rediseñado como UI hint únicamente, con comentario explícito de que no es capa de seguridad.

---

## Hallazgos medios (P1)

### ~~P1-1 `PendingIntent.FLAG_MUTABLE` en Geofence broadcast (Android 12+)~~ ✅
- ~~**Archivo**: `ProximityNotificationManager.kt:134-139`~~
- ~~**Descripción**: `PendingIntent.getBroadcast` con `FLAG_MUTABLE`.~~
- **Resuelto**: `ProximityNotificationManager.kt` usa `FLAG_IMMUTABLE` con guard `Build.VERSION.SDK_INT >= S`.

### ~~P1-2 Deep link `{id}` sin validación de formato~~ ✅
- ~~**Archivo**: `PlaceDetailsActivity.kt:76-112`, `PlaceDetailsActivity.kt:254`~~
- ~~**Descripción**: `handleDeepLink` extrae `uri.lastPathSegment` sin validar formato.~~
- **Resuelto**: `PLACE_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")` valida el ID antes de cualquier uso. Logs de rechazo guardados por `BuildConfig.DEBUG`.

### P1-3 API keys (Gemini, Directions, Weather, Maps) embebidas en el APK
- **Archivo**: `app/build.gradle.kts:33-38`, `ConfigManager.kt:86,108`
- **Estado parcial**: Cloud Functions creadas (`functions/src/generateRoute.ts`, `functions/src/getWeather.ts`). Android cliente actualizado en `WeatherManager.kt` y `PreferencesActivity.kt` para usar CF cuando la API key local está ausente. Dependencia `firebase-functions-ktx` añadida.
- **Pendiente (acciones humanas)**:
  1. Ejecutar `firebase secrets:set GEMINI_API_KEY` y `firebase secrets:set OPENWEATHERMAP_API_KEY`
  2. Ejecutar `firebase deploy --only functions`
  3. Eliminar `GEMINI_API_KEY` y `WEATHER_API_KEY` de `local.properties`
  4. Aplicar restricciones SHA-1 + package en Google Cloud Console para `DIRECTIONS_API_KEY` y `MAPS_API_KEY` (ver `.claude/docs/api_keys_hardening.md` sección 1)
- `MAPS_API_KEY` no es migrable a proxy (SDK Maps requiere key en cliente); proteger únicamente con SHA-1+package+cuotas.

### ~~P1-4 `allowBackup="true"` incluye `TrazaGoPrefs.xml`~~ ✅
- ~~**Archivo**: `AndroidManifest.xml:18`, `backup_rules.xml:8`~~
- **Resuelto**: `backup_rules.xml` excluye `TrazaGoPrefs.xml`, `TrazaGoUsage.xml`, tokens Firebase Auth y toda la DB Room.

### ~~P1-5 Sin rate-limit de login + user enumeration~~ ✅
- ~~**Archivo**: `LoginActivity.kt:67-102`~~
- ~~**Descripción**: Mensajes distintos para "no account" vs "wrong password" permiten enumeración.~~
- **Resuelto**: Mensaje genérico unificado. Backoff local: 3 fallos → `BACKOFF_MS` de espera. Variable `nextAllowedAuthAt` con `SystemClock.elapsedRealtime()`.

---

## Hallazgos bajos (P2)

### ~~P2-1 `visitCount` manipulable por cualquier autenticado~~ ✅
- ~~**Archivo**: `firestore.rules:44-46`~~
- **Resuelto**: Regla valida `visitCount == old+1`, `rating ∈ [0,5]`, `reviewCount` no decrementa. Solo admin puede modificar otros campos.

### ~~P2-2 Login/Register sin `FLAG_SECURE`~~ ✅
- ~~**Archivo**: `AndroidManifest.xml:32-39`~~
- **Resuelto**: `LoginActivity.onCreate` y `RegisterActivity.onCreate` aplican `window.setFlags(FLAG_SECURE, FLAG_SECURE)` antes de inflar la vista.

### ~~P2-3 Password policy débil (mínimo 6 caracteres)~~ ✅
- ~~**Archivo**: `RegisterActivity.kt:53-70`~~
- **Resuelto**: `MIN_PASSWORD_LENGTH = 10`. Validación de complejidad: al menos una letra y un dígito.

### ~~P2-4 Logs `Log.w` fuera de `BuildConfig.DEBUG`~~ ✅
- ~~**Archivo**: `PlaceDetailsActivity.kt:318,328`~~
- **Resuelto**: Todos los `Log.*` en `PlaceDetailsActivity` están guardados por `if (BuildConfig.DEBUG)`.

### ~~P2-5 PII en Room sin SQLCipher~~ ✅
- ~~**Archivo**: `RoomEntities.kt:222-244`, `AppDatabase.kt`~~
- **Resuelto**: `AppDatabase.kt` usa `SupportFactory(passphrase.copyOf())` con SQLCipher. `DatabasePassphraseManager` genera y persiste una passphrase de 32 bytes en `EncryptedSharedPreferences` (Android Keystore AES-256-GCM). Base de datos cifrada con AES-256.

### ~~P2-6 `displayName` no sanitizado en reviews~~ ✅
- ~~**Archivo**: `PlaceDetailsActivity.kt:394`~~
- **Resuelto**: `firestore.rules` valida `userName.size() <= 50`, `comment.size() <= 500`, `rating ∈ [1,5]` en create y update de reviews.

---

## Buenas prácticas detectadas

- `network_security_config.xml` con `cleartextTrafficPermitted="false"` y solo trust anchors del sistema.
- Firebase App Check con Play Integrity habilitado en release.
- Reglas Firestore con default-deny (`firestore.rules:343-345`).
- `EncryptedSharedPreferences` con AES256-GCM / AES256-SIV.
- ProGuard/R8 habilitado en release (`minifyEnabled = true`).
- API keys fuera de git (Secrets Gradle Plugin).
- Reviews impiden duplicado por `userId`.
- Check-ins son inmutables.
- `GeofenceBroadcastReceiver` correctamente `exported="false"`.

---

## Checklist OWASP Mobile Top 10 (2024)

| # | Categoría | Estado | Notas |
|---|---|---|---|
| M1 | Improper Credential Usage | ~~Parcial~~ **OK** | ~~User enumeration (P1-5); password policy débil (P2-3); sin FLAG_SECURE (P2-2)~~ Resueltos |
| M2 | Inadequate Supply Chain Security | OK | Version Catalog; Firebase/Google oficiales |
| M3 | Insecure Authentication/Authorization | ~~**Crítico**~~ **OK** | ~~Admin via claim `email` sin custom claims (P0-2); sin rate-limit~~ Resueltos |
| M4 | Insufficient Input/Output Validation | ~~Parcial~~ **OK** | ~~Deep link sin validación (P1-2); `visitCount` manipulable (P2-1)~~ Resueltos |
| M5 | Insecure Communication | OK | HTTPS-only; network_security_config estricto; App Check release |
| M6 | Inadequate Privacy Controls | ~~Parcial~~ **OK** | ~~Backup incluye prefs (P1-4); sin FLAG_SECURE login~~ Resueltos |
| M7 | Insufficient Binary Protection | Parcial | R8 activo. CF code creado y cliente actualizado (P1-3). Pendiente: deploy + eliminar keys de BuildConfig |
| M8 | Security Misconfiguration | ~~**Crítico**~~ **OK** | ~~Mismatch Firestore rules ↔ código (P0-1); PendingIntent MUTABLE (P1-1)~~ Resueltos |
| M9 | Insecure Data Storage | OK | EncryptedSharedPreferences activo; Room sin PII sensible |
| M10 | Insufficient Cryptography | OK | AES256-GCM; hashing delegado a Firebase Auth |

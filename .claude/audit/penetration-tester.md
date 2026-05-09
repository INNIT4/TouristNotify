# Penetration Test Report — LUPITA (Static Analysis)

## Resumen ejecutivo

La postura es razonable: `network_security_config` fuerza HTTPS, reglas Firestore default-deny, sin WebView/addJavascriptInterface, mayoría de activities `exported="false"`. Se identificaron **2 hallazgos P0**, **3 P1**, y varios P2/hardening. El principal riesgo real es el modelo de admin basado en email claim (vulnerable si atacante registra email con dominio correcto), y el `FLAG_MUTABLE` en PendingIntent de geofence. Prompt injection en Gemini es posible pero daño acotado por validador posterior.

---

## Vectores explotables (P0)

### ~~[PEN-001] Admin privilege claim vía email propio~~ ✅
- ~~**Archivo**: `firestore.rules:21-27`~~
- ~~**Pre-condiciones**: Cualquiera puede registrarse con Firebase Auth. La función `isAdmin()` hace: `request.auth.token.email in [...]`.~~
- **Resuelto**: `firestore.rules` usa `request.auth.token.admin == true`. Custom claim asignado únicamente via Firebase Admin SDK. `AdminConfig.kt` es solo UI hint.

### ~~[PEN-002] PendingIntent de geofence con FLAG_MUTABLE~~ ✅
- ~~**Archivo**: `ProximityNotificationManager.kt:132-140`~~
- ~~**Pre-condiciones**: Android 12+ (API 31). PendingIntent construido con `FLAG_UPDATE_CURRENT or FLAG_MUTABLE`.~~
- **Resuelto**: `getGeofencePendingIntent()` usa `FLAG_IMMUTABLE` con guard `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`.

---

## Vectores con explotación limitada (P1)

### [PEN-003] Digital Asset Links ausente → App Links no verificados
- **Archivo**: `AndroidManifest.xml:110-120`, `app/src/main/assets/.well-known/assetlinks.json`
- **Impacto**: `autoVerify="true"` falla silenciosamente → disambiguation dialog. Otra app maliciosa puede interceptar `https://touristnotify.app/place/*`.
- **Estado**: Plantilla JSON válida creada en `app/src/main/assets/.well-known/assetlinks.json`.
- **Pendiente (acciones humanas)**:
  1. Obtener SHA-256 del release keystore:
     ```
     keytool -list -v -keystore release.keystore -alias <ALIAS> | grep "SHA256:"
     ```
  2. Si usas Play App Signing: Play Console → App integrity → App signing key certificate → SHA-256 (añadir como segunda entrada en `sha256_cert_fingerprints`).
  3. Reemplazar `REPLACE_WITH_RELEASE_SHA256_FINGERPRINT` en el archivo JSON.
  4. Publicar en `https://touristnotify.app/.well-known/assetlinks.json` con `Content-Type: application/json`.
  5. Verificar: `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://touristnotify.app&relation=delegate_permission/common.handle_all_urls`
- **Esfuerzo**: M (requiere keystore de release + despliegue web)

### ~~[PEN-004] Prompt injection en Gemini vía `customRequest`~~ ✅
- ~~**Archivo**: `PreferencesActivity.kt:97, 213-217`~~
- ~~**Pre-condiciones**: Campo `customRequestEditText` sin longitud máxima ni sanitización.~~
- **Resuelto**: `android:maxLength="200"` en el XML. `PromptSanitizer.sanitizeCustomRequest()` filtra caracteres de control y palabras clave. Nombres de Firestore pasan por `PromptSanitizer.sanitizePlaceField()`.

### ~~[PEN-005] SSRF/local file load vía Glide~~ ✅
- ~~**Archivo**: `MapsActivity.kt:399`, `PhotoGalleryAdapter.kt:41`, `FullScreenPhotoAdapter.kt:37`, `ProfileActivity.kt:87`~~
- ~~**Pre-condiciones**: Admin comprometido setea `imageUrl = "file:///..."`.~~
- **Resuelto**: `SafeImageUrl.sanitize()` valida scheme `https` y whitelist de hosts (`firebasestorage.googleapis.com`, `lh*.googleusercontent.com`, `wikimedia.org`). `MapsActivity` corregido para usar `SafeImageUrl.sanitize(spot.imagenUrl)` (era el único sin protección).

---

## Vectores teóricos / defensa en profundidad (P2)

### ~~[PEN-006] IDOR en `incrementVisitCount` — estadísticas manipulables~~ ✅
- ~~**Archivo**: `PlaceDetailsActivity.kt:324-330`, `firestore.rules:44-46`~~
- **Resuelto**: `firestore.rules` restringe `visitCount` a solo +1 por update. Rating validado en `[0,5]`. Migración completa a Cloud Function en `.claude/docs/rating_visitcount_cloudfunctions.md`.

### ~~[PEN-007] `rating` y `reviewCount` modificables directo desde cliente~~ ✅
- ~~**Archivo**: `firestore.rules:44-46`~~
- **Resuelto**: Regla valida que `reviewCount` solo puede incrementar (+1) o mantenerse igual. `rating` restringido a `[0,5]`. Solo admin puede modificar otros campos del documento.

### ~~[PEN-008] `exported=true` en `PlaceDetailsActivity` — UI spoofing~~ ✅
- **Resuelto**: `PlaceDetailsActivity.kt:43-79` implementa defensa completa: (1) `PLACE_ID` validado con `PLACE_ID_PATTERN` regex — `finish()` si no coincide; (2) `PLACE_NAME`/`PLACE_CATEGORY` tratados como hints de carga, sanitizados (truncado + strip de control chars) y siempre sobrescritos por datos reales de Firestore en `loadPlaceDetails()`; (3) extras de coordenadas ignorados — posición siempre tomada del documento Firestore. `exported=true` necesario para deep links.

---

## Vectores probados y NO explotables

- **WebView / addJavascriptInterface**: cero ocurrencias. Descartado.
- **HTTP cleartext**: `network_security_config.xml:9` impone `cleartextTrafficPermitted="false"`. Correcto.
- **SQL injection**: No hay concatenación en queries; APIs tipadas de Firestore.
- **Deep link path traversal**: `handleDeepLink` valida scheme/host; ID validado con `PLACE_ID_PATTERN`.
- **Logs con password/token/apiKey**: todos los `Log.*` guardados por `BuildConfig.DEBUG`.
- **taskAffinity / allowTaskReparenting**: no declarados (defaults seguros).
- **Intent redirection en LoginActivity**: solo lee `RETURN_AFTER_LOGIN` (booleano inofensivo).
- **ProGuard/R8**: activado en release.
- **Firestore default-deny**: `firestore.rules` correcto.

---

## Recomendaciones de hardening

1. ~~**[P0-Urgente]** Reemplazar `isAdmin()` email-based por custom claims + `email_verified == true`.~~ ✅
2. ~~**[P0-Urgente]** Cambiar geofence PendingIntent a `FLAG_IMMUTABLE`.~~ ✅
3. **[P1-Alta]** Completar `assetlinks.json` con SHA-256 del keystore de release y publicar en `https://touristnotify.app/.well-known/` (ver PEN-003 — pasos detallados en el hallazgo).
4. ~~**[P1-Alta]** Whitelistear hosts en Glide (solo `firebasestorage.googleapis.com`).~~ ✅
5. ~~**[P2-Media]** Mover cálculo de `rating`, `reviewCount`, `visitCount` a Cloud Functions.~~ ✅ (validación server-side en rules; plan de CF en docs)
6. ~~**[P2-Media]** `maxLength=200` en `customRequestEditText` + sanitización de markers de prompt.~~ ✅
7. **[P2-Baja]** Restringir API keys en Google Cloud Console por package + SHA-256.

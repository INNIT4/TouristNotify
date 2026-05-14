# Compliance Audit — TrazaGo (TrazaGo)

**Fecha**: 2026-04-24 | **Scope**: Read-only, análisis estático

## Resumen ejecutivo

TrazaGo recolecta datos personales sensibles (email, ubicación precisa + segundo plano, fotos de perfil, historial check-ins con timestamp y GPS, reseñas, prompts enviados a Gemini), opera con Firebase/Google LLC (transferencia internacional México→EEUU→UE), y distribuye a turistas globales. **Incumplimientos P0 significativos**: no existe Política de Privacidad ni Términos de Servicio en el código ni referenciados por URL. Crashlytics y Analytics se auto-inicializan sin consentimiento granular (violación GDPR Art. 6). Sin disclosure prominente para `ACCESS_BACKGROUND_LOCATION` (requisito Play Policy). **Estado**: No apto para publicación en Play Store mercados UE/EEUU sin remediación P0.

---

## Marcos aplicables

| Marco | Aplicable | Justificación |
|---|---|---|
| **GDPR** (UE/EEA) | Sí | Turistas UE pueden descargar; Firebase procesa en EEUU (Art. 3(2) scope) |
| **LFPDPPP** (México) | Sí | Responsable en México; email, nickname, foto, ubicación, check-ins |
| **CCPA/CPRA** (California) | Probable | Si se comercializa en US Play Store |
| **Google Play Data Safety** | Obligatorio | Todos los publicadores Play |
| **Play Families Policy** | Riesgo alto | App turismo sin age gate → clasificada "mixed audience" |
| **COPPA** (US, <13) | Riesgo | Sin verificación de edad |
| **Target SDK 35** | Cumple | Declarado en CLAUDE.md |

---

## Hallazgos P0 — Riesgo legal inmediato

### ~~P0-1 Ausencia total de Política de Privacidad y Términos de Servicio~~ ✅ (parcial)
- ~~**Evidencia**: Grep en `/app/src/main/java` para `privacy`, `terms`, `privacy_url` → 0 resultados. Ninguna `PrivacyPolicyActivity`. Sin URL en `strings.xml`.~~
- **Resuelto en app**: `PrivacyPolicyActivity` creada con aviso de privacidad placeholder (GDPR/LFPDPPP). Enlace "Al crear una cuenta aceptas nuestra Política de Privacidad" añadido en `RegisterActivity` y `OnboardingActivity`. Texto cargado con `HtmlCompat.fromHtml()` para subrayado accesible. Strings `privacy_policy_content`, `register_privacy_notice`, `onboarding_privacy_notice` en `strings.xml`.
- **Pendiente (requiere acción humana)**:
  1. Reemplazar `privacy_policy_content` placeholder con texto legal definitivo revisado por abogado.
  2. Publicar en `https://TrazaGo.app/privacy` y enlazar desde Play Console listing.
  3. ~~Añadir enlace también en `ProfileActivity` (ajustes de cuenta).~~ ✅ `privacy_policy_button` añadido a layout + `setupListeners()`.
  4. Crear Términos de Servicio separados.

### ~~P0-2 Crashlytics y Analytics sin consentimiento (GDPR/ePrivacy)~~ ✅
- ~~**Evidencia**: `TrazaGoApplication.kt:36-40` inicializa Firebase. Auto-init por manifest merger.~~
- **Resuelto**: `AndroidManifest.xml` desactiva por defecto `firebase_crashlytics_collection_enabled=false` y `firebase_analytics_collection_enabled=false`. `ConsentManager` activa ambos flags en runtime tras opt-in del usuario.

### ~~P0-3 Sin disclosure prominente para `ACCESS_BACKGROUND_LOCATION`~~ ✅
- ~~**Evidencia**: `ProximityNotificationsActivity.kt:227-231` lo solicita directamente. Sin pantalla previa.~~
- **Resuelto**: `ProximityNotificationsActivity` muestra `AlertDialog` con el texto literal requerido por Play Policy antes de solicitar `ACCESS_BACKGROUND_LOCATION`.

### ~~P0-4 Datos enviados a Gemini sin transparencia~~ ✅
- ~~**Evidencia**: `PreferencesActivity.kt:97, 213-217`. Sin disclosure.~~
- **Resuelto**: `PreferencesActivity` muestra `AlertDialog` con disclaimer la primera vez que el usuario genera una ruta (persiste en `SharedPreferences` con key `ai_disclaimer_accepted_v1`).

### ~~P0-5 Sin función de portabilidad/exportación de datos (GDPR Art. 20)~~ ✅
- ~~**Evidencia**: `ProfileActivity.kt:185-372` soporta editar y eliminar cuenta, pero no exportar.~~
- **Resuelto**: Botón "Exportar mis datos" en `ProfileActivity`. Función `exportUserData()` recolecta perfil, favoritos, check-ins, rutas y reseñas en paralelo, genera JSON y lo comparte vía `FileProvider` + `Intent.ACTION_SEND`. `FileProvider` registrado en `AndroidManifest.xml`.

---

## Hallazgos P1 — Requisitos Play Store

### P1-1 Sin age gate ni declaración de target audience
- **Evidencia**: `RegisterActivity.kt` solo pide email/password. Sin campo de fecha de nacimiento.
- **Decisión recomendada**: Declarar `18+` en Play Console → evita Play Families Policy y COPPA/CFAA. La app es turismo general; no produce contenido para menores.
- **Pendiente (acción humana — Play Console)**:
  1. Play Console → App content → Target audience → seleccionar "18 and over".
  2. Si se mantiene "all ages": añadir campo de fecha de nacimiento en `RegisterActivity` y bloquear menores de 18 (o 13 para COPPA) mostrando `AlertDialog` de restricción.
  3. Actualizar Política de Privacidad con sección "Edad mínima de uso: 18 años".

### ~~P1-2 `allowBackup="true"` expone datos a backup de Google Drive~~ ✅
- ~~**Evidencia**: `AndroidManifest.xml:18`. Datos de check-in y prefs van al backup sin disclosure.~~
- **Resuelto**: `backup_rules.xml` excluye Room DB, EncryptedSharedPreferences, tokens Firebase Auth.

### ~~P1-3 `READ_MEDIA_IMAGES` sin justificación Play Policy 2024~~ ✅
- ~~**Evidencia**: `ProfileActivity.kt:37-41` usa `GetContent()` en lugar de Photo Picker moderno.~~
- **Resuelto**: `ProfileActivity` migrado a `ActivityResultContracts.PickVisualMedia()` — no requiere `READ_MEDIA_IMAGES` en API 33+. Fallback automático del sistema en dispositivos más antiguos.

### ~~P1-4 Función de borrado de cuenta incompleta~~ ✅
- ~~**Evidencia**: `ProfileActivity.kt:333-372` elimina `routes/` (subcol. huérfana), falta `resenas/`, `place_photos/`, Storage, Room cache.~~
- **Resuelto**: `deleteAccount()` ahora elimina: `favorites/`, `stats/`, `usage/` (subcols. reales), `rutas/` (colección raíz con `id_usuario`), `checkIns/` (raíz), reviews (collectionGroup), `notifications/`, Storage `users/$uid/`, y Room DB local (`AppDatabase.clearAllTables()`).

---

## Hallazgos P2 — Best practices

- **P2-1** Sin retención definida para `checkIns`, `reviews`, `place_photos` (GDPR Art. 5(1)(e)) — documentar en Política de Privacidad.
- **P2-2** Email duplicado en Firestore `users/{uid}` — minimización GDPR Art. 5(1)(c).
- **P2-3** `FirebaseAuth email verification` enviada pero no forzada — bloquear funciones sensibles hasta verificación.
- **P2-4** Sin breach notification plan documentado (GDPR Art. 33: 72h).
- **P2-5** Sin DPA firmado documentado con Google Cloud/Firebase, Google AI/Gemini, OpenWeatherMap.
- ~~**P2-6** Admin emails hardcodeados en `firestore.rules` — rotación imposible sin deploy.~~ ✅ Resuelto con custom claims.
- **P2-7** `GeofenceBroadcastReceiver` sin `FOREGROUND_SERVICE_LOCATION` wiring explícito — verificar Foreground Service Types requirement (SDK 34+).

---

## Google Play Data Safety — Declaración sugerida

| Categoría | Dato | Recolectado | Compartido | Opcional | Propósito |
|---|---|---|---|---|---|
| Personal info | Email address | Sí | No | No | Auth, Account management |
| Personal info | Name (nickname) | Sí | No | Sí | Personalization |
| Personal info | User ID | Sí | Con Google (Firebase) | No | Account management |
| Photos | Profile photo | Sí | No | Sí | Personalization |
| Location | Precise location | Sí | Con Google (Maps) | Sí | Geofencing |
| Location | Approximate location | Sí | Con Google (Maps) | No | App functionality |
| App activity | In-app prompts (Gemini) | Sí | Con Google (Gemini) | No | AI features |
| App activity | Check-ins, favoritos, reviews | Sí | No | Sí | Analytics, Personalization |
| App info | Crash logs, diagnostics | Sí (Crashlytics) | Con Google | **Opt-in** | Analytics |
| Device IDs | Device ID | Sí (Analytics) | Con Google | **Opt-in** | Analytics |

**Security practices**: Data encrypted in transit (HTTPS). Data deletion: Sí (deleteAccount completo). Data portability: Sí (exportUserData).

---

## Checklist de derechos del titular

| Derecho | Implementado | Gap |
|---|---|---|
| Acceso (Art. 15) | Parcial | Solo muestra perfil/stats; no check-ins/reviews completos |
| Rectificación (Art. 16) | Sí | Nickname editable; email no editable |
| Supresión/Borrado (Art. 17) | ~~Parcial~~ **Sí** | ~~Falta borrar resenas, place_photos, Storage, Room~~ Resuelto |
| Oposición (Art. 21) | No | Sin toggles granulares para analytics/notifications marketing |
| Portabilidad (Art. 20) | ~~**No**~~ **Sí** | ~~No existe función de exportación~~ Resuelto |
| Limitación tratamiento (Art. 18) | No | No implementado |
| Revocación de consentimiento | Parcial | Toggle proximity notifications; sin toggle analytics/crashlytics en perfil |

---

## Documentos a crear (prioritarios — requieren acción humana)

1. **Política de Privacidad** (ES/EN) en `https://TrazaGo.app/privacy` + enlace desde Play Store listing, `OnboardingActivity`, `ProfileActivity`, `LoginActivity`.
2. **Términos de Servicio** con UGC, edad mínima, ley aplicable (México), limitación de responsabilidad.
3. **Aviso de Privacidad Simplificado** in-app (LFPDPPP Art. 17) integrado en `OnboardingActivity`.
4. **Consent banner** para analytics/crashlytics (opt-in separado de funcional).
5. **DPIA** (GDPR Art. 35) — obligatoria por procesamiento sistemático de ubicación.
6. **ROPA** (GDPR Art. 30) — Registro de Actividades de Tratamiento.
7. **DPA** con Firebase/Google, Gemini AI, OpenWeatherMap.
8. **Data Retention Policy**: TTLs para check-ins (3 años), reviews (indefinido mientras cuenta activa), fotos (borradas con cuenta).
9. **Incident Response Plan**: procedimiento breach 72h.

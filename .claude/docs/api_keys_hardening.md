# API Keys — Hardening y migración a servidor

LUPITA depende de cuatro API keys externas:
- `GEMINI_API_KEY` — generación de rutas con IA
- `DIRECTIONS_API_KEY` — Google Routes API v2
- `WEATHER_API_KEY` — OpenWeatherMap
- `MAPS_API_KEY` — Google Maps SDK (renderizado del mapa)

Hoy las cuatro viven en `BuildConfig` o en el manifest, lo que significa que
**están dentro del APK** y son extraíbles con `apktool` por cualquier persona.
App Check protege Firebase Auth + Firestore + Storage, pero **no** protege
Google Routes, OpenWeatherMap ni Gemini.

Este documento describe:
1. **Restricciones obligatorias** que deben aplicarse hoy (sin código nuevo).
2. **Migración a Cloud Functions** para Gemini y OpenWeatherMap (recomendado a corto plazo).
3. **Maps SDK** — caso especial.

---

## 1. Restricciones en Google Cloud Console (HACER YA)

Para cada key Google (`GEMINI_API_KEY`, `DIRECTIONS_API_KEY`, `MAPS_API_KEY`):

1. Ir a **Google Cloud Console → APIs & Services → Credentials**.
2. Seleccionar la API key.
3. **Application restrictions** → "Android apps":
   - Package name: `com.joseibarra.touristnotify`
   - SHA-1 certificate fingerprint: el del **release keystore**
     ```bash
     keytool -list -v -keystore release.keystore -alias <ALIAS> | grep "SHA1:"
     ```
   - Si usas Play App Signing, agrega también el SHA-1 que muestra Play Console
     en *Setup → App integrity*.
4. **API restrictions** → seleccionar solo las APIs que esa key necesita:
   - `GEMINI_API_KEY` → Generative Language API
   - `DIRECTIONS_API_KEY` → Routes API
   - `MAPS_API_KEY` → Maps SDK for Android, Places API
5. Guardar. La key dejará de funcionar para cualquier dispositivo que no
   esté firmado con ese certificado.

Para `WEATHER_API_KEY` (OpenWeatherMap):
- OpenWeatherMap **no** soporta restricción por SHA-1. Solo por IP.
- Por eso es la primera candidata a migración a Cloud Function.

---

## 2. Migración de Gemini a Cloud Function

Mover la generación de rutas a un endpoint protegido por **Firebase App Check**.
El cliente envía las preferencias del usuario; el servidor llama a Gemini con
la key real (que vive solo en server) y devuelve la respuesta.

### `functions/src/generateRoute.ts`

```typescript
import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { GoogleGenerativeAI } from '@google/generative-ai';

admin.initializeApp();

// Gemini API key vive en config de Firebase (NO en código):
//   firebase functions:config:set gemini.key="ABC..."
const genAI = new GoogleGenerativeAI(functions.config().gemini.key);

export const generateRoute = functions
  .runWith({ enforceAppCheck: true })          // exige token App Check
  .https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError(
        'unauthenticated',
        'Debes iniciar sesión'
      );
    }
    if (context.app == null) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        'Solicitud sin token App Check'
      );
    }

    // Rate limit por usuario (5/día) - leer de Firestore
    const usageRef = admin.firestore()
      .collection('users')
      .doc(context.auth.uid)
      .collection('usage')
      .doc(new Date().toISOString().slice(0, 10));
    const usage = await usageRef.get();
    const count = (usage.data()?.routesGenerated ?? 0) + 1;
    if (count > 5) {
      throw new functions.https.HttpsError(
        'resource-exhausted',
        'Límite diario alcanzado'
      );
    }
    await usageRef.set({ routesGenerated: count, timestamp: Date.now() }, { merge: true });

    const { promptInput } = data;
    if (typeof promptInput !== 'string' || promptInput.length > 4000) {
      throw new functions.https.HttpsError(
        'invalid-argument',
        'promptInput inválido'
      );
    }

    const model = genAI.getGenerativeModel({
      model: 'gemini-2.5-flash',
      generationConfig: { temperature: 0.3, maxOutputTokens: 512 },
    });
    const result = await model.generateContent(promptInput);
    return { text: result.response.text() };
  });
```

### Cliente Kotlin

```kotlin
val functions = Firebase.functions
val data = mapOf("promptInput" to fullPrompt)
val result = functions.getHttpsCallable("generateRoute").call(data).await()
val text = (result.data as Map<*, *>)["text"] as String
```

`GEMINI_API_KEY` deja de existir en el APK. Las restricciones de IP para
Generative Language API se aplican al servidor, donde son efectivas.

---

## 3. Migración de OpenWeatherMap a Cloud Function

OpenWeatherMap no soporta restricciones por package. Mover a Cloud Function
con caché de Firestore (15 min) reduce costos y elimina la key del APK.

```typescript
// functions/src/getWeather.ts
export const getWeather = functions
  .runWith({ enforceAppCheck: true })
  .https.onCall(async (data, context) => {
    const { lat, lon } = data;
    if (typeof lat !== 'number' || typeof lon !== 'number') {
      throw new functions.https.HttpsError('invalid-argument', 'lat/lon requeridos');
    }
    // Caché Firestore por (lat, lon, hora)
    const cacheKey = `${lat.toFixed(2)}_${lon.toFixed(2)}_${Math.floor(Date.now() / 900_000)}`;
    const cacheRef = admin.firestore().collection('weather_cache').doc(cacheKey);
    const cached = await cacheRef.get();
    if (cached.exists) return cached.data();

    const url = `https://api.openweathermap.org/data/2.5/weather?lat=${lat}&lon=${lon}` +
                `&appid=${functions.config().openweathermap.key}&units=metric&lang=es`;
    const res = await fetch(url);
    const data2 = await res.json();
    await cacheRef.set(data2);
    return data2;
  });
```

---

## 4. `MAPS_API_KEY` — caso especial

El SDK de Google Maps para Android **no** puede llamarse a través de proxy.
La key debe estar accesible al runtime del cliente. Las únicas defensas:

1. **Restricciones por SHA-1 + package** (ver sección 1) — obligatorio.
2. **Cuotas estrictas** en Cloud Console → APIs & Services → Quotas.
   Por ejemplo, 10,000 llamadas/día — limita el daño si la key se filtra.
3. **Monitorear billing alerts** — si alguien extrae la key y la usa, las
   alertas de presupuesto te avisan en horas.

No hay forma técnica de "proteger" la `MAPS_API_KEY` más allá de eso.

---

## 5. Migración paso a paso

| Paso | Acción | Esfuerzo | Resultado |
|---|---|---|---|
| 1 | Restringir keys Google por package + SHA-1 (sección 1) | 30 min | Ninguna app distinta puede usarlas |
| 2 | Habilitar Cloud Functions en el proyecto | 1 h | Servidor disponible |
| 3 | Migrar Gemini a `generateRoute` callable | 4 h | `GEMINI_API_KEY` fuera del APK |
| 4 | Migrar OpenWeatherMap a `getWeather` | 2 h | `WEATHER_API_KEY` fuera del APK |
| 5 | Eliminar las keys de `local.properties` y `BuildConfig` | 30 min | APK limpio |
| 6 | Migrar Routes API igual que Gemini (opcional) | 4 h | `DIRECTIONS_API_KEY` fuera del APK |

Tras el paso 5, el único secreto en el APK será `MAPS_API_KEY`, restringido
por package + SHA-1.

---

## 6. Detección de filtración

Habilitar en **Cloud Console → APIs & Services → Quotas → Alerts**:
- Email cuando uso supera 80% del límite diario.
- Email cuando aparezcan llamadas desde IPs/regiones inesperadas.

En **Firebase Console → App Check → Metrics**:
- Verificar que el % de tráfico verificado por App Check sea cercano a 100%.
  Si baja, hay clientes sin token (probable extracción de key).

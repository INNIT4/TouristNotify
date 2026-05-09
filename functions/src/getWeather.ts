import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";
import * as https from "https";

if (admin.apps.length === 0) {
  admin.initializeApp();
}

// Almacenar la OPENWEATHERMAP_API_KEY en Firebase Secret Manager:
//   firebase secrets:set OPENWEATHERMAP_API_KEY
const weatherSecret = defineSecret("OPENWEATHERMAP_API_KEY");

// 15 minutos en unidades de "ventanas" de 15 min desde Unix epoch
const CACHE_WINDOW_MS = 15 * 60 * 1000;

/**
 * Callable: obtiene datos del clima desde OpenWeatherMap con caché de 15 min.
 *
 * App Check opcional (datos públicos); sin autenticación requerida.
 *
 * Input:  { lat: number, lon: number, type: "current" | "forecast" }
 * Output: { json: string } — JSON de OpenWeatherMap como string (mismo formato
 *         que la API directa) para que el cliente reutilice el código de parseo.
 *
 * Despliegue:
 *   1. firebase secrets:set OPENWEATHERMAP_API_KEY
 *   2. firebase deploy --only functions
 */
export const getWeather = onCall(
  { enforceAppCheck: false, secrets: [weatherSecret] },
  async (request) => {
    const { lat, lon, type } = request.data as {
      lat: unknown;
      lon: unknown;
      type: unknown;
    };

    if (typeof lat !== "number" || typeof lon !== "number") {
      throw new HttpsError("invalid-argument", "lat y lon deben ser números");
    }
    if (type !== "current" && type !== "forecast") {
      throw new HttpsError(
        "invalid-argument",
        "type debe ser 'current' o 'forecast'"
      );
    }

    const window = Math.floor(Date.now() / CACHE_WINDOW_MS);
    const cacheKey = `${lat.toFixed(2)}_${lon.toFixed(2)}_${type}_${window}`;
    const cacheRef = admin.firestore().collection("weather_cache").doc(cacheKey);

    const cached = await cacheRef.get();
    if (cached.exists) {
      return cached.data() as { json: string };
    }

    const apiKey = weatherSecret.value();
    const endpoint =
      type === "forecast" ? "forecast" : "weather";
    const url =
      `https://api.openweathermap.org/data/2.5/${endpoint}?` +
      `lat=${lat}&lon=${lon}&appid=${apiKey}&units=metric&lang=es`;

    const raw = await fetchJson(url);
    const response = { json: JSON.stringify(raw) };
    await cacheRef.set(response);
    return response;
  }
);

function fetchJson(url: string): Promise<Record<string, unknown>> {
  return new Promise((resolve, reject) => {
    https
      .get(url, (res) => {
        let body = "";
        res.on("data", (chunk: string) => (body += chunk));
        res.on("end", () => {
          try {
            resolve(JSON.parse(body) as Record<string, unknown>);
          } catch (e) {
            reject(e);
          }
        });
      })
      .on("error", reject);
  });
}

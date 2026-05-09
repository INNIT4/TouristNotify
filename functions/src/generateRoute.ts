import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";
import { GoogleGenerativeAI, SchemaType } from "@google/generative-ai";

if (admin.apps.length === 0) {
  admin.initializeApp();
}

// firebase secrets:set GEMINI_API_KEY
const geminiSecret = defineSecret("GEMINI_API_KEY");

const MAX_DAILY_ROUTES = 5;
const MAX_PROMPT_LENGTH = 8000;

// Schema de ruta — espejo del ROUTE_RESPONSE_SCHEMA en GeneratedRoute.kt
const ROUTE_SCHEMA = {
  type: SchemaType.OBJECT,
  required: ["resumen", "paradas", "metricas"],
  properties: {
    resumen: {
      type: SchemaType.OBJECT,
      required: ["titulo", "descripcion", "temaPrincipal"],
      properties: {
        titulo:            { type: SchemaType.STRING },
        descripcion:       { type: SchemaType.STRING },
        temaPrincipal:     { type: SchemaType.STRING },
        consejosGenerales: { type: SchemaType.ARRAY, items: { type: SchemaType.STRING } },
      },
    },
    paradas: {
      type: SchemaType.ARRAY,
      items: {
        type: SchemaType.OBJECT,
        required: ["placeId", "ordenSugerido", "razonSeleccion", "duracionEstimadaMin", "horaSugeridaInicio"],
        properties: {
          placeId:              { type: SchemaType.STRING },
          ordenSugerido:        { type: SchemaType.INTEGER },
          razonSeleccion:       { type: SchemaType.STRING },
          duracionEstimadaMin:  { type: SchemaType.INTEGER },
          horaSugeridaInicio:   { type: SchemaType.STRING },
          horaSugeridaFin:      { type: SchemaType.STRING },
          costoEstimadoMxn:     { type: SchemaType.INTEGER },
          tipsParaEstaParada:   { type: SchemaType.ARRAY, items: { type: SchemaType.STRING } },
          alternativaSiCerrado: { type: SchemaType.STRING },
        },
      },
    },
    metricas: {
      type: SchemaType.OBJECT,
      properties: {
        tiempoTotalMin:          { type: SchemaType.INTEGER },
        costoTotalEstimadoMxn:   { type: SchemaType.INTEGER },
        distanciaCaminadaMetros: { type: SchemaType.INTEGER },
        comidaIncluida:          { type: SchemaType.BOOLEAN },
        advertenciaClima:        { type: SchemaType.STRING },
      },
    },
  },
};

/**
 * Callable: genera una ruta turística con Gemini AI.
 *
 * Requiere App Check + usuario autenticado.
 * Rate limit: MAX_DAILY_ROUTES generaciones por usuario por día.
 *
 * Input:  { promptInput: string, useStructuredOutput?: boolean }
 * Output: { text: string }  (JSON si useStructuredOutput=true)
 *
 * Despliegue:
 *   1. firebase secrets:set GEMINI_API_KEY
 *   2. firebase deploy --only functions
 */
export const generateRoute = onCall(
  { enforceAppCheck: true, secrets: [geminiSecret] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Debes iniciar sesión");
    }

    const uid = request.auth.uid;
    const today = new Date().toISOString().slice(0, 10);
    const usageRef = admin
      .firestore()
      .collection("users")
      .doc(uid)
      .collection("usage")
      .doc(today);

    const usage = await usageRef.get();
    const currentCount: number = (usage.data()?.routesGenerated as number) ?? 0;
    if (currentCount >= MAX_DAILY_ROUTES) {
      throw new HttpsError("resource-exhausted", "Límite diario de rutas alcanzado");
    }

    const data = request.data as {
      promptInput: unknown;
      useStructuredOutput?: boolean;
    };

    const { promptInput, useStructuredOutput = true } = data;
    if (typeof promptInput !== "string" || promptInput.length > MAX_PROMPT_LENGTH) {
      throw new HttpsError("invalid-argument", "promptInput inválido");
    }

    const genAI = new GoogleGenerativeAI(geminiSecret.value());

    const generationConfig = useStructuredOutput
      ? {
          temperature: 0.3,
          maxOutputTokens: 2048,
          responseMimeType: "application/json",
          responseSchema: ROUTE_SCHEMA,
        }
      : {
          temperature: 0.3,
          maxOutputTokens: 1024,
        };

    const model = genAI.getGenerativeModel({
      model: "gemini-2.5-flash",
      generationConfig,
    });

    // Incrementar contador antes de llamar (previene spam en caso de fallo de red)
    await usageRef.set(
      { routesGenerated: currentCount + 1, updatedAt: Date.now() },
      { merge: true }
    );

    const result = await model.generateContent(promptInput);
    const text = result.response.text();

    // Validación básica de JSON cuando se usa structured output
    if (useStructuredOutput) {
      try {
        const parsed = JSON.parse(text);
        if (!parsed.paradas || !Array.isArray(parsed.paradas) || parsed.paradas.length < 2) {
          throw new HttpsError(
            "failed-precondition",
            "La IA generó una ruta con menos de 2 paradas. Intenta regenerar."
          );
        }
      } catch (e) {
        if (e instanceof HttpsError) throw e;
        throw new HttpsError("internal", "La IA devolvió una respuesta malformada.");
      }
    }

    return { text };
  }
);

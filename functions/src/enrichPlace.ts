import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";
import { GoogleGenerativeAI, SchemaType } from "@google/generative-ai";

if (admin.apps.length === 0) {
  admin.initializeApp();
}

const geminiSecret = defineSecret("GEMINI_API_KEY");
const db = admin.firestore();
const PLACES_COLLECTION = "lugares";

const ENRICHMENT_SCHEMA = {
  type: SchemaType.OBJECT,
  properties: {
    descripcionCorta:        { type: SchemaType.STRING },
    descripcionLarga:        { type: SchemaType.STRING },
    tipsVisita:              { type: SchemaType.ARRAY, items: { type: SchemaType.STRING } },
    historiaResumen:         { type: SchemaType.STRING },
    tipoActividad:           { type: SchemaType.STRING },
    duracionMinSugeridaMin:  { type: SchemaType.INTEGER },
    duracionMaxSugeridaMin:  { type: SchemaType.INTEGER },
    mejorMomentoDelDia:      { type: SchemaType.ARRAY, items: { type: SchemaType.STRING } },
    mejorTemporada:          { type: SchemaType.ARRAY, items: { type: SchemaType.STRING } },
    epocasEvitar:            { type: SchemaType.ARRAY, items: { type: SchemaType.STRING } },
    audienciaIdeal:          { type: SchemaType.ARRAY, items: { type: SchemaType.STRING } },
    aptoNinos:               { type: SchemaType.BOOLEAN },
    aptoMascotas:            { type: SchemaType.BOOLEAN },
    nivelDificultadFisica:   { type: SchemaType.INTEGER },
    precioNivel:             { type: SchemaType.INTEGER },
    precioPromedioMxn:       { type: SchemaType.INTEGER },
    entradaGratuita:         { type: SchemaType.BOOLEAN },
    tags:                    { type: SchemaType.ARRAY, items: { type: SchemaType.STRING } },
  },
};

/**
 * Callable CF: enriquece un lugar con Gemini.
 * Auth: requiere custom claim `admin: true`.
 * Parámetros: { placeId: string }
 */
export const enrichPlace = onCall(
  { secrets: [geminiSecret], enforceAppCheck: false },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Se requiere autenticación.");
    }
    if (request.auth.token["admin"] !== true) {
      throw new HttpsError("permission-denied", "Solo administradores.");
    }

    const placeId: string = request.data?.placeId;
    if (!placeId) throw new HttpsError("invalid-argument", "placeId requerido.");

    const doc = await db.collection(PLACES_COLLECTION).doc(placeId).get();
    if (!doc.exists) throw new HttpsError("not-found", `Lugar ${placeId} no encontrado.`);

    const spot = doc.data()!;
    const prompt = buildPrompt(spot);

    const genAI = new GoogleGenerativeAI(geminiSecret.value());
    const model = genAI.getGenerativeModel({
      model: "gemini-1.5-flash",
      generationConfig: {
        responseMimeType: "application/json",
        responseSchema: ENRICHMENT_SCHEMA as any,
        temperature: 0.3,
        maxOutputTokens: 2048,
      },
    });

    const result = await model.generateContent(prompt);
    const text = result.response.text();
    const enriched = JSON.parse(text);

    // Merge into Firestore (respeta manualOverride)
    const overrides: Record<string, boolean> = spot.enrichment?.manualOverride ?? {};
    const update: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(enriched)) {
      if (!overrides[key]) update[key] = value;
    }
    update["enrichment"] = {
      version:         (spot.enrichment?.version ?? 0) + 1,
      source:          "gemini-1.5-flash",
      confidenceScore: 0.75,
      manualOverride:  overrides,
    };

    await db.collection(PLACES_COLLECTION).doc(placeId).update(update);
    return { placeId, fieldsUpdated: Object.keys(update).length };
  }
);

function buildPrompt(spot: Record<string, unknown>): string {
  return `Eres un experto en turismo en Álamos, Sonora, México.
Analiza el siguiente lugar turístico y completa los campos solicitados en JSON.
Responde solo en español con datos reales y precisos.

Nombre: ${spot.nombre}
Categoría: ${spot.categoria}
Descripción: ${String(spot.descripcion ?? "").slice(0, 400)}
Dirección: ${spot.direccion}
Horarios: ${spot.horarios ?? spot.horariosTextoOriginal ?? ""}
Precio estimado: ${spot.precioEstimado ?? ""}

Instrucciones: descripcionCorta ≤ 140 chars, tipsVisita 3-5 items, tags 5-10 etiquetas libres en español, tipoActividad uno de INTERIOR/EXTERIOR/MIXTO.`;
}

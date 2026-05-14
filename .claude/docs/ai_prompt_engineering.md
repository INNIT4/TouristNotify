# AI Prompt Engineering — TrazaGo

## Overview

Route generation uses Google Gemini (`generativeai:0.9.0`) via `RouteGenerator.buildPrompt()` → `PreferencesActivity` → `MapsActivity`. The prompt is structured, deterministic, and constrained to exact place names from Firestore.

---

## Prompt Architecture

**Entry point**: `PreferencesActivity.kt` — `generateRouteWithAI()` and `invokeGenerateCF()`

**Prompt builder**: `RouteGenerator.buildPrompt(params: PromptParams): String`

### PromptParams fields

| Field | Source | Example |
|---|---|---|
| `budget` | TextInputLayout (MXN) | `"500"` |
| `time` | TextInputLayout (hours) | `"4"` |
| `interests` | CheckBox group | `["historia", "gastronomía"]` |
| `travelType` | Spinner | `"En familia"` |
| `pace` | Spinner | `"Relajado"` |
| `mobility` | Spinner | `"Caminando"` |
| `customRequest` | Optional EditText | `"Quiero ver arquitectura colonial"` |
| `placesForPrompt` | Firestore `lugares` — filtered by category | `"Plaza de Armas, Museo Costumbrista..."` |

### Prompt structure

```
# ROL
Eres un experto planificador de rutas turísticas para Álamos, Sonora, México.

# CONTEXTO GEOGRÁFICO
Álamos es un Pueblo Mágico colonial compacto (~1 km de radio caminable).
Las coordenadas del centro son lat:27.0275, lng:-108.94.

# PERFIL DEL VIAJERO
- Presupuesto: $<budget> MXN
- Tiempo disponible: <time> horas
- Intereses: <interests>
- Tipo de viaje: <travelType>
- Ritmo: <pace>
- Movilidad: <mobility>
[## Petición específica: <customRequest>]  ← omitido si blank

# LUGARES DISPONIBLES
- <placesForPrompt>

# RESTRICCIONES ABSOLUTAS
- SOLO usa nombres EXACTOS de la lista
- NO inventes lugares
- NO uses listas numeradas ni viñetas
- El orden de mención ES el orden de visita

# FORMATO DE RESPUESTA
Responde ÚNICAMENTE con los nombres de los lugares separados por comas, en orden de visita.
Ejemplo: Plaza de Armas, Museo Costumbrista de Sonora, Mirador El Perico
```

---

## Response Parsing

Gemini returns a comma-separated list of place names. The parser:

1. Splits on `,`
2. Trims whitespace
3. Filters empty entries
4. Attempts to resolve each name to a Firestore document ID via `nameToId: Map<String, String>`
5. If **all** names resolve → calls `navigateToMapWithRouteIds(placeIds)` → `MapsActivity` uses `FieldPath.documentId()` query (efficient)
6. If **any** name doesn't resolve → falls back to `navigateToMapWithRoute(placeNames)` → `MapsActivity` uses `whereIn("nombre", names)` (legacy path)

The `nameToId` map is built before the Gemini call from the same Firestore batch that populates `placesForPrompt`:
```kotlin
val nameToId = filteredPlaces.associate { it.name to it.id }
```

---

## Timeout & Error Handling

- **Timeout**: `AppConstants.AI_TIMEOUT_MS = 60_000L` (60 seconds)
- On timeout or exception: shows error Snackbar, re-enables generate button
- Loading indicator shown during call; button disabled to prevent double-submit

---

## Cloud Function path

`invokeGenerateCF()` calls the `generateRoute` Cloud Function (Firebase Functions) instead of calling Gemini directly from the device. Used as fallback or primary path based on `ConfigManager` flags. The CF uses the same `nameToId` resolution logic post-response.

---

## Constraints & Known Limitations

- **DB-005**: `placesForPrompt` is built from Room cache. LIKE search on the name uses leading wildcard (`%query%`) which bypasses the B-tree index. Filtering for the prompt list uses `getSpotsByCategory` which does use the `categoria` index.
- The prompt hardcodes Álamos, Sonora geography — not reusable for other cities without modifying `RouteGenerator`.
- Gemini may occasionally return place names with minor spelling differences — the fallback to `whereIn("nombre")` handles this gracefully.
- `customRequest` is passed as a quoted string inside the prompt (`"..."`) to prevent prompt injection from user input breaking the structure.

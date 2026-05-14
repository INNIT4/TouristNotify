# Prompt Engineering Review — TrazaGo (Gemini AI)

## Estado general

La mayoría de hallazgos críticos ya estaban parcialmente implementados en sesiones anteriores. Esta sesión completó los pendientes.

---

## Hallazgos

### ~~P0 — Críticos~~ ✅

~~**[PROMPT-001] Prompt injection sin sanitizar — `PreferencesActivity.kt:213-216`**~~
~~`customRequest` se interpola directamente sin limpieza.~~

**Resuelto**: `PromptSanitizer.sanitizeCustomRequest()` aplica:
- `.take(200)` truncado duro
- Aplana `\n\r\t#` y backticks
- Reemplaza keywords de injection (`ignore`, `system`, etc.) con `[filtrado]`
- Referenciado en `PreferencesActivity` línea 268: `val safeCustom = PromptSanitizer.sanitizeCustomRequest(customRequest)`

~~**[PROMPT-002] Nombres de lugares de Firestore se interpolan sin escape**~~

**Resuelto**: `PromptSanitizer.sanitizePlaceField()` limpia todos los campos antes de interpolación. Aplicado en `fetchPlacesAndThenGenerateRoute()`.

~~**[PROMPT-003] Parsing por substring produce falsos positivos y fallas silenciosas**~~
~~Si "Templo" y "Templo de la Purísima Concepción" ambos están en la lista, `indexOf("Templo")` matchea dentro del nombre largo.~~

**Resuelto**: `generateRouteWithAI()` ahora:
1. Ordena `knownPlaceNames` por longitud descendente (nombres largos primero)
2. Mantiene `consumedRanges: MutableList<IntRange>` — si una posición ya fue consumida por un nombre más largo, el nombre corto se descarta
3. Compile error con `placeWithIndex` referenciado antes de ser definido también corregido

### ~~P1 — Altos~~ ✅

~~**[PROMPT-004] Sin GenerationConfig ni SafetySettings**~~

**Resuelto**: `generationConfig { temperature = 0.3f; topP = 0.8f; topK = 20; maxOutputTokens = 1024 }` + `SafetySettings` para 4 `HarmCategory`. Ya estaba implementado.

~~**[PROMPT-005] Fallback cuando no hay lugares es Toast genérico**~~ ✅
**Resuelto Sprint 3**: `handleRouteResult()` ahora llama `showEmptyResponseDialog(rawResponse)` en lugar de Toast. Muestra AlertDialog con título "No se generó ninguna ruta", mensaje explicativo con 3 sugerencias de ajuste, y botón "Ver respuesta IA" (solo en DEBUG) que muestra los primeros 800 chars de la respuesta cruda para diagnóstico. El path Cloud Function (`invokeGenerateCF`) también usa el mismo dialog cuando `responseText.isNullOrBlank()`.

### P2 — Medios

~~**[PROMPT-008] Sin filtrado de lugares por categoría antes del prompt**~~
~~Se envían todos los 50+ lugares al prompt (~1200+ tokens).~~

**Resuelto**: `fetchPlacesAndThenGenerateRoute()` ahora filtra `allPlaces` por `interests` antes de construir `placesForPrompt`. Si el filtro produce < 3 lugares, hace fallback al set completo para no dejar la ruta vacía.

~~**[PROMPT-006] One-shot example con variable vacía**~~ ✅
**Resuelto**: `RouteGenerator.buildPrompt()` ahora incluye sección `# FORMATO DE RESPUESTA` con ejemplo literal de salida esperada: `"Plaza de Armas, Museo Costumbrista de Sonora, Mirador El Perico"`. El ejemplo usa nombres reales del seeder, no hardcoded de intereses.

**[PROMPT-007] Coordenadas con baja precisión** — Sin cambios (bajo impacto).

---

## Cambios aplicados en esta sesión

| Cambio | Archivo |
|---|---|
| Fix compile error `placeWithIndex` + PROMPT-003 (consumed ranges) | `PreferencesActivity.kt` |
| PROMPT-008: filtrado por intereses antes de construir `placesForPrompt` | `PreferencesActivity.kt` |
| `PromptSanitizer.kt` (PROMPT-001/002) | ya existía desde sesión anterior |
| `GenerationConfig` + `SafetySettings` (PROMPT-004) | ya existía desde sesión anterior |

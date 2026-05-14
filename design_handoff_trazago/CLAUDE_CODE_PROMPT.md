# Prompt sugerido para Claude Code

Copia y pega esto en Claude Code, dentro del repo de tu app TrazaGo, después de poner esta carpeta `design_handoff_trazago/` en la raíz del proyecto:

---

Hola Claude. En la carpeta `design_handoff_trazago/` tienes un paquete de diseño para rediseñar la app TrazaGo.

**Antes de tocar código:**
1. Lee `design_handoff_trazago/README.md` completo — contiene las specs de las 6 pantallas, los design tokens (colores, tipografía, espaciados) y las interacciones esperadas.
2. Abre `design_handoff_trazago/TrazaGo Prototype.html` en un navegador para ver el prototipo en acción. Las pantallas reales están definidas en `design_handoff_trazago/app.jsx`.
3. Explora el repo actual y dime:
   - Qué stack usa (Android nativo / Flutter / React Native / etc.)
   - Dónde están los design tokens actuales (colors.xml, theme.ts, tailwind.config, etc.)
   - Qué pantallas ya existen y cuáles habría que crear desde cero

**Importante:**
- Los archivos HTML/JSX del handoff son **referencias visuales**, no código a copiar. Recrea los diseños usando los patrones, librerías y design system del repo actual.
- Respeta los valores exactos de los design tokens del README (hex, px, pesos de fuente).
- Si un valor no está documentado, pregúntame antes de inventar.

**Plan que espero de ti:**
1. Resumen del stack detectado y de los archivos clave que vas a tocar.
2. Lista de pantallas a migrar en orden de prioridad.
3. Propuesta de PR pequeño (1 pantalla o solo los tokens) para validar el approach antes de seguir.

No empieces a escribir código hasta que confirme el plan.

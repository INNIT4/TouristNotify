package com.joseibarra.touristnotify.admin

import com.joseibarra.touristnotify.TouristSpot

object EnrichmentPromptBuilder {

    fun buildPrompt(spot: TouristSpot): String = """
Eres un experto en turismo en Álamos, Sonora, México.
Analiza el siguiente lugar turístico y completa TODOS los campos del JSON.
Responde ÚNICAMENTE en español con datos precisos y verídicos para este lugar.

Lugar: ${spot.nombre}
Categoría: ${spot.categoria}
Descripción actual: ${spot.descripcion.take(500)}
Dirección: ${spot.direccion}
Teléfono: ${spot.telefono}
Sitio web: ${spot.sitioWeb}
Horarios: ${spot.horarios.ifBlank { spot.horariosTextoOriginal }}
Precio estimado: ${spot.precioEstimado}
Subcategorías: ${spot.subcategorias.joinToString()}

Instrucciones por campo:
- descripcionCorta: 1-2 oraciones atractivas, máximo 140 caracteres.
- descripcionLarga: 3-5 párrafos detallados sobre historia, arquitectura, experiencia, valor cultural.
- tipsVisita: 3-5 consejos prácticos únicos para este lugar (horario óptimo, qué llevar, qué no perderse).
- historiaResumen: 1-2 párrafos sobre el origen e historia del lugar.
- tipoActividad: "INTERIOR" si es principalmente bajo techo, "EXTERIOR" si es al aire libre, "MIXTO" si combina ambos.
- duracionMinSugeridaMin y duracionMaxSugeridaMin: tiempo de visita realista en minutos.
- mejorMomentoDelDia: lista con valores de ["MANANA", "MEDIODIA", "TARDE", "NOCHE"] según cuándo es mejor visitarlo.
- mejorTemporada: meses o estaciones del año recomendados para visitar (ej: ["Octubre", "Noviembre", "Diciembre"]).
- epocasEvitar: meses o condiciones a evitar (ej: ["Julio", "Agosto"] por calor extremo).
- audienciaIdeal: lista con valores de ["SOLO", "PAREJA", "FAMILIA", "AMIGOS", "NINOS", "MAYORES"].
- aptoNinos: true si es adecuado para niños menores de 12 años.
- aptoMascotas: true si se permiten mascotas.
- nivelDificultadFisica: 1=muy fácil (sin escalones), 5=muy difícil (senderismo exigente).
- precioNivel: 0=gratis, 1=económico (<100 MXN), 2=moderado (100-300 MXN), 3=caro (300-700 MXN), 4=muy caro (>700 MXN).
- precioPromedioMxn: precio promedio por persona en pesos mexicanos (0 si es gratis).
- entradaGratuita: true si no tiene costo de entrada.
- tags: 5-10 etiquetas libres en español (ej: ["colonial", "instagrameable", "mirador", "histórico", "arquitectura"]).
- accesibilidad: estima basado en el tipo de lugar.
- servicios: estima basado en el tipo de lugar.
- restaurante: completar solo si la categoría es restaurante, cafetería o similar.
""".trimIndent()
}

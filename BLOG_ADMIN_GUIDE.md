# 📝 Guía de Administración del Blog - Oficina de Turismo de Álamos

## 🎯 Propósito
Este documento describe cómo la **Oficina de Turismo de Álamos** puede gestionar el contenido del blog en la aplicación TrazaGo.

---

## 🔐 Acceso Autorizado

### Emails Autorizados
Solo el personal de la Oficina de Turismo con los siguientes emails puede crear y administrar posts:

- `turismo@alamos.gob.mx`
- `admin@turismoalamos.gob.mx`
- `info@turismoalamos.gob.mx`
- `comunicacion@alamos.gob.mx`
- `director.turismo@alamos.gob.mx`

### Cómo Registrarse
1. Abrir la aplicación TrazaGo
2. Ir a **Registro**
3. Crear cuenta con uno de los emails autorizados
4. Verificar el email
5. Iniciar sesión

---

## ✍️ Cómo Crear un Post

### Paso 1: Acceder al Blog
1. Iniciar sesión con email autorizado
2. En el menú principal, tocar **"📝 Blog de Consejos"**
3. Verás un botón flotante **+** en la esquina inferior derecha
4. Tocar el botón **+**

### Paso 2: Llenar el Formulario
**Título del Post:**
- Debe ser claro y descriptivo
- Ejemplo: "10 Lugares Imperdibles en Álamos"

**Seleccionar Categoría:**
- 💡 **Consejos**: Tips prácticos para visitantes
- 📜 **Historia**: Información histórica de Álamos
- 🍽️ **Gastronomía**: Restaurantes, comida típica
- 🎭 **Cultura**: Eventos culturales, tradiciones
- 🌿 **Naturaleza**: Ecoturismo, rutas naturales
- 🎉 **Eventos**: Festivales, conciertos, eventos especiales

**Contenido del Post:**
- Escribir el contenido completo
- Puede incluir múltiples párrafos
- Sea descriptivo y útil para los turistas

**Post Destacado:**
- Activar este switch si el post debe aparecer primero
- Ideal para noticias importantes o eventos próximos
- Los posts destacados tienen una estrella ⭐

### Paso 3: Publicar
- Tocar el botón **"📤 Publicar Post"**
- El post aparecerá inmediatamente en el blog
- Todos los usuarios verán: "Por Oficina de Turismo de Álamos"

---

## 📊 Métricas del Blog

Cada post registra automáticamente:
- **👁️ Vistas**: Incrementa cuando alguien abre el post completo
- **❤️ Likes**: Los usuarios pueden dar "me gusta"

Estas métricas ayudan a entender qué contenido es más popular.

---

## 💡 Mejores Prácticas

### Títulos Efectivos
✅ **Bueno**: "Festival Alfonso Ortiz Tirado 2026 - Fechas y Programa"
❌ **Malo**: "Festival"

### Contenido de Calidad
- Sé específico con fechas, horarios y ubicaciones
- Incluye información práctica (precios, contacto)
- Usa párrafos cortos para facilitar la lectura en móvil
- Revisa ortografía antes de publicar

### Categorización
- Usa la categoría más apropiada
- Si un post tiene múltiples temas, elige el predominante
- Consejos: información práctica y útil
- Historia: contexto histórico y cultural
- Gastronomía: restaurantes, comida, bebidas
- Cultura: tradiciones, arte, música
- Naturaleza: ecoturismo, paisajes, rutas
- Eventos: festivales, conciertos, eventos temporales

### Posts Destacados
- Usa con moderación (máximo 2-3 destacados activos)
- Ideal para:
  - Eventos próximos importantes
  - Avisos urgentes (cierres temporales, etc.)
  - Contenido estacional

---

## 🔒 Seguridad

### Protección Implementada
- Solo emails autorizados pueden crear posts
- No se pueden crear posts desde emails externos
- Sistema de verificación en cada acceso
- Intentos no autorizados se bloquean automáticamente

### Si Necesitas Agregar Más Emails
Contactar al equipo de desarrollo para agregar nuevos emails autorizados en el archivo `AdminConfig.kt`.

---

## 📱 Ejemplos de Posts

### Ejemplo 1: Evento
**Título:** Festival Alfonso Ortiz Tirado 2026
**Categoría:** 🎉 Eventos
**Contenido:**
```
Del 20 al 27 de enero de 2026, Álamos se viste de gala para recibir
el Festival Alfonso Ortiz Tirado, el evento cultural más importante
de la región.

Programa:
- Ópera en el Templo de la Purísima Concepción
- Conciertos nocturnos en la Plaza de Armas
- Exposiciones de arte en la Casa de la Cultura

Entrada general: $250 pesos
Boletos en taquilla del teatro o en línea.
```
**Destacado:** ✅ Sí

### Ejemplo 2: Consejo
**Título:** Mejor Época para Visitar Álamos
**Categoría:** 💡 Consejos
**Contenido:**
```
La mejor época para visitar Álamos es de noviembre a marzo,
cuando el clima es más fresco y agradable.

Temperatura promedio: 18-25°C
Época de lluvias: Julio a Septiembre (evitar)

Recomendamos visitar durante el Festival Alfonso Ortiz Tirado
en enero para una experiencia cultural completa.
```
**Destacado:** ❌ No

### Ejemplo 3: Gastronomía
**Título:** Dónde Comer: Los Mejores Restaurantes
**Categoría:** 🍽️ Gastronomía
**Contenido:**
```
Álamos ofrece una experiencia gastronómica única que mezcla
la cocina tradicional sonorense con toques internacionales.

Restaurantes recomendados:

1. La Mansión (Cocina Gourmet)
   - Calle Juárez #5
   - Tel: 647-428-0000

2. Las Palmeras (Cocina Regional)
   - Plaza de Armas
   - Especialidad: Carne asada sonorense

3. El Portón (Desayunos)
   - Av. Obregón #23
   - Famoso por sus chilaquiles
```
**Destacado:** ❌ No

---

## ❓ Preguntas Frecuentes

**P: ¿Puedo editar un post después de publicarlo?**
R: Actualmente no se puede editar directamente. Para modificar un post, crear uno nuevo y solicitar al equipo técnico eliminar el anterior.

**P: ¿Cuántos posts puedo crear?**
R: No hay límite. Crea tantos posts como sea necesario para mantener informados a los visitantes.

**P: ¿Los posts se eliminan automáticamente?**
R: No, los posts permanecen indefinidamente. Contactar al equipo técnico para eliminar posts obsoletos.

**P: ¿Puedo agregar imágenes?**
R: En la versión actual no. Esta funcionalidad estará disponible en una actualización futura.

**P: ¿Qué pasa si pierdo acceso a mi email autorizado?**
R: Contactar al equipo de desarrollo para transferir permisos a un nuevo email.

---

## 📞 Soporte Técnico

Para asistencia técnica o reportar problemas:
- Crear un issue en el repositorio del proyecto
- Contactar al equipo de desarrollo

---

**Última actualización:** Enero 2026
**Versión de la aplicación:** 1.0

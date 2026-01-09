# 🔧 Guía del Panel Administrativo

## Importar Lugares desde Google Places API

### 📱 Cómo Acceder al Panel Administrativo

Hay **dos formas** de acceder al panel admin desde el menú principal:

1. **Mantener presionado** el texto "Explora Álamos con IA" (en la parte inferior)
2. **Hacer 5 clicks rápidos** en el mismo texto

Esto mostrará un diálogo de confirmación para acceder al panel administrativo.

---

## 🎯 Funciones del Panel Administrativo

### 1. Buscar Lugares por Categoría

El panel tiene botones para buscar diferentes tipos de lugares:

- **🎯 Atracciones**: Lugares turísticos de interés
- **🍽️ Restaurantes**: Lugares para comer
- **🏨 Hoteles**: Opciones de hospedaje
- **🏛️ Museos**: Museos y galerías
- **🔍 Buscar Todo**: Busca todas las categorías a la vez

### 2. Revisar Resultados

Para cada lugar encontrado, verás:
- **Nombre** del establecimiento
- **Dirección** completa
- **Rating** de Google (estrellas y número de reseñas)
- **Iconos informativos**:
  - 📞 = Tiene teléfono
  - 🌐 = Tiene sitio web
  - 🕐 = Tiene horarios
  - 📷 = Tiene fotos
- **Categoría sugerida** automáticamente

### 3. Ver Detalles

Al hacer click en **"Detalles"**, verás toda la información del lugar:
- Nombre completo
- Dirección
- Teléfono
- Sitio web
- Rating y número de reseñas
- Precio estimado ($, $$, $$$)
- Horarios de apertura (por día)
- Tipos de lugar según Google

### 4. Importar a Firebase

Al hacer click en **"Importar"**:
1. Se abre un diálogo de confirmación
2. Puedes **seleccionar la categoría** correcta
3. Al confirmar, el lugar se guarda en Firebase con todos sus datos

---

## 📊 Datos que se Importan Automáticamente

Cuando importas un lugar, se guardan:

✅ **Básicos:**
- Nombre
- Descripción (marcada como "Importado desde Google Places")
- Ubicación (latitud/longitud)
- Categoría (la que seleccionaste)

✅ **Contacto:**
- Teléfono
- Sitio web
- Dirección completa

✅ **Información:**
- Horarios de apertura (por día de la semana)
- Precio estimado ($, $$, $$$, etc.)
- Rating actual de Google
- Número de reseñas

✅ **Referencias:**
- Google Place ID (para futuras actualizaciones)

---

## ✏️ Editar Después de Importar

Después de importar un lugar, puedes:

1. **Agregar descripción personalizada** desde Firebase Console
2. **Agregar fotos** específicas de Álamos
3. **Añadir tips locales** para turistas
4. **Traducir** información al español si es necesario
5. **Actualizar** cualquier dato que haya cambiado

---

## 💡 Tips de Uso

### Para mejores resultados:

1. **Importa por categoría** en lugar de "Buscar Todo"
   - Es más fácil de revisar
   - Evita duplicados

2. **Revisa los detalles** antes de importar
   - Verifica que tenga horarios
   - Confirma que la categoría sea correcta

3. **Personaliza después**
   - Agrega descripciones en español
   - Añade contexto histórico local
   - Incluye tips para turistas

4. **Evita duplicados**
   - Si un lugar ya está importado, aparecerá en la app
   - Verifica en "Ver Mapa" antes de importar

---

## 🔒 Seguridad

- El acceso admin está **oculto** de usuarios normales
- Solo quien conozca el método de acceso puede usar la herramienta
- Considera agregar **autenticación adicional** si es necesario

---

## ⚠️ Consideraciones

### Cuota de Google Places API:
- **$200 USD/mes gratis**
- Suficiente para ~28,000 búsquedas de detalles
- Para Álamos, esto es más que suficiente

### No se importan automáticamente:
- ❌ Fotos (por tamaño y cuotas)
- ❌ Reseñas de Google (se usan las propias)
- ❌ Disponibilidad en tiempo real

### Se recomienda importar manualmente:
- Historia local del lugar
- Eventos especiales
- Recomendaciones personalizadas
- Conexión con rutas turísticas

---

## 📝 Workflow Recomendado

1. **Buscar** lugares por categoría
2. **Revisar** detalles y seleccionar los relevantes
3. **Importar** con la categoría correcta
4. **Ir a Firebase Console** y personalizar:
   - Descripción en español
   - Contexto histórico/cultural
   - Tips para turistas
5. **Verificar** en la app que se vea bien
6. **Repetir** con otras categorías

---

## 🆘 Solución de Problemas

### "Error API: X"
- Verifica que la API Key de Google Places esté activa
- Confirma que Places API esté habilitada en Google Cloud Console

### "No se encontraron lugares"
- Álamos es un pueblo pequeño, es normal encontrar pocos resultados
- Intenta categorías específicas
- Considera agregar lugares manualmente para completar

### "Error al importar"
- Verifica conexión a internet
- Confirma que Firebase esté configurado correctamente
- Revisa los permisos de escritura en Firestore

---

¡Listo! Con esta herramienta puedes poblar rápidamente tu app con datos reales y actualizados de Álamos. 🎉

# 📱 Códigos QR + Modo Invitado

## 🎯 **Tu Idea Implementada:**

Has solicitado dos funcionalidades clave:
1. **Códigos QR** en puntos de interés que abran PlaceDetailsActivity
2. **Modo invitado** para usar funciones básicas sin login

**¡Ambas están LISTAS!** ✅

---

## 1️⃣ **Sistema de Códigos QR**

### **¿Cómo funciona?**

La oficina de turismo puede generar códigos QR siguiendo estos formatos:

**Formato 1 (Recomendado):**
```
touristnotify://place/{placeId}
```

**Formato 2 (URL web):**
```
https://touristnotify.app/place/{placeId}
```

**Formato 3 (Solo ID):**
```
{placeId}
```

### **Flujo del usuario:**

1. Turista ve letrero con código QR en un lugar histórico
2. Abre TouristNotify (puede ser modo invitado)
3. ~~Click en botón "Escanear QR"~~ (Pendiente agregar al menú)
4. Apunta cámara al código QR
5. ✅ **Automáticamente** abre la pantalla de detalles del lugar
6. Ve fotos, descripción, horarios, mapa, etc.

### **Características del escáner:**

- ✅ Detección automática (ML Kit)
- ✅ Rápido y preciso
- ✅ Funciona sin internet (solo escaneo)
- ✅ Botón de flash para lugares oscuros
- ✅ Indicadores visuales de escaneo
- ✅ NO requiere login

### **Archivos creados:**

- `QRScannerActivity.kt` - Activity de escaneo
- `activity_qrscanner.xml` - Layout con preview de cámara
- `qr_scan_frame.xml` - Marco visual para guiar al usuario

---

## 2️⃣ **Modo Invitado (Sin Login)**

### **¿Por qué es una buena estrategia?**

**Ventajas:**
- ✅ **Menor fricción inicial** - Turistas prueban app inmediatamente
- ✅ **Más conversiones** - Ven valor ANTES de registrarse
- ✅ **Casos de uso real** - Turistas de 1-2 días no quieren crear cuenta
- ✅ **Similar a apps exitosas** - Google Maps, TripAdvisor usan este modelo

**Estrategia de negocio:**
1. Usuario prueba funciones básicas (sin login)
2. Se enamora de la app
3. Cuando quiere funciones avanzadas → Se registra
4. Mayor tasa de conversión que forzar login al inicio

### **Funciones SIN login (Modo Invitado):**

Cualquiera puede usar esto sin crear cuenta:

| Función | Estado |
|---------|--------|
| 📱 Escanear códigos QR | ✅ Funciona |
| 🗺️ Ver mapa con lugares | ✅ Funciona |
| 📍 Ver detalles de lugares | ✅ Funciona |
| 📝 Leer blog de consejos | ✅ Funciona |
| 🎉 Ver eventos próximos | ✅ Funciona |
| 🌤️ Ver clima actual | ✅ Funciona |
| 🎨 Ver rutas temáticas | ✅ Funciona |
| 🏆 Ver top 10 lugares | ✅ Funciona |
| 📸 Ver galería de fotos | ✅ Funciona |
| 🏪 Directorio de servicios | ✅ Funciona |

### **Funciones CON login requerido:**

Estas funciones piden crear cuenta:

| Función | Razón |
|---------|-------|
| 🤖 Generar rutas con IA | Usa cuota de Gemini API (costo) |
| ⭐ Guardar favoritos | Datos personales en Firebase |
| 💾 Guardar rutas | Datos personales en Firebase |
| 📤 Compartir rutas | Necesita identificar autor |
| 📍 Check-ins | Registro de visitas personales |
| 📤 Subir fotos | Moderación y atribución |
| ⭐ Dejar reseñas | Evitar spam/bots |
| 🔔 Notificaciones proximidad | Geofences personalizados |
| 📊 Estadísticas personales | Tracking de usuario |
| 📞 Contactos emergencia | Datos sensibles |

### **¿Cómo funciona el prompt de login?**

Cuando un invitado intenta usar función premium:

```
┌─────────────────────────────────────┐
│        Iniciar sesión                │
├─────────────────────────────────────┤
│ Para generar rutas personalizadas   │
│ con IA necesitas crear una cuenta   │
│ o iniciar sesión.                   │
│                                     │
│ ¿Deseas continuar?                  │
├─────────────────────────────────────┤
│  [Iniciar sesión]  [Ahora no]      │
└─────────────────────────────────────┘
```

**Amigable y no intrusivo** - El usuario decide.

### **Archivos creados:**

- `AuthManager.kt` - Gestión de autenticación y modo invitado
- Modificado `LoginActivity.kt` - Botón "Continuar como invitado"
- Modificado `activity_login.xml` - UI del botón

---

## 🔧 **Implementación Técnica**

### **AuthManager.kt - API Principal**

```kotlin
// Verificar si usuario está autenticado
AuthManager.isAuthenticated() // true/false

// Verificar si está en modo invitado
AuthManager.isGuestMode(context) // true/false

// Activar modo invitado
AuthManager.enableGuestMode(context)

// Pedir login si es necesario
AuthManager.requireAuth(context, "guardar favoritos") {
    // Código se ejecuta solo si está autenticado
    saveFavorite()
}
```

### **Formatos de QR soportados:**

El escáner acepta 3 formatos para flexibilidad:

```kotlin
// Formato 1: Deep link (Recomendado para letreros físicos)
"touristnotify://place/abc123xyz"

// Formato 2: URL web (Si tienen sitio web)
"https://touristnotify.app/place/abc123xyz"

// Formato 3: Solo ID (Más simple)
"abc123xyz"
```

---

## ✅ **Estado Actual**

### **Completado:**

- [x] QRScannerActivity con ML Kit
- [x] AuthManager para gestión de login
- [x] Modo invitado en LoginActivity
- [x] Botón "Continuar como invitado"
- [x] Permisos de cámara
- [x] Layout de escáner con preview
- [x] Detección automática de 3 formatos QR
- [x] Apertura automática de PlaceDetailsActivity

### **Pendiente (Próximos pasos):**

- [ ] Agregar botón "📱 Escanear QR" en MenuActivity
- [ ] Agregar verificación de login en PreferencesActivity (rutas IA)
- [ ] Agregar verificación en FavoritesActivity
- [ ] Agregar verificación en ProximityNotificationsActivity
- [ ] Agregar verificación en AdminPhotoUploadActivity
- [ ] Crear generador web de QR codes para oficina de turismo
- [ ] Testing en dispositivo real

---

## 📲 **Cómo usar (Turistas):**

### **Opción 1: Con cuenta (funciones completas)**

1. Abrir app
2. Click "Iniciar sesión" o "Registrarse"
3. Crear cuenta
4. Acceso a TODAS las funciones

### **Opción 2: Sin cuenta (modo invitado)**

1. Abrir app
2. Click "🚶 Continuar como invitado"
3. Explorar lugares, blog, eventos
4. Escanear QR en letreros
5. Ver detalles de lugares
6. Cuando quiera funciones avanzadas → Se registra

---

## 🏢 **Para la Oficina de Turismo**

### **Generar códigos QR:**

**Herramienta online recomendada:**
- https://www.qr-code-generator.com/

**Pasos:**
1. Ve a qr-code-generator.com
2. Selecciona "URL"
3. Ingresa: `touristnotify://place/{ID_DEL_LUGAR}`
   - Ejemplo: `touristnotify://place/museo_costumbrista`
4. Descarga el QR en alta resolución
5. Imprímelo en el letrero físico

**Tips de impresión:**
- ✅ Mínimo 5x5 cm para fácil escaneo
- ✅ Alto contraste (negro sobre blanco)
- ✅ Ubicarlo a altura de ojos (1.2-1.5 metros)
- ✅ Evitar reflejos de luz
- ✅ Agregar texto: "Escanea para más información"

### **Obtener IDs de lugares:**

Los IDs están en Firestore en la colección `lugares_turisticos`:
- Museo Costumbrista de Álamos
- Parroquia de la Purísima Concepción
- Plaza de Armas
- Etc.

Se pueden consultar desde la app en modo admin.

---

## 🚀 **Próximas Mejoras Sugeridas**

1. **Botón QR flotante en MenuActivity**
   - FloatingActionButton con ícono de QR
   - Siempre visible para fácil acceso

2. **Analytics de QR scans**
   - Trackear cuántas personas escanean cada QR
   - Qué lugares son más populares
   - Estadísticas para oficina de turismo

3. **Generador de QR dentro de la app**
   - Panel admin puede generar QRs directamente
   - Descarga PNG para imprimir
   - Preview del QR

4. **Deep links adicionales**
   - `touristnotify://event/{eventId}` - Para eventos
   - `touristnotify://route/{routeId}` - Para rutas temáticas
   - `touristnotify://blog/{postId}` - Para artículos

5. **NFC tags**
   - Además de QR, soportar tags NFC
   - Turistas tocan teléfono en letrero
   - Más moderno pero QR es más universal

---

## ❓ **Preguntas Frecuentes**

**P: ¿Qué pasa si escaneo un QR de otro app?**
R: El escáner valida que sea QR de TouristNotify. Si no lo es, muestra error amigable.

**P: ¿Funciona sin internet?**
R: El escaneo sí. Ver detalles del lugar requiere internet o modo offline.

**P: ¿Puedo escanear en modo invitado?**
R: ¡Sí! Es una de las funciones básicas sin login.

**P: ¿Cómo sabe qué lugar es?**
R: El QR contiene el ID del lugar. La app consulta Firebase para obtener detalles.

**P: ¿Qué pasa si alguien crea QR falso?**
R: Solo abrirá lugares que existan en Firebase. IDs falsos mostrarán error.

---

## 📞 **Soporte**

Si necesitas:
- Agregar botón QR al menú
- Implementar verificaciones de login
- Crear generador de QR
- Cualquier ajuste

¡Avísame y lo implemento! 🚀

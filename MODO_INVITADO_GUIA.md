# 🚶 Modo Invitado + Sistema de Autenticación

## ✅ **Implementación Completa**

El sistema de modo invitado permite a los turistas explorar funciones básicas sin crear cuenta, pero requiere autenticación para funciones premium/costosas/personales.

---

## 🎯 **Estrategia: Prueba Antes de Compromiso**

### **Beneficios del Modo Invitado:**

- ✅ **Menor fricción inicial** - Explora sin barreras
- ✅ **Prueba de valor** - Ve qué ofrece la app antes de registrarte
- ✅ **UX no intrusiva** - Solo pide login cuando es necesario
- ✅ **Alineado con uso real** - Turistas quieren info rápida

### **¿Por qué no todo es gratis?**

- 🤖 **Costos de API** - Gemini AI para rutas cuesta dinero
- 🔒 **Datos personales** - Favoritos, rutas, estadísticas
- 🛡️ **Moderación** - Fotos y reseñas requieren identificación
- 📊 **Analytics** - Necesitamos saber quién usa qué

---

## 📋 **Funciones por Modo**

### **✅ Funciones SIN Login (Modo Invitado)**

| Función | Actividad | ¿Por qué es gratis? |
|---------|-----------|---------------------|
| 📱 Escanear códigos QR | PlaceDetailsActivity | Deep links nativos, sin costo |
| 🗺️ Ver mapa con lugares | MapsActivity | Info pública del destino |
| 📍 Ver detalles de lugares | PlaceDetailsActivity | Info pública del destino |
| 📝 Leer blog de consejos | BlogActivity | Contenido promocional |
| 🎉 Ver eventos | EventsActivity | Info pública del destino |
| 🌤️ Ver clima | WeatherActivity | API gratuita |
| 🎨 Ver rutas temáticas | ThemedRoutesActivity | Contenido curado |
| 📸 Ver galería de fotos | PhotoGalleryActivity | Contenido público |
| 📞 Ver contactos emergencia | ContactsActivity | Info de seguridad pública |
| 🏆 Ver top lugares | TopPlacesActivity | Rankings públicos |
| 🔍 Buscar lugares | GlobalSearchActivity | Búsqueda básica |
| 🏨 Ver directorio servicios | ServicesDirectoryActivity | Info pública |

### **🔒 Funciones CON Login Requerido**

| Función | Actividad | ¿Por qué requiere login? | Mensaje al usuario |
|---------|-----------|--------------------------|-------------------|
| 🤖 Generar rutas IA | PreferencesActivity | Cuesta dinero (Gemini API) | "generar rutas personalizadas con IA" |
| ⭐ Guardar favoritos | PlaceDetailsActivity | Datos personales | "guardar favoritos" |
| 📋 Ver favoritos | FavoritesActivity | Datos personales | "ver tus favoritos" |
| 💾 Guardar rutas | MapsActivity | Datos personales | "guardar rutas" |
| 🗺️ Ver mis rutas | MyRoutesActivity | Datos personales | "ver tus rutas guardadas" |
| 📍 Hacer check-ins | PlaceDetailsActivity | Registro de visitas | "hacer check-ins en lugares" |
| ⭐ Dejar reseñas | PlaceDetailsActivity | Moderación necesaria | "dejar reseñas" |
| 📤 Subir fotos | AdminPhotoUploadActivity | Moderación necesaria | "subir fotos" |
| 🔔 Notificaciones | ProximityNotificationsActivity | Geofences personalizados | "activar notificaciones de proximidad" |
| 📊 Ver estadísticas | StatsActivity | Datos personales | "ver estadísticas personales" |

---

## 🔧 **Arquitectura Técnica**

### **AuthManager.kt - Núcleo del Sistema**

```kotlin
object AuthManager {
    // Verificar si el usuario está autenticado
    fun isAuthenticated(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    // Verificar si está en modo invitado
    fun isGuestMode(context: Context): Boolean {
        return context.getSharedPreferences("TrazaGoPrefs", Context.MODE_PRIVATE)
            .getBoolean("guest_mode_enabled", false)
    }

    // Habilitar modo invitado
    fun enableGuestMode(context: Context) {
        context.getSharedPreferences("TrazaGoPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("guest_mode_enabled", true)
            .apply()
    }

    // Requerir autenticación para una acción
    fun requireAuth(
        context: Context,
        actionName: String,
        onAuthConfirmed: () -> Unit
    ): Boolean {
        if (isAuthenticated()) {
            onAuthConfirmed()
            return true
        }

        // Mostrar diálogo amigable
        AlertDialog.Builder(context)
            .setTitle("Iniciar sesión")
            .setMessage("Para $actionName necesitas crear una cuenta o iniciar sesión.\n\n¿Deseas continuar?")
            .setPositiveButton("Iniciar sesión") { _, _ ->
                val intent = Intent(context, LoginActivity::class.java)
                intent.putExtra("RETURN_AFTER_LOGIN", true)
                context.startActivity(intent)
            }
            .setNegativeButton("Ahora no", null)
            .show()

        return false
    }

    // Constantes para mensajes de autorización
    object AuthRequired {
        const val GENERATE_ROUTES = "generar rutas personalizadas con IA"
        const val SAVE_FAVORITES = "guardar favoritos"
        const val MY_FAVORITES = "ver tus favoritos"
        const val SAVE_ROUTES = "guardar rutas"
        const val MY_ROUTES = "ver tus rutas guardadas"
        const val CHECK_IN = "hacer check-ins en lugares"
        const val LEAVE_REVIEWS = "dejar reseñas"
        const val UPLOAD_PHOTOS = "subir fotos"
        const val PROXIMITY_NOTIFICATIONS = "activar notificaciones de proximidad"
        const val VIEW_STATS = "ver estadísticas personales"
    }
}
```

---

## 📱 **Patrones de Implementación**

### **Patrón 1: Activity Completa Requiere Auth**

Para activities donde TODA la funcionalidad requiere login:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Verificar autenticación antes de inicializar
    if (!AuthManager.requireAuth(this, AuthManager.AuthRequired.MY_FAVORITES) {
            initializeActivity()
        }) {
        finish()
        return
    }
}

private fun initializeActivity() {
    binding = ActivityFavoritesBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // ... resto de inicialización
}
```

**Archivos usando este patrón:**
- `FavoritesActivity.kt` (líneas 21-35)
- `MyRoutesActivity.kt` (líneas 22-42)
- `StatsActivity.kt` (líneas 24-44)
- `ProximityNotificationsActivity.kt` (líneas 62-82)
- `AdminPhotoUploadActivity.kt` (líneas 45-78)

---

### **Patrón 2: Acción Específica Requiere Auth**

Para actions/botones específicos que requieren login:

```kotlin
binding.generateRouteButton.setOnClickListener {
    AuthManager.requireAuth(this, AuthManager.AuthRequired.GENERATE_ROUTES) {
        // Código que solo se ejecuta si está autenticado
        generateRouteWithAuth()
    }
}

private fun generateRouteWithAuth() {
    // Lógica de generación de ruta
}
```

**Archivos usando este patrón:**
- `PlaceDetailsActivity.kt`:
  - `toggleFavorite()` (líneas 166-191)
  - `performCheckIn()` (líneas 193-218)
  - `submitReview()` (líneas 320-364)
- `PreferencesActivity.kt`:
  - Generar ruta IA (líneas 28-34)
- `MapsActivity.kt`:
  - Guardar ruta (líneas 255-267)

---

## 🚀 **Flujo de Usuario**

### **Caso 1: Turista Nueva - Modo Invitado**

```
1. Abre app por primera vez
   ↓
2. Ve pantalla de login
   ↓
3. Click en "🚶 Continuar como invitado"
   ↓
4. ✅ Accede a MenuActivity
   ↓
5. Explora mapa, blog, eventos (sin restricciones)
   ↓
6. Intenta guardar un favorito
   ↓
7. ⚠️ Diálogo: "Para guardar favoritos necesitas crear una cuenta"
   ↓
8. Decide: [Iniciar sesión] o [Ahora no]
```

---

### **Caso 2: Turista Escanea QR (Sin App Instalada)**

```
1. Ve letrero con QR en plaza
   ↓
2. Abre cámara del teléfono
   ↓
3. Escanea QR: TrazaGo://place/plaza_armas
   ↓
4. Teléfono pregunta: "¿Abrir con TrazaGo?"
   ↓
5. No tiene la app → Dirige a Play Store
   ↓
6. Descarga e instala
   ↓
7. Vuelve a escanear QR
   ↓
8. ✅ App se abre directamente en PlaceDetailsActivity
   ↓
9. Ve toda la info del lugar (sin login)
   ↓
10. Si quiere guardar favorito → Pide login
```

---

### **Caso 3: Turista Quiere Ruta con IA**

```
1. Abre PreferencesActivity
   ↓
2. Llena formulario (presupuesto, tiempo, intereses)
   ↓
3. Click en "Generar Ruta"
   ↓
4. ⚠️ Diálogo: "Para generar rutas personalizadas con IA necesitas crear una cuenta"
   ↓
5. Click en "Iniciar sesión"
   ↓
6. Redirige a LoginActivity
   ↓
7. Se registra o inicia sesión
   ↓
8. ✅ Vuelve a PreferencesActivity
   ↓
9. ✅ Genera ruta con IA (ahora tiene permiso)
```

---

## 🎨 **Experiencia de Usuario**

### **Diálogo de Autenticación:**

```
┌─────────────────────────────────────┐
│         Iniciar sesión              │
├─────────────────────────────────────┤
│ Para guardar favoritos necesitas    │
│ crear una cuenta o iniciar sesión.  │
│                                     │
│ ¿Deseas continuar?                  │
├─────────────────────────────────────┤
│    [Iniciar sesión]  [Ahora no]    │
└─────────────────────────────────────┘
```

**Características:**
- ✅ Mensaje claro y específico (no genérico)
- ✅ Explica POR QUÉ necesita login
- ✅ Opción de cancelar sin molestia
- ✅ No bloquea exploración

---

## 📊 **Estadísticas de Conversión**

Con este sistema puedes trackear:

```kotlin
// En AuthManager.requireAuth()
if (!isAuthenticated()) {
    FirebaseAnalytics.getInstance(context).logEvent("auth_prompt_shown") {
        param("feature", actionName)
        param("user_mode", if(isGuestMode(context)) "guest" else "unknown")
    }
}
```

**Métricas útiles:**
- 📈 ¿Cuántos invitados se convierten en usuarios?
- 📈 ¿Qué función motiva más conversiones?
- 📈 ¿En qué momento deciden registrarse?
- 📈 ¿Cuántos exploran sin registrarse?

---

## 🔐 **Seguridad**

### **Validaciones en Backend (Firestore Security Rules):**

```javascript
// Firestore Security Rules
match /users/{userId}/favorites/{favoriteId} {
  // Solo el dueño puede leer/escribir sus favoritos
  allow read, write: if request.auth != null && request.auth.uid == userId;
}

match /rutas/{routeId} {
  // Solo el creador puede modificar/eliminar su ruta
  allow read: if true; // Rutas son públicas
  allow create: if request.auth != null;
  allow update, delete: if request.auth != null &&
                           resource.data.id_usuario == request.auth.uid;
}

match /checkIns/{checkInId} {
  // Solo usuarios autenticados pueden hacer check-in
  allow create: if request.auth != null &&
                   request.resource.data.userId == request.auth.uid;
  allow read: if request.auth != null &&
                 resource.data.userId == request.auth.uid;
}
```

### **Validaciones en Cliente:**

```kotlin
// Ejemplo: PlaceDetailsActivity - toggleFavorite()
private fun toggleFavorite() {
    AuthManager.requireAuth(this, AuthManager.AuthRequired.SAVE_FAVORITES) {
        val currentPlaceId = placeId ?: return@requireAuth
        // ✅ Solo se ejecuta si está autenticado
        lifecycleScope.launch {
            // Lógica de favoritos
        }
    }
}
```

---

## 🧪 **Testing**

### **Probar Modo Invitado:**

1. **Desinstalar app** (limpiar datos)
2. **Instalar y abrir**
3. **Click en "Continuar como invitado"**
4. **Verificar funciones gratis:**
   - ✅ Ver mapa
   - ✅ Ver detalles de lugares
   - ✅ Ver blog
   - ✅ Ver eventos
   - ✅ Escanear QR (con cámara nativa)
5. **Intentar funciones premium:**
   - ❌ Generar ruta IA → Debe mostrar diálogo
   - ❌ Guardar favorito → Debe mostrar diálogo
   - ❌ Hacer check-in → Debe mostrar diálogo
6. **Registrarse desde diálogo**
7. **Verificar que funciones premium ahora funcionan**

---

## 📝 **Checklist de Implementación**

### **✅ Completado:**

- [x] AuthManager con requireAuth()
- [x] LoginActivity con botón invitado
- [x] PreferencesActivity - Generar rutas
- [x] FavoritesActivity - Ver favoritos
- [x] PlaceDetailsActivity - Favoritos, check-ins, reseñas
- [x] MapsActivity - Guardar rutas
- [x] MyRoutesActivity - Ver rutas guardadas
- [x] StatsActivity - Ver estadísticas
- [x] ProximityNotificationsActivity - Notificaciones
- [x] AdminPhotoUploadActivity - Subir fotos

### **🔄 Opcional (Futuras Mejoras):**

- [ ] Analytics de conversión (auth_prompt_shown, auth_completed)
- [ ] Recordar qué acción motivó el registro (para UX post-login)
- [ ] Onboarding específico para invitados vs registrados
- [ ] Rate limiting para invitados (prevenir abuso)
- [ ] Banner sutil en MenuActivity: "Regístrate para desbloquear funciones premium"

---

## 🎯 **Beneficios de Esta Implementación**

| Aspecto | Beneficio |
|---------|-----------|
| **Conversión** | Mayor % de usuarios que exploran antes de decidir |
| **Fricción** | Menor abandono en primer uso |
| **Costos** | Protege APIs costosas (Gemini) |
| **Seguridad** | Datos personales solo con autenticación |
| **UX** | No intrusivo, el usuario decide cuándo registrarse |
| **Flexibilidad** | Fácil agregar nuevas funciones premium |
| **Mantenibilidad** | Patrón consistente en toda la app |

---

## 🚀 **Próximos Pasos**

1. **Testing exhaustivo** de todos los flujos
2. **Firestore Security Rules** para proteger backend
3. **Analytics** para medir conversión
4. **UI polish** en LoginActivity (hacer más atractivo)
5. **Documentación para Oficina de Turismo** sobre qué promocionar

---

## 📞 **Soporte**

Si necesitas:
- ✅ Agregar nuevas funciones premium
- ✅ Cambiar qué funciones requieren auth
- ✅ Personalizar mensajes de diálogo
- ✅ Implementar analytics de conversión

¡Avísame! 🚀

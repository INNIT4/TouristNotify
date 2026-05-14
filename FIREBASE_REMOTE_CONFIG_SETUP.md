# 🔧 Configuración de Firebase Remote Config para Producción

## 📋 Tabla de Contenidos
1. [¿Por qué Remote Config?](#por-qué-remote-config)
2. [Configuración Inicial](#configuración-inicial)
3. [Configurar API Keys](#configurar-api-keys)
4. [Configurar Parámetros](#configurar-parámetros)
5. [Verificación](#verificación)
6. [Troubleshooting](#troubleshooting)

---

## 🎯 ¿Por qué Remote Config?

**Problema**: `local.properties` no se sube al repositorio (por seguridad), por lo que:
- ❌ No funciona en otros dispositivos
- ❌ No funciona en Google Play
- ❌ Los colaboradores no tienen las API keys

**Solución**: Firebase Remote Config permite:
- ✅ Actualizar API keys sin recompilar la app
- ✅ Configuración centralizada en la nube
- ✅ Cambios en tiempo real
- ✅ Diferentes valores para dev/prod

---

## 🚀 Configuración Inicial

### Paso 1: Acceder a Firebase Console

1. Ve a [Firebase Console](https://console.firebase.google.com/)
2. Selecciona tu proyecto **TrazaGo**
3. En el menú lateral, busca **Remote Config** (bajo "Interactúa")
4. Click en **Comenzar** si es la primera vez

### Paso 2: Crear Parámetros

Click en **"Agregar parámetro"** y crea los siguientes:

---

## 🔑 Configurar API Keys

### 1. gemini_api_key

```yaml
Clave del parámetro: gemini_api_key
Tipo de datos: String
Valor predeterminado: [TU_GEMINI_API_KEY_AQUÍ]
Descripción: API key de Google Gemini para generación de rutas con IA
```

**¿Dónde obtenerla?**
- Ve a [Google AI Studio](https://makersuite.google.com/app/apikey)
- Click en "Create API Key"
- Copia la key generada

### 2. maps_api_key

```yaml
Clave del parámetro: maps_api_key
Tipo de datos: String
Valor predeterminado: [TU_MAPS_API_KEY_AQUÍ]
Descripción: API key de Google Maps para mapas y direcciones
```

**¿Dónde obtenerla?**
- Ve a [Google Cloud Console](https://console.cloud.google.com/)
- APIs & Services → Credentials
- Create Credentials → API Key
- Habilita las APIs: Maps SDK for Android, Directions API, Places API

### 3. weather_api_key (Opcional)

```yaml
Clave del parámetro: weather_api_key
Tipo de datos: String
Valor predeterminado: [TU_OPENWEATHER_API_KEY_AQUÍ]
Descripción: API key de OpenWeatherMap para clima real (opcional, la app funciona sin ella)
```

**¿Dónde obtenerla?**
- Ve a [OpenWeatherMap](https://openweathermap.org/api)
- Sign Up → Free tier
- My API Keys → Copiar key

---

## ⚙️ Configurar Parámetros

### 4. max_daily_routes

```yaml
Clave del parámetro: max_daily_routes
Tipo de datos: Number
Valor predeterminado: 5
Descripción: Límite diario de rutas IA para usuarios estándar
```

### 5. max_daily_routes_premium

```yaml
Clave del parámetro: max_daily_routes_premium
Tipo de datos: Number
Valor predeterminado: 20
Descripción: Límite diario de rutas IA para usuarios premium (futuro)
```

---

## 📸 Ejemplo Visual

Tu configuración debería verse así:

```
Firebase Console > Remote Config > Parámetros

┌─────────────────────────────────────────────────────┐
│ gemini_api_key                              String  │
│ --------------------------------------------------- │
│ Valor: AIzaSy...                                    │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ maps_api_key                                String  │
│ --------------------------------------------------- │
│ Valor: AIzaSy...                                    │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ max_daily_routes                            Number  │
│ --------------------------------------------------- │
│ Valor: 5                                            │
└─────────────────────────────────────────────────────┘
```

---

## 🔐 Seguridad Recomendada

### Restricciones de API Keys

**Para Google Maps API**:
1. Ve a Google Cloud Console → Credentials
2. Click en tu API Key → "Application restrictions"
3. Selecciona "Android apps"
4. Añade:
   - Package name: `com.joseibarra.TrazaGo`
   - SHA-1: (obtén con `keytool -list -v -keystore ~/.android/debug.keystore`)

**Para Gemini API**:
1. Ve a Google AI Studio → API Keys
2. Click en los 3 puntos → "Set API restrictions"
3. Activa "Application restrictions"
4. Añade tu package name

---

## ✅ Verificación

### Paso 1: Publicar Cambios

1. En Firebase Console → Remote Config
2. Click en **"Publicar cambios"** (botón azul superior derecha)
3. Confirma la publicación

### Paso 2: Verificar en la App

La app incluye una función de diagnóstico:

```kotlin
// En cualquier Activity o Fragment
val info = ConfigManager.getDiagnosticInfo()
Log.d("ConfigTest", info)
```

Debería mostrar:
```
🔧 CONFIGURACIÓN
Remote Config: ✅

🔑 API KEYS
Gemini: ✅
Maps: ✅
Weather: ✅ (o ⚠️ Mock)

⚙️ LÍMITES
Rutas diarias: 5
Rutas premium: 20
```

---

## 🐛 Troubleshooting

### ❌ "Remote Config: ❌"

**Problema**: Remote Config no se inicializó correctamente

**Soluciones**:
1. Verifica que `google-services.json` esté en `app/`
2. Verifica conexión a internet
3. Revisa logs: `adb logcat | grep ConfigManager`
4. Espera 1-2 minutos después de publicar cambios

### ❌ "Gemini: ❌"

**Problema**: API key no está configurada o es inválida

**Soluciones**:
1. Verifica que el parámetro se llame exactamente `gemini_api_key` (sin espacios)
2. Verifica que la key no tenga espacios al inicio/final
3. Prueba la key en [AI Studio](https://makersuite.google.com/)
4. Fuerza un refresh: `ConfigManager.forceRefresh()`

### ⚠️ "Usando BuildConfig como fallback"

**No es un error**: La app usa `local.properties` cuando Remote Config no está disponible

**Para desarrollo**: Esto es normal y esperado
**Para producción**: Asegúrate de configurar Remote Config

### 🔄 Cambios no se reflejan

**Problema**: La app usa cache de Remote Config (1 hora por defecto)

**Soluciones**:
1. Desinstala y reinstala la app
2. Reduce `minimumFetchIntervalInSeconds` en `ConfigManager.kt` línea 34:
   ```kotlin
   .setMinimumFetchIntervalInSeconds(60) // 1 minuto para testing
   ```
3. Llama a `ConfigManager.forceRefresh()`

---

## 📱 Flujo en Producción

```
┌─────────────────────┐
│  Usuario abre app   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ ConfigManager       │
│ .initialize()       │
└──────────┬──────────┘
           │
           ├──────────────────────┐
           │                      │
           ▼                      ▼
┌─────────────────────┐  ┌──────────────────┐
│ Firebase Remote     │  │ local.properties │
│ Config (Nube)       │  │ (Fallback)       │
└──────────┬──────────┘  └────────┬─────────┘
           │                      │
           └──────────┬───────────┘
                      │
                      ▼
           ┌─────────────────────┐
           │  App usa API keys   │
           │  desde ConfigManager│
           └─────────────────────┘
```

---

## 🎓 Para Colaboradores

**Si eres un nuevo desarrollador en el proyecto:**

1. Clona el repo
2. Crea `local.properties` en la raíz de `app/`:
   ```properties
   MAPS_API_KEY=pide_la_key_al_admin
   GEMINI_API_KEY=pide_la_key_al_admin
   DIRECTIONS_API_KEY=pide_la_key_al_admin
   ```
3. La app funcionará en desarrollo
4. Para producción, el admin configurará Remote Config

---

## 🚀 Deploy a Google Play

**Checklist antes de subir a Google Play:**

- [ ] Remote Config configurado con todos los parámetros
- [ ] API Keys publicadas en Firebase Console
- [ ] Restricciones de API keys activadas
- [ ] `minimumFetchIntervalInSeconds` = 3600 (1 hora)
- [ ] Probado en dispositivo de producción (APK release)
- [ ] Verificado `ConfigManager.getDiagnosticInfo()` muestra ✅

**Build de producción:**
```bash
./gradlew assembleRelease
```

Las API keys se obtendrán automáticamente de Firebase Remote Config. 🎉

---

## 📞 Contacto

¿Problemas? Revisa los logs:
```bash
adb logcat | grep -E "ConfigManager|RemoteConfig"
```

¿Aún tienes dudas? Contacta al administrador del proyecto.

---

**Última actualización**: 2026-01-27
**Versión de la guía**: 1.0
**Compatible con**: TrazaGo v1.0+

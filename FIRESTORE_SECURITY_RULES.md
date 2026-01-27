# 🔐 Firestore Security Rules - TouristNotify

## 📋 **Resumen**

Este documento explica las reglas de seguridad de Firestore para TouristNotify, incluyendo:
- ✅ Qué protegen las reglas
- ✅ Cómo desplegarlas
- ✅ Cómo probarlas
- ✅ Ejemplos de operaciones permitidas/bloqueadas

---

## 🎯 **Principios de Seguridad**

### **1. Modo Invitado (Guest Mode)**
- ✅ Lectura pública de contenido general (lugares, blog, eventos, fotos)
- ❌ Escritura solo con autenticación

### **2. Datos Personales**
- ✅ Solo el dueño puede acceder a sus propios datos (favoritos, rutas, stats, check-ins)
- ❌ Nadie más puede leer o modificar datos de otros usuarios

### **3. Funciones Administrativas**
- ✅ Solo emails autorizados pueden crear/modificar contenido
- ❌ Usuarios regulares no pueden modificar lugares, blog, eventos

### **4. Validación de Datos**
- ✅ Estructura de datos validada en creación
- ✅ Usuario actual debe coincidir con `userId` en documentos
- ❌ No se puede crear datos con `userId` de otro usuario

---

## 📊 **Matriz de Permisos**

| Colección | Lectura (Invitado) | Lectura (Autenticado) | Escritura (Usuario) | Escritura (Admin) |
|-----------|-------------------|----------------------|--------------------|--------------------|
| **lugares** | ✅ | ✅ | ❌ | ✅ |
| **lugares/{id}/reviews** | ✅ | ✅ | ✅ (crear solo) | ✅ |
| **rutas** | ✅ | ✅ | ✅ (sus propias rutas) | ✅ |
| **users/{userId}/favorites** | ❌ | ✅ (solo sus favoritos) | ✅ (solo sus favoritos) | ❌ |
| **users/{userId}/stats** | ❌ | ✅ (solo sus stats) | ✅ (solo sus stats) | ❌ |
| **checkIns** | ❌ | ✅ (solo sus check-ins) | ✅ (crear solo) | ❌ |
| **blog_posts** | ✅ | ✅ | ❌ | ✅ |
| **eventos** | ✅ | ✅ | ❌ | ✅ |
| **place_photos** | ✅ | ✅ | ❌ | ✅ |
| **themed_routes** | ✅ | ✅ | ❌ | ✅ |
| **services** | ✅ | ✅ | ❌ | ✅ |
| **emergency_contacts** | ✅ | ✅ | ❌ | ✅ |

---

## 🚀 **Despliegue de Reglas**

### **Método 1: Firebase Console (Recomendado para Testing)**

1. **Ir a Firebase Console:**
   ```
   https://console.firebase.google.com/
   ```

2. **Seleccionar proyecto TouristNotify**

3. **Navegar a Firestore Database → Rules**

4. **Copiar contenido de `firestore.rules`**

5. **Pegar en el editor y click en "Publicar"**

---

### **Método 2: Firebase CLI (Recomendado para Producción)**

#### **Instalación:**

```bash
# Instalar Firebase CLI globalmente
npm install -g firebase-tools

# Login a Firebase
firebase login

# Inicializar proyecto (solo primera vez)
firebase init firestore
```

Cuando pregunte:
- **What file should be used for Firestore Rules?** → `firestore.rules`
- **File firestore.rules already exists. Do you want to overwrite it?** → `No`

#### **Despliegue:**

```bash
# Desplegar solo las reglas de Firestore
firebase deploy --only firestore:rules

# Ver el proyecto actual
firebase use

# Cambiar de proyecto (si tienes staging/production)
firebase use production
firebase deploy --only firestore:rules
```

---

### **Método 3: Desde Android Studio**

Si tienes Firebase integrado:

1. **Tools → Firebase → Cloud Firestore**
2. **Click en "Deploy security rules"**
3. **Seleccionar `firestore.rules`**

---

## 🧪 **Testing de Reglas**

### **Opción 1: Rules Playground (Firebase Console)**

1. **Ir a Firestore Database → Rules → Simulador**
2. **Configurar el contexto de autenticación**
3. **Probar operaciones**

#### **Ejemplos de Tests:**

**Test 1: Invitado puede leer lugares**
```
Authenticated: No
Location: /lugares/plaza_armas
Operation: get
Result: ✅ ALLOW
```

**Test 2: Invitado NO puede crear lugares**
```
Authenticated: No
Location: /lugares/nuevo_lugar
Operation: create
Result: ❌ DENY
```

**Test 3: Usuario autenticado puede guardar favorito**
```
Authenticated: Yes
User ID: abc123
Location: /users/abc123/favorites/fav001
Operation: create
Data: {
  "placeId": "plaza_armas",
  "placeName": "Plaza de Armas",
  "category": "Plaza",
  "addedAt": "2024-01-20T10:00:00Z"
}
Result: ✅ ALLOW
```

**Test 4: Usuario NO puede guardar favorito de otro usuario**
```
Authenticated: Yes
User ID: abc123
Location: /users/xyz789/favorites/fav001
Operation: create
Result: ❌ DENY
```

---

### **Opción 2: Firebase Emulator (Testing Local)**

#### **Setup:**

```bash
# Instalar emuladores
firebase init emulators

# Seleccionar: Firestore Emulator

# Iniciar emuladores
firebase emulators:start
```

#### **Usar en App:**

```kotlin
// En tu Application class o MainActivity
if (BuildConfig.DEBUG) {
    val firestore = FirebaseFirestore.getInstance()
    firestore.useEmulator("10.0.2.2", 8080)

    val auth = FirebaseAuth.getInstance()
    auth.useEmulator("10.0.2.2", 9099)
}
```

---

### **Opción 3: Unit Tests con @firebase/rules-unit-testing**

Crea `firestore.test.js`:

```javascript
const { initializeTestEnvironment } = require('@firebase/rules-unit-testing');
const fs = require('fs');

let testEnv;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'tourist-notify-test',
    firestore: {
      rules: fs.readFileSync('firestore.rules', 'utf8'),
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

describe('TouristNotify Security Rules', () => {

  test('invitado puede leer lugares', async () => {
    const unauthedDb = testEnv.unauthenticatedContext().firestore();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('lugares').doc('test').set({
        nombre: 'Plaza de Armas'
      });
    });

    const doc = unauthedDb.collection('lugares').doc('test');
    await assertSucceeds(doc.get());
  });

  test('invitado NO puede crear lugares', async () => {
    const unauthedDb = testEnv.unauthenticatedContext().firestore();
    const doc = unauthedDb.collection('lugares').doc('new');
    await assertFails(doc.set({ nombre: 'Nuevo Lugar' }));
  });

  test('usuario puede guardar su propio favorito', async () => {
    const authedDb = testEnv.authenticatedContext('user123').firestore();
    const doc = authedDb.collection('users').doc('user123')
                        .collection('favorites').doc('fav1');

    await assertSucceeds(doc.set({
      placeId: 'plaza_armas',
      placeName: 'Plaza de Armas',
      category: 'Plaza',
      addedAt: new Date()
    }));
  });

  test('usuario NO puede guardar favorito de otro', async () => {
    const authedDb = testEnv.authenticatedContext('user123').firestore();
    const doc = authedDb.collection('users').doc('user456')
                        .collection('favorites').doc('fav1');

    await assertFails(doc.set({
      placeId: 'plaza_armas',
      placeName: 'Plaza de Armas',
      category: 'Plaza',
      addedAt: new Date()
    }));
  });
});
```

Ejecutar:
```bash
npm test
```

---

## 🛡️ **Validaciones Implementadas**

### **1. Favoritos**

✅ **Permitido:**
```kotlin
// Usuario autenticado (uid=abc123) guarda su propio favorito
db.collection("users").document("abc123")
  .collection("favorites").document("fav001")
  .set(mapOf(
    "placeId" to "plaza_armas",
    "placeName" to "Plaza de Armas",
    "category" to "Plaza",
    "addedAt" to FieldValue.serverTimestamp()
  ))
```

❌ **Bloqueado:**
```kotlin
// Usuario abc123 intenta guardar favorito de otro usuario
db.collection("users").document("xyz789")  // ← Diferente UID
  .collection("favorites").document("fav001")
  .set(...)  // ← DENIED
```

---

### **2. Rutas**

✅ **Permitido:**
```kotlin
// Usuario crea ruta con su propio userId
db.collection("rutas").document("route001")
  .set(mapOf(
    "id_ruta" to "route001",
    "id_usuario" to currentUser.uid,  // ← Mismo que auth.uid
    "nombre_ruta" to "Mi ruta",
    "pdis_incluidos" to listOf("lugar1", "lugar2"),
    "fecha_creacion" to FieldValue.serverTimestamp()
  ))
```

❌ **Bloqueado:**
```kotlin
// Usuario intenta crear ruta con userId de otro
db.collection("rutas").document("route001")
  .set(mapOf(
    "id_usuario" to "otro_usuario_uid"  // ← Diferente a auth.uid
    // ... ← DENIED
  ))
```

---

### **3. Reviews**

✅ **Permitido:**
```kotlin
// Usuario autenticado deja review
db.collection("lugares").document("plaza_armas")
  .collection("reviews").document("review001")
  .set(mapOf(
    "userId" to currentUser.uid,
    "userName" to "Juan Pérez",
    "rating" to 5.0,
    "comment" to "Excelente lugar",
    "timestamp" to FieldValue.serverTimestamp()
  ))
```

❌ **Bloqueado:**
```kotlin
// Invitado (no autenticado) intenta dejar review
// ← DENIED porque !isAuthenticated()
```

---

### **4. Check-ins**

✅ **Permitido:**
```kotlin
// Usuario autenticado hace check-in
db.collection("checkIns").document("checkin001")
  .set(mapOf(
    "userId" to currentUser.uid,
    "placeId" to "plaza_armas",
    "placeName" to "Plaza de Armas",
    "timestamp" to FieldValue.serverTimestamp()
  ))
```

❌ **Bloqueado:**
```kotlin
// Usuario intenta modificar check-in (inmutables)
db.collection("checkIns").document("checkin001")
  .update("timestamp", newTime)  // ← DENIED
```

---

### **5. Admin - Lugares**

✅ **Permitido (Admin):**
```kotlin
// Email: turismo@alamos.gob.mx
db.collection("lugares").document("nuevo_lugar")
  .set(mapOf(
    "nombre" to "Nuevo Museo",
    "categoria" to "Museo",
    // ...
  ))
```

❌ **Bloqueado (Usuario Regular):**
```kotlin
// Email: usuario@gmail.com (no admin)
db.collection("lugares").document("nuevo_lugar")
  .set(...)  // ← DENIED
```

---

## 🔧 **Configurar Emails de Admin**

### **Actualizar en `firestore.rules`:**

```javascript
// Líneas 24-28
function isAdmin() {
  return isAuthenticated() &&
         request.auth.token.email in [
           'turismo@alamos.gob.mx',        // ← Email oficial
           'admin@touristnotify.app',      // ← Email del sistema
           'jose.ibarra@example.com'       // ← Agregar nuevos admins aquí
         ];
}
```

### **Desplegar cambios:**

```bash
firebase deploy --only firestore:rules
```

---

## 🚨 **Errores Comunes y Soluciones**

### **Error 1: PERMISSION_DENIED al leer favoritos**

**Causa:** Invitado intenta leer `/users/{userId}/favorites`

**Solución:**
```kotlin
// Verificar autenticación ANTES de intentar leer
if (AuthManager.isAuthenticated()) {
    db.collection("users").document(currentUser.uid)
      .collection("favorites")
      .get()
} else {
    // Mostrar mensaje: "Inicia sesión para ver favoritos"
}
```

---

### **Error 2: PERMISSION_DENIED al crear ruta**

**Causa:** `id_usuario` no coincide con `auth.uid`

**Solución:**
```kotlin
// ❌ MAL
val route = Route(
    id_usuario = "hardcoded_user_id"  // ← ERROR
)

// ✅ BIEN
val route = Route(
    id_usuario = FirebaseAuth.getInstance().currentUser!!.uid
)
```

---

### **Error 3: Admin no puede subir fotos**

**Causa:** Email no está en la lista de admins

**Solución:**
1. Verificar email del usuario:
   ```kotlin
   Log.d("Auth", "Email: ${FirebaseAuth.getInstance().currentUser?.email}")
   ```

2. Agregar email a `firestore.rules`:
   ```javascript
   request.auth.token.email in [
     'turismo@alamos.gob.mx',
     'admin@touristnotify.app',
     'tu_email@example.com'  // ← Agregar aquí
   ]
   ```

3. Desplegar cambios:
   ```bash
   firebase deploy --only firestore:rules
   ```

---

## 📊 **Monitoring y Logs**

### **Ver deniegues en Firebase Console:**

1. **Firestore → Rules → Monitor**
2. Filtra por:
   - ⚠️ **Permission denied**
   - 🔍 **User ID**
   - 📅 **Fecha/Hora**

### **Ejemplo de log:**

```
2024-01-20 10:30:45 - PERMISSION_DENIED
Collection: users/xyz789/favorites
Operation: create
User: abc123
Reason: isOwner(userId) returned false
```

---

## 🎯 **Best Practices**

### ✅ **DO:**

1. **Siempre verificar autenticación en cliente ANTES de escribir:**
   ```kotlin
   if (AuthManager.isAuthenticated()) {
       // Escribir a Firestore
   }
   ```

2. **Usar `FieldValue.serverTimestamp()`:**
   ```kotlin
   "timestamp" to FieldValue.serverTimestamp()
   ```

3. **Validar estructura de datos:**
   ```kotlin
   // Asegúrate de incluir todos los campos requeridos
   val favorite = mapOf(
       "placeId" to placeId,      // ✅ Requerido
       "placeName" to placeName,  // ✅ Requerido
       "category" to category,    // ✅ Requerido
       "addedAt" to FieldValue.serverTimestamp()  // ✅ Requerido
   )
   ```

---

### ❌ **DON'T:**

1. **No confíes solo en validaciones de cliente:**
   ```kotlin
   // ❌ MAL - Fácil de bypassear
   if (user.isAdmin) {
       db.collection("lugares").add(...)
   }
   ```

2. **No uses `allow write: if true;`** en producción

3. **No hardcodees user IDs:**
   ```kotlin
   // ❌ MAL
   val userId = "abc123"

   // ✅ BIEN
   val userId = FirebaseAuth.getInstance().currentUser!!.uid
   ```

---

## 🔄 **Ciclo de Actualización**

```
1. Modificar firestore.rules localmente
   ↓
2. Probar en emulator o Rules Playground
   ↓
3. Si funciona, desplegar a staging:
   firebase use staging
   firebase deploy --only firestore:rules
   ↓
4. Testing en staging con app de prueba
   ↓
5. Si todo OK, desplegar a producción:
   firebase use production
   firebase deploy --only firestore:rules
   ↓
6. Monitorear logs por 24-48 horas
```

---

## 📞 **Troubleshooting**

### **Problema: Reglas no se aplican inmediatamente**

**Solución:**
- Las reglas toman ~1-2 minutos en propagarse
- Fuerza refresh en Firebase Console
- Reinicia app si usas emulator

---

### **Problema: Emulator no respeta reglas**

**Solución:**
```bash
# Verificar que firestore.rules está en la ruta correcta
firebase emulators:start --import=./data --export-on-exit

# Ver logs del emulator
firebase emulators:start --debug
```

---

## 📚 **Recursos Adicionales**

- [Documentación oficial de Firestore Security Rules](https://firebase.google.com/docs/firestore/security/get-started)
- [Rules Playground](https://firebase.google.com/docs/rules/simulator)
- [Testing Rules con Emulator](https://firebase.google.com/docs/firestore/security/test-rules-emulator)
- [Best Practices](https://firebase.google.com/docs/firestore/security/rules-conditions)

---

## ✅ **Checklist de Despliegue**

Antes de desplegar a producción:

- [ ] Reglas testeadas en emulator
- [ ] Reglas testeadas en Rules Playground
- [ ] Emails de admin actualizados
- [ ] Todos los flujos de app testeados
- [ ] Logs monitoreados por 24h en staging
- [ ] Backup de reglas anteriores guardado
- [ ] Equipo notificado del despliegue
- [ ] Plan de rollback preparado

---

## 🎉 **Resultado Final**

Con estas reglas desplegadas:

✅ **Modo invitado funciona** - Explora sin fricción
✅ **Datos personales protegidos** - Solo el dueño accede
✅ **Admin controlado** - Solo emails autorizados
✅ **Validación de datos** - Estructura correcta garantizada
✅ **Auditable** - Logs de todos los deniegues

🚀 **¡Tu app está segura!**

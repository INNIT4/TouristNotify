# 🛡️ Manejo de Errores de Firestore - Ejemplos

## 📋 **Tabla de Contenidos**

1. [Uso Básico](#uso-básico)
2. [Con Callbacks (Tasks)](#con-callbacks-tasks)
3. [Con Coroutines](#con-coroutines)
4. [Casos Específicos](#casos-específicos)
5. [Testing](#testing)

---

## 🎯 **Uso Básico**

### **Ejemplo 1: Manejo Simple de Error**

```kotlin
// En cualquier Activity
lifecycleScope.launch {
    try {
        val doc = db.collection("lugares").document(placeId).get().await()
        // Procesar documento
    } catch (e: Exception) {
        // Usar extension function
        e.handleFirestoreError(
            context = this@PlaceDetailsActivity,
            view = binding.root,
            operation = "cargar detalles del lugar"
        )
    }
}
```

**Resultado si hay error de permisos:**
```
Snackbar: ⚠️ No tienes permiso para cargar detalles del lugar.
         Por favor, inicia sesión o verifica tus permisos.
```

---

### **Ejemplo 2: Con Callback de Autenticación**

```kotlin
lifecycleScope.launch {
    try {
        val result = db.collection("users")
            .document(userId)
            .collection("favorites")
            .get()
            .await()

        // Procesar favoritos
    } catch (e: Exception) {
        e.handleFirestoreError(
            context = this@FavoritesActivity,
            view = binding.root,
            operation = "cargar tus favoritos",
            onAuthRequired = {
                // Redirigir a login cuando el usuario toque "Iniciar sesión"
                val intent = Intent(this@FavoritesActivity, LoginActivity::class.java)
                intent.putExtra("RETURN_AFTER_LOGIN", true)
                startActivity(intent)
            }
        )
    }
}
```

**Resultado si el usuario no está autenticado:**
```
Snackbar: 🔒 Debes iniciar sesión para cargar tus favoritos
          [Iniciar sesión] ← Botón clicable
```

---

## 📞 **Con Callbacks (Tasks)**

### **Ejemplo 3: Extension Function para Tasks**

```kotlin
// Cargar lugares (lectura pública)
db.collection("lugares")
    .get()
    .addOnSuccessListener { documents ->
        val places = documents.toObjects(TouristSpot::class.java)
        updateUI(places)
    }
    .onFirestoreError(
        view = binding.root,
        operation = "cargar lugares"
    )
```

**Sin necesidad de `.addOnFailureListener`** - ¡más limpio!

---

### **Ejemplo 4: Con Callback de Autenticación (Tasks)**

```kotlin
// Guardar favorito
db.collection("users")
    .document(currentUser.uid)
    .collection("favorites")
    .document(favoriteId)
    .set(favoriteData)
    .addOnSuccessListener {
        NotificationHelper.success(binding.root, "Agregado a favoritos")
    }
    .onFirestoreError(
        view = binding.root,
        operation = "guardar favorito",
        onAuthRequired = {
            // Redirigir a login
            startActivity(Intent(this, LoginActivity::class.java))
        }
    )
```

---

### **Ejemplo 5: Con Manejo Adicional**

```kotlin
db.collection("rutas")
    .document(routeId)
    .delete()
    .addOnSuccessListener {
        NotificationHelper.success(binding.root, "Ruta eliminada")
        finish()
    }
    .onFirestoreError(
        view = binding.root,
        operation = "eliminar ruta"
    ) { exception ->
        // Manejo adicional después del error
        Log.e("Routes", "Failed to delete route", exception)
        // Rollback local si es necesario
        adapter.notifyDataSetChanged()
    }
```

---

## ⚡ **Con Coroutines**

### **Ejemplo 6: Try-Catch Básico**

```kotlin
// En FavoritesActivity
private fun loadFavorites() {
    binding.progressBar.visibility = View.VISIBLE

    lifecycleScope.launch {
        try {
            val result = FavoritesManager.getFavorites()

            result.onSuccess { favorites ->
                updateUI(favorites)
            }.onFailure { e ->
                e.handleFirestoreError(
                    context = this@FavoritesActivity,
                    view = binding.root,
                    operation = "cargar tus favoritos"
                )
            }
        } finally {
            binding.progressBar.visibility = View.GONE
        }
    }
}
```

---

### **Ejemplo 7: Múltiples Operaciones**

```kotlin
// En PlaceDetailsActivity
private fun loadPlaceData(placeId: String) {
    lifecycleScope.launch {
        try {
            // Cargar lugar
            val placeDoc = db.collection("lugares")
                .document(placeId)
                .get()
                .await()

            val place = placeDoc.toObject(TouristSpot::class.java)
                ?: throw Exception("Lugar no encontrado")

            updatePlaceDetails(place)

            // Cargar reviews
            val reviewsSnapshot = db.collection("lugares")
                .document(placeId)
                .collection("reviews")
                .get()
                .await()

            val reviews = reviewsSnapshot.toObjects(Review::class.java)
            updateReviews(reviews)

        } catch (e: Exception) {
            e.handleFirestoreError(
                context = this@PlaceDetailsActivity,
                view = binding.root,
                operation = "cargar información del lugar"
            )
        }
    }
}
```

---

### **Ejemplo 8: Con Transacción**

```kotlin
// Incrementar contador de check-ins
lifecycleScope.launch {
    try {
        db.runTransaction { transaction ->
            val placeRef = db.collection("lugares").document(placeId)
            val placeDoc = transaction.get(placeRef)

            val currentCount = placeDoc.getLong("checkInCount") ?: 0L
            transaction.update(placeRef, "checkInCount", currentCount + 1)

            // Registrar check-in del usuario
            val checkInRef = db.collection("checkIns").document()
            transaction.set(checkInRef, mapOf(
                "userId" to currentUser.uid,
                "placeId" to placeId,
                "timestamp" to FieldValue.serverTimestamp()
            ))
        }.await()

        NotificationHelper.success(binding.root, "✅ Check-in exitoso!")

    } catch (e: Exception) {
        e.handleFirestoreError(
            context = this@PlaceDetailsActivity,
            view = binding.root,
            operation = "hacer check-in",
            onAuthRequired = {
                AuthManager.requireAuth(
                    this@PlaceDetailsActivity,
                    AuthManager.AuthRequired.CHECK_IN
                ) {
                    // Reintentar después de login
                    performCheckIn()
                }
            }
        )
    }
}
```

---

## 🎯 **Casos Específicos**

### **Caso 1: Usuario NO Autenticado (Invitado)**

```kotlin
// Usuario invitado intenta guardar favorito
db.collection("users")
    .document("guest_user")  // ← No tiene UID válido
    .collection("favorites")
    .add(favoriteData)
    .onFirestoreError(
        view = binding.root,
        operation = "guardar favorito",
        onAuthRequired = {
            // Mostrar diálogo de autenticación
            AuthManager.requireAuth(
                this,
                AuthManager.AuthRequired.SAVE_FAVORITES
            ) {
                // Reintentar operación
                saveFavorite()
            }
        }
    )
```

**Resultado:**
```
FirestoreException: PERMISSION_DENIED
   ↓
Snackbar: ⚠️ No tienes permiso para guardar favorito.
          Por favor, inicia sesión o verifica tus permisos.
          [Iniciar sesión] ← Click aquí
   ↓
AlertDialog: "Para guardar favoritos necesitas crear una cuenta..."
```

---

### **Caso 2: Usuario Intenta Modificar Datos de Otro**

```kotlin
// Usuario abc123 intenta modificar ruta de xyz789
val otherUserId = "xyz789"
val currentUserId = "abc123"

db.collection("rutas")
    .document(routeId)
    .update("nombre_ruta", "Nueva ruta")
    .onFirestoreError(
        view = binding.root,
        operation = "modificar ruta"
    )
```

**Resultado:**
```
FirestoreException: PERMISSION_DENIED
(Porque resource.data.id_usuario != request.auth.uid)
   ↓
Snackbar: ⚠️ No tienes permiso para modificar ruta.
          Por favor, inicia sesión o verifica tus permisos.
```

---

### **Caso 3: Usuario Regular Intenta Crear Lugar**

```kotlin
// Usuario regular (no admin) intenta crear lugar
db.collection("lugares")
    .add(mapOf(
        "nombre" to "Nuevo Museo",
        "categoria" to "Museo"
    ))
    .onFirestoreError(
        view = binding.root,
        operation = "crear lugar"
    )
```

**Resultado:**
```
FirestoreException: PERMISSION_DENIED
(Porque !isAdmin())
   ↓
Snackbar: ⚠️ No tienes permiso para crear lugar.
          Por favor, inicia sesión o verifica tus permisos.
```

---

### **Caso 4: Conexión Perdida**

```kotlin
// Sin conexión a internet
db.collection("lugares")
    .get()
    .onFirestoreError(
        view = binding.root,
        operation = "cargar lugares"
    )
```

**Resultado:**
```
FirestoreException: UNAVAILABLE
   ↓
Snackbar: 📡 Servicio temporalmente no disponible. Intenta más tarde.
```

---

### **Caso 5: Timeout**

```kotlin
// Operación muy lenta
db.collection("lugares")
    .get()
    .onFirestoreError(
        view = binding.root,
        operation = "cargar lugares"
    )
```

**Resultado:**
```
FirestoreException: DEADLINE_EXCEEDED
   ↓
Snackbar: ⏱️ La operación tardó demasiado. Verifica tu conexión.
```

---

## 🔍 **Debugging con Logs Detallados**

### **Ejemplo 9: Log Completo de Error**

```kotlin
lifecycleScope.launch {
    try {
        val result = db.collection("rutas")
            .document(routeId)
            .get()
            .await()

        // Procesar ruta

    } catch (e: Exception) {
        // Log detallado para debugging
        FirestoreErrorHandler.logDetailedError(
            exception = e,
            operation = "cargar ruta",
            context = mapOf(
                "routeId" to routeId,
                "userId" to currentUser?.uid,
                "timestamp" to System.currentTimeMillis(),
                "source" to "MyRoutesActivity"
            )
        )

        // Mostrar error al usuario
        e.handleFirestoreError(
            context = this@MyRoutesActivity,
            view = binding.root,
            operation = "cargar ruta"
        )
    }
}
```

**Logcat Output:**
```
E/FirestoreError: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
E/FirestoreError: Firestore Error Details
E/FirestoreError: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
E/FirestoreError: Operation: cargar ruta
E/FirestoreError: Exception: FirebaseFirestoreException
E/FirestoreError: Message: PERMISSION_DENIED: Missing or insufficient permissions.
E/FirestoreError: Code: PERMISSION_DENIED
E/FirestoreError: Context: routeId: route_001
E/FirestoreError:          userId: abc123
E/FirestoreError:          timestamp: 1705749012345
E/FirestoreError:          source: MyRoutesActivity
E/FirestoreError: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 🧪 **Testing**

### **Test 1: Verificar Mensaje de Error Correcto**

```kotlin
@Test
fun testPermissionDeniedMessage() {
    val exception = FirebaseFirestoreException(
        "PERMISSION_DENIED",
        FirebaseFirestoreException.Code.PERMISSION_DENIED
    )

    val message = FirestoreErrorHandler.getErrorMessage(
        exception,
        "guardar favorito"
    )

    assertTrue(message.contains("permiso"))
    assertTrue(message.contains("guardar favorito"))
}
```

---

### **Test 2: Verificar Detección de Auth Requerido**

```kotlin
@Test
fun testRequiresAuthentication() {
    val permissionDenied = FirebaseFirestoreException(
        "PERMISSION_DENIED",
        FirebaseFirestoreException.Code.PERMISSION_DENIED
    )

    val unauthenticated = FirebaseFirestoreException(
        "UNAUTHENTICATED",
        FirebaseFirestoreException.Code.UNAUTHENTICATED
    )

    assertTrue(FirestoreErrorHandler.requiresAuthentication(permissionDenied))
    assertTrue(FirestoreErrorHandler.requiresAuthentication(unauthenticated))
}
```

---

### **Test 3: Mock de Firestore con Error**

```kotlin
@Test
fun testHandleFirestoreError() = runBlocking {
    // Simular error de Firestore
    val mockTask = Tasks.forException<DocumentSnapshot>(
        FirebaseFirestoreException(
            "PERMISSION_DENIED",
            FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
    )

    // Verificar que se muestra el mensaje correcto
    var errorShown = false

    mockTask
        .addOnFailureListener { exception ->
            val message = FirestoreErrorHandler.getErrorMessage(
                exception,
                "operación de prueba"
            )
            assertTrue(message.contains("permiso"))
            errorShown = true
        }
        .await()

    assertTrue(errorShown)
}
```

---

## 📊 **Resumen de Códigos de Error**

| Código | Significado | Cuándo Ocurre | Mensaje al Usuario |
|--------|-------------|---------------|-------------------|
| `PERMISSION_DENIED` | Sin permisos | Security Rules bloquean operación | "No tienes permiso..." |
| `UNAUTHENTICATED` | No autenticado | Usuario no ha iniciado sesión | "Debes iniciar sesión..." |
| `NOT_FOUND` | No encontrado | Documento no existe | "No se encontró el elemento" |
| `ALREADY_EXISTS` | Ya existe | Documento duplicado | "Este elemento ya existe" |
| `INVALID_ARGUMENT` | Datos inválidos | Estructura incorrecta | "Datos inválidos" |
| `UNAVAILABLE` | Servicio no disponible | Sin conexión / Firebase caído | "Servicio no disponible" |
| `DEADLINE_EXCEEDED` | Timeout | Operación muy lenta | "Tardó demasiado" |
| `RESOURCE_EXHAUSTED` | Cuota excedida | Límite de operaciones | "Límite excedido" |
| `CANCELLED` | Cancelado | Usuario canceló | "Operación cancelada" |
| `ABORTED` | Transacción fallida | Conflicto de concurrencia | "Operación interrumpida" |

---

## ✅ **Best Practices**

### ✅ **DO:**

1. **Siempre usar `onFirestoreError` o `handleFirestoreError`:**
   ```kotlin
   // ✅ BIEN
   db.collection("lugares").get()
     .onFirestoreError(binding.root, "cargar lugares")

   // ❌ MAL
   db.collection("lugares").get()
     .addOnFailureListener { e ->
       Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
     }
   ```

2. **Proporcionar `onAuthRequired` cuando sea apropiado:**
   ```kotlin
   // ✅ BIEN
   e.handleFirestoreError(
       view = binding.root,
       operation = "guardar favorito",
       onAuthRequired = { redirectToLogin() }
   )
   ```

3. **Usar operaciones descriptivas:**
   ```kotlin
   // ✅ BIEN - Específico
   operation = "cargar tus favoritos"

   // ❌ MAL - Genérico
   operation = "cargar datos"
   ```

---

### ❌ **DON'T:**

1. **No ignores errores:**
   ```kotlin
   // ❌ MAL
   try {
       db.collection("lugares").get().await()
   } catch (e: Exception) {
       // Silencioso - mal UX
   }
   ```

2. **No uses Toast para errores críticos:**
   ```kotlin
   // ❌ MAL
   .addOnFailureListener { e ->
       Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
   }

   // ✅ BIEN
   .onFirestoreError(binding.root, "operación")
   ```

3. **No hardcodees mensajes:**
   ```kotlin
   // ❌ MAL
   NotificationHelper.error(binding.root, "Error al cargar")

   // ✅ BIEN
   e.handleFirestoreError(...)
   ```

---

## 🎉 **Resultado Final**

Con `FirestoreErrorHandler`:

✅ **Mensajes consistentes** en toda la app
✅ **UX mejorada** con acciones contextuales
✅ **Debugging facilitado** con logs detallados
✅ **Código más limpio** con extension functions
✅ **Testing simplificado** con funciones auxiliares

🚀 **¡Errores bien manejados = Usuarios felices!**

# Asignar el rol de administrador (Custom Claims)

TrazaGo usa **Firebase Auth Custom Claims** para identificar admins. Anteriormente
las reglas Firestore validaban contra una lista de emails — un diseño explotable:
cualquier persona que registrara `admin@TrazaGo.app` antes que el equipo
legítimo obtenía control total sobre el contenido del blog, eventos y geofences.

Con Custom Claims, el rol vive en el ID token firmado por Firebase y no puede
falsificarse desde el cliente.

---

## Asignar el claim a un usuario

Necesitas:
- El **UID** del usuario (visible en Firebase Console → Authentication → Users).
- Una de las opciones de abajo: Firebase CLI, Cloud Function, o script Node con
  Firebase Admin SDK.

### Opción A — `firebase` CLI (rápido, una vez)

```bash
# Instalar firebase-tools si no lo tienes
npm install -g firebase-tools

# Autenticarte (abre el navegador)
firebase login

# Ejecutar este script Node con tu service-account.json
node ./scripts/set-admin-claim.js <uid>
```

`scripts/set-admin-claim.js`:
```javascript
const admin = require('firebase-admin');
const serviceAccount = require('./service-account.json'); // bajado de
                                                          // Firebase Console
                                                          // → Project Settings
                                                          // → Service accounts

admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });

const uid = process.argv[2];
if (!uid) {
  console.error('Usage: node set-admin-claim.js <uid>');
  process.exit(1);
}

admin.auth().setCustomUserClaims(uid, { admin: true })
  .then(() => {
    console.log(`✓ Admin claim asignado a ${uid}`);
    console.log('  El usuario debe cerrar sesión y volver a iniciar para que el');
    console.log('  cliente reciba el nuevo token con el claim.');
    process.exit(0);
  })
  .catch(err => {
    console.error('✗ Error:', err.message);
    process.exit(1);
  });
```

### Opción B — Cloud Function callable (recomendado a largo plazo)

```typescript
// functions/src/admin.ts
import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

admin.initializeApp();

export const grantAdmin = functions.https.onCall(async (data, context) => {
  // Solo admins existentes pueden crear más admins
  if (context.auth?.token.admin !== true) {
    throw new functions.https.HttpsError(
      'permission-denied',
      'Solo administradores pueden asignar roles.'
    );
  }
  const targetUid = data.uid;
  if (typeof targetUid !== 'string') {
    throw new functions.https.HttpsError('invalid-argument', 'uid requerido');
  }
  await admin.auth().setCustomUserClaims(targetUid, { admin: true });
  return { ok: true };
});
```

El **primer admin** debes crearlo manualmente con la Opción A, después puedes
usar la Cloud Function desde la app o un panel admin.

---

## Verificar el claim desde la app

```kotlin
lifecycleScope.launch {
    val isAdmin = AdminClaims.isAdmin(forceRefresh = true)
    if (isAdmin) {
        // Mostrar UI de admin
    }
}
```

`forceRefresh = true` fuerza la descarga del token actualizado, necesario
inmediatamente después de que el servidor asigna el claim. En navegación
normal usar `forceRefresh = false` (el token se cachea ~1 hora).

---

## Revocar el claim

```javascript
admin.auth().setCustomUserClaims(uid, null);  // o { admin: false }
admin.auth().revokeRefreshTokens(uid);        // fuerza re-login en todos los dispositivos
```

---

## Migración desde el modelo anterior

Para cada email en la antigua lista (`turismo@alamos.gob.mx`, etc.):
1. Verificar que el usuario existe en Firebase Console → Authentication.
2. Copiar su UID.
3. Ejecutar `node set-admin-claim.js <uid>`.
4. Pedirle que cierre sesión y vuelva a iniciar.

Las reglas Firestore actualizadas rechazarán a cualquier usuario sin el claim
incluso si su email coincide con la lista antigua de `AdminConfig.kt`.
`AdminConfig` ahora solo controla la visibilidad de UI de admin (UX), no la
seguridad real.

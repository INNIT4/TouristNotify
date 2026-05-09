# Cloud Functions para `rating`, `reviewCount` y `visitCount`

Las reglas de Firestore actuales bloquean ataques groseros (`rating > 5`,
`reviewCount` decreciente, `visitCount` incremento != +1) pero no impiden
que un usuario autenticado:

- **PEN-006**: Llame `incrementVisitCount` 1000 veces sin haber estado en el
  lugar, inflando el Top 10.
- **PEN-007**: Setee directamente `rating = 5.0` sin haber dejado una review
  legítima, ya que la regla solo valida que el valor esté en [0, 5].

La defensa completa requiere mover el cálculo a **Cloud Functions triggers**.
Los bloques de abajo son código listo para `firebase deploy`.

---

## 1. `aggregateRating` — recalcula rating al crear/editar/borrar review

```typescript
// functions/src/aggregateRating.ts
import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

async function recompute(placeId: string) {
  const reviews = await db.collection('lugares').doc(placeId)
    .collection('reviews').get();
  const count = reviews.size;
  const sum = reviews.docs.reduce((acc, doc) => acc + (doc.data().rating || 0), 0);
  const avg = count === 0 ? 0 : sum / count;
  await db.collection('lugares').doc(placeId).update({
    rating: Number(avg.toFixed(2)),
    reviewCount: count,
  });
}

export const onReviewCreated = functions.firestore
  .document('lugares/{placeId}/reviews/{reviewId}')
  .onCreate((_, ctx) => recompute(ctx.params.placeId));

export const onReviewUpdated = functions.firestore
  .document('lugares/{placeId}/reviews/{reviewId}')
  .onUpdate((_, ctx) => recompute(ctx.params.placeId));

export const onReviewDeleted = functions.firestore
  .document('lugares/{placeId}/reviews/{reviewId}')
  .onDelete((_, ctx) => recompute(ctx.params.placeId));
```

Una vez desplegado, **endurecer las reglas** quitando `rating` y `reviewCount`
del listado de campos updateables por cliente:

```
allow update: if isAuthenticated() && (
  isAdmin()
  ||
  (
    request.resource.data.diff(resource.data).affectedKeys()
      .hasOnly(['visitCount']) &&
    request.resource.data.visitCount is number &&
    request.resource.data.visitCount == resource.data.get('visitCount', 0) + 1
  )
);
```

Y en `PlaceDetailsActivity.submitNewReview` / `updateExistingReview`,
**eliminar** las líneas:
```kotlin
transaction.update(placeRef, "rating", newRating)
transaction.update(placeRef, "reviewCount", newReviewCount)
```
La Cloud Function se encarga.

---

## 2. `recordVisit` — visit count con anti-spam (PEN-006)

```typescript
// functions/src/recordVisit.ts
import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

if (admin.apps.length === 0) admin.initializeApp();
const db = admin.firestore();

const VISIT_COOLDOWN_MS = 4 * 60 * 60 * 1000;   // 4h entre visitas registradas
const MAX_DISTANCE_M = 200;                     // dentro de 200m del lugar

function haversine(a: {lat: number, lon: number}, b: {lat: number, lon: number}) {
  const R = 6_371_000;
  const dLat = (b.lat - a.lat) * Math.PI / 180;
  const dLon = (b.lon - a.lon) * Math.PI / 180;
  const lat1 = a.lat * Math.PI / 180;
  const lat2 = b.lat * Math.PI / 180;
  const x = Math.sin(dLat / 2) ** 2 +
            Math.sin(dLon / 2) ** 2 * Math.cos(lat1) * Math.cos(lat2);
  return 2 * R * Math.asin(Math.sqrt(x));
}

export const recordVisit = functions
  .runWith({ enforceAppCheck: true })
  .https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'Requiere login');
    }
    const { placeId, lat, lon } = data;
    if (typeof placeId !== 'string' || typeof lat !== 'number' || typeof lon !== 'number') {
      throw new functions.https.HttpsError('invalid-argument', 'datos inválidos');
    }

    const placeRef = db.collection('lugares').doc(placeId);
    const placeSnap = await placeRef.get();
    if (!placeSnap.exists) {
      throw new functions.https.HttpsError('not-found', 'Lugar no existe');
    }
    const place = placeSnap.data()!;
    const placeGeo = place.ubicacion;
    if (!placeGeo) {
      throw new functions.https.HttpsError('failed-precondition', 'Lugar sin ubicación');
    }

    const distance = haversine(
      { lat, lon },
      { lat: placeGeo.latitude, lon: placeGeo.longitude }
    );
    if (distance > MAX_DISTANCE_M) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        `Estás a ${Math.round(distance)}m del lugar; necesitas estar dentro de ${MAX_DISTANCE_M}m`
      );
    }

    // Cooldown por usuario+lugar
    const visitRef = db.collection('users').doc(context.auth.uid)
      .collection('visit_log').doc(placeId);
    const last = await visitRef.get();
    if (last.exists) {
      const elapsed = Date.now() - (last.data()!.timestamp ?? 0);
      if (elapsed < VISIT_COOLDOWN_MS) {
        return { ok: false, reason: 'cooldown', remainingMs: VISIT_COOLDOWN_MS - elapsed };
      }
    }

    await Promise.all([
      placeRef.update({ visitCount: admin.firestore.FieldValue.increment(1) }),
      visitRef.set({ timestamp: Date.now() }),
    ]);
    return { ok: true };
  });
```

Una vez desplegado, en el cliente:
```kotlin
val data = mapOf("placeId" to id, "lat" to currentLat, "lon" to currentLon)
Firebase.functions.getHttpsCallable("recordVisit").call(data).await()
```
Y endurecer las reglas para **prohibir** completamente que el cliente toque
`visitCount` directamente.

---

## 3. Despliegue

```bash
cd functions
npm install firebase-functions firebase-admin
firebase deploy --only functions:onReviewCreated,functions:onReviewUpdated,functions:onReviewDeleted,functions:recordVisit
```

Tras el deploy, actualizar `firestore.rules` con las versiones endurecidas
indicadas arriba en cada sección.

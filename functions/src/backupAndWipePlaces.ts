import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";

if (admin.apps.length === 0) {
  admin.initializeApp();
}

const db = admin.firestore();
const PLACES_COLLECTION = "lugares";
const BATCH_SIZE = 499;

/**
 * Callable Cloud Function: crea un backup de `lugares` y opcionalmente borra el original.
 *
 * Auth: requiere usuario con custom claim `admin: true`.
 * Parámetros:
 *   - wipeAfterBackup: boolean (default false) — si true, borra `lugares` tras backup exitoso.
 *
 * Retorna:
 *   - backupCollection: string (ej: "lugares_backup_1714000000000")
 *   - backedUpCount: number
 *   - wipedCount: number (0 si wipeAfterBackup=false)
 */
export const backupAndWipePlaces = onCall(
  { enforceAppCheck: false },
  async (request) => {
    // ── Auth check ──────────────────────────────────────────────────────────
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Se requiere autenticación.");
    }
    const claims = request.auth.token;
    if (claims["admin"] !== true) {
      throw new HttpsError("permission-denied", "Solo administradores pueden ejecutar esta operación.");
    }

    const wipeAfterBackup: boolean = request.data?.wipeAfterBackup === true;
    const timestamp = Date.now();
    const backupCollection = `lugares_backup_${timestamp}`;

    // ── Backup ───────────────────────────────────────────────────────────────
    const snapshot = await db.collection(PLACES_COLLECTION).get();
    const docs = snapshot.docs;

    let backedUpCount = 0;
    const backupBatches = chunkArray(docs, BATCH_SIZE);
    for (const chunk of backupBatches) {
      const batch = db.batch();
      for (const doc of chunk) {
        const ref = db.collection(backupCollection).doc(doc.id);
        batch.set(ref, doc.data());
      }
      await batch.commit();
      backedUpCount += chunk.length;
    }

    // ── Wipe (opcional) ──────────────────────────────────────────────────────
    let wipedCount = 0;
    if (wipeAfterBackup) {
      const wipeBatches = chunkArray(docs, BATCH_SIZE);
      for (const chunk of wipeBatches) {
        const batch = db.batch();
        for (const doc of chunk) {
          batch.delete(doc.ref);
        }
        await batch.commit();
        wipedCount += chunk.length;
      }
    }

    return { backupCollection, backedUpCount, wipedCount };
  }
);

function chunkArray<T>(arr: T[], size: number): T[][] {
  const chunks: T[][] = [];
  for (let i = 0; i < arr.length; i += size) {
    chunks.push(arr.slice(i, i + size));
  }
  return chunks;
}

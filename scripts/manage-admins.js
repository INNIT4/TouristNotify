/**
 * Gestiona los usuarios admin via custom claims.
 *
 * Requisitos:
 *   1. Tener Node.js instalado (https://nodejs.org)
 *   2. Haber iniciado sesión con Firebase CLI: npx firebase login
 *   3. Tener una key de servicio:
 *      - Ve a Firebase Console > Project settings > Service accounts
 *      - Generate new private key
 *      - Guarda el JSON como scripts/service-account.json
 *   4. npm install firebase-admin (una sola vez)
 *
 * Uso:
 *   node scripts/manage-admins.js list
 *   node scripts/manage-admins.js set jose@gmail.com true
 *   node scripts/manage-admins.js set otro@email.com false
 */

const admin = require('firebase-admin');

const serviceAccount = require('./service-account.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: 'touristnotify-db',
});

async function listAdmins() {
  let nextPageToken;
  const admins = [];
  do {
    const result = await admin.auth().listUsers(1000, nextPageToken);
    for (const user of result.users) {
      const isAdmin = user.customClaims?.admin === true;
      if (isAdmin) admins.push(user);
    }
    nextPageToken = result.pageToken;
  } while (nextPageToken);

  if (admins.length === 0) {
    console.log('No hay admins registrados.');
  } else {
    console.log(`\nAdmins (${admins.length}):`);
    for (const u of admins) {
      console.log(`  - ${u.email || u.uid} (${u.uid})`);
    }
  }
}

async function setAdmin(email, isAdmin) {
  try {
    const user = await admin.auth().getUserByEmail(email);
    await admin.auth().setCustomUserClaims(user.uid, { admin: isAdmin });
    console.log(`${isAdmin ? '✅ Admin' : '❌ Quitado admin'}: ${email}`);
  } catch (e) {
    if (e.code === 'auth/user-not-found') {
      console.log(`Usuario no encontrado: ${email}`);
    } else {
      console.error(e.message);
    }
  }
}

const [cmd, ...args] = process.argv.slice(2);

if (cmd === 'list') {
  listAdmins().then(() => process.exit(0));
} else if (cmd === 'set' && args.length >= 2) {
  setAdmin(args[0], args[1] === 'true').then(() => process.exit(0));
} else if (cmd === 'clear-all-except' && args.length >= 1) {
  (async () => {
    const keep = args[0].toLowerCase();
    let nextPageToken;
    do {
      const result = await admin.auth().listUsers(1000, nextPageToken);
      for (const user of result.users) {
        if (user.customClaims?.admin === true && user.email?.toLowerCase() !== keep) {
          await admin.auth().setCustomUserClaims(user.uid, { admin: false });
          console.log(`❌ Quitado admin: ${user.email || user.uid}`);
        }
      }
      nextPageToken = result.pageToken;
    } while (nextPageToken);
    await setAdmin(keep, true);
    console.log('Hecho.');
    process.exit(0);
  })();
} else {
  console.log('Uso:');
  console.log('  node scripts/manage-admins.js list');
  console.log('  node scripts/manage-admins.js set <email> <true|false>');
  console.log('  node scripts/manage-admins.js clear-all-except <email>');
}

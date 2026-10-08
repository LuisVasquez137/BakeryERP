/**
 * Cambia el rol de un usuario (por correo) dentro de su empresa. Imprime antes y después.
 * Se usa para lo que la app todavía no permite: nombrar ADMIN u OWNER.
 *
 * Uso: node set-user-role.js --email=<correo> --role=OWNER|ADMIN|MANAGER|EMPLOYEE
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const arg = (k) => (process.argv.find((a) => a.startsWith(`--${k}=`)) || '').split('=')[1];
const EMAIL = (arg('email') || '').toLowerCase();
const ROLE = arg('role');

(async () => {
  if (!EMAIL || !['OWNER', 'ADMIN', 'MANAGER', 'EMPLOYEE'].includes(ROLE)) {
    console.error('Uso: node set-user-role.js --email=<correo> --role=OWNER|ADMIN|MANAGER|EMPLOYEE');
    process.exit(1);
  }
  const snap = await db.collection('users').get();
  const matches = snap.docs.filter((d) => String(d.data().email || '').toLowerCase() === EMAIL);
  if (matches.length !== 1) throw new Error(`Se esperaba 1 usuario con ${EMAIL}, hay ${matches.length}`);
  const ref = matches[0].ref;
  const before = matches[0].data();
  console.log(`Antes:   ${before.email} | ${before.displayName} | ${before.role} | org ${before.orgId}`);
  await ref.update({ role: ROLE });
  const after = (await ref.get()).data();
  console.log(`Después: ${after.email} | ${after.displayName} | ${after.role} | org ${after.orgId}`);
})().catch((e) => { console.error(e.message); process.exit(1); });

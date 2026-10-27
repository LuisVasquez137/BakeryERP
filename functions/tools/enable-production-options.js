/**
 * Enciende (o apaga con --off) las opciones de producción de una empresa:
 * usesMadeForStock (pan para existencia, pedir a otra sucursal, transformar) y
 * usesBakedAhead (bizcochos horneados por adelantado). Lo mismo hace el dueño desde
 * Ajustes de la empresa; esto es para dejarlas puestas sin abrir la app.
 *
 * Uso: node enable-production-options.js [--company=<id>] [--off]
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const companyArg = process.argv.find((a) => a.startsWith('--company='));
const COMPANY = companyArg ? companyArg.split('=')[1] : 'tiO54slG1n9sk6k2ePCD';
const value = !process.argv.includes('--off');

(async () => {
  const ref = db.collection('organizations').doc(COMPANY);
  const snap = await ref.get();
  if (!snap.exists) throw new Error(`No existe organizations/${COMPANY}`);
  await ref.update({ usesMadeForStock: value, usesBakedAhead: value });
  const after = (await ref.get()).data();
  console.log(`${after.name}: usesMadeForStock=${after.usesMadeForStock} usesBakedAhead=${after.usesBakedAhead}`);
})().catch((e) => { console.error(e); process.exit(1); });

/**
 * Pone el tope de pan de colaborador (`dailyStaffBreadUnits`, panes por persona al día) en las
 * sucursales que venden. Es lo mismo que editarlo a mano en Sucursales → "Pan de colaborador".
 *
 * Escritura parcial (`update`): no toca el resto de la ficha de la sucursal.
 *
 * Uso (desde functions/):
 *   node tools/set-staff-bread-limit.js [--units=4] [--company=<id>] [--apply]
 * En seco por defecto: muestra qué cambiaría.
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const arg = (name) => {
  const a = process.argv.find((x) => x.startsWith(`--${name}=`));
  return a ? a.slice(name.length + 3) : null;
};
const COMPANY = arg('company') || 'tiO54slG1n9sk6k2ePCD';
const UNITS = Number(arg('units') || 4);
const APPLY = process.argv.includes('--apply');

if (!Number.isFinite(UNITS) || UNITS < 0) { console.error('--units no es válido'); process.exit(1); }

(async () => {
  const snap = await db.collection(`companies/${COMPANY}/branches`).get();
  let changed = 0;
  for (const doc of snap.docs) {
    const b = doc.data();
    const sells = (b.capabilities || []).includes('CAN_SELL');
    if (!sells || b.active === false) {
      console.log(`  ·  ${b.name} — no vende o inactiva, se omite`);
      continue;
    }
    const current = Number(b.dailyStaffBreadUnits) || 0;
    if (current === UNITS) {
      console.log(`  =  ${b.name} — ya tiene ${UNITS}`);
      continue;
    }
    console.log(`  →  ${b.name}: ${current} → ${UNITS}`);
    if (APPLY) await doc.ref.update({ dailyStaffBreadUnits: UNITS, updatedAt: Date.now() });
    changed++;
  }
  console.log(APPLY ? `\nListo: ${changed} sucursal(es) actualizadas.` : `\nEn seco: ${changed} cambiarían. Corre con --apply.`);
})().catch((e) => { console.error(e); process.exit(1); });

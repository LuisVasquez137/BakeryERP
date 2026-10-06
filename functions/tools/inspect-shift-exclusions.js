/**
 * Diagnóstico de solo lectura: lotes repartidos por turnos de los últimos días que
 * llevan un producto EXCLUIDO de ese turno, y a qué sucursales se le asignó.
 * No escribe nada.
 *
 * Uso: node inspect-shift-exclusions.js [--company=<id>] [--days=10]
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const arg = (k, d) => (process.argv.find((a) => a.startsWith(`--${k}=`)) || '').split('=')[1] || d;
const COMPANY = arg('company', 'tiO54slG1n9sk6k2ePCD');
const DAYS = Number(arg('days', '10'));
const base = db.collection('companies').doc(COMPANY);
const num = (v) => (v && typeof v.toMillis === 'function' ? v.toMillis() : Number(v || 0));

(async () => {
  const branches = (await base.collection('branches').get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  console.log('Sucursales con viaje restringido:');
  branches.filter((b) => Number(b.receivesOnlyInTrip || 0) > 0)
    .forEach((b) => console.log(`   ${b.name} (${b.id}) solo viaje ${b.receivesOnlyInTrip}`));
  const shifts = (await base.collection('productionShifts').get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  const shiftById = Object.fromEntries(shifts.map((s) => [s.id, s]));
  console.log('\nTurnos:');
  shifts.forEach((s) => console.log(`   ${s.name} (${s.id}) planta=${s.branchId || s.producingBranchId} viaje=${s.dispatchOrder} activo=${s.active} excluidos=${(s.excludedProductIds || []).length}`));

  const since = Date.now() - DAYS * 86400000;
  const lots = (await base.collection('productionLots').get()).docs
    .map((d) => ({ id: d.id, ...d.data() }))
    .filter((l) => num(l.productionDate) >= since && l.status !== 'CANCELLED' && l.shiftId)
    .sort((a, b) => num(a.productionDate) - num(b.productionDate));

  for (const lot of lots) {
    const shift = shiftById[lot.shiftId];
    const excluded = new Set((shift && shift.excludedProductIds) || []);
    const lines = (await base.collection('productionLots').doc(lot.id).collection('lines').get()).docs.map((d) => d.data());
    const bad = lines.filter((l) => excluded.has(l.productId));
    const day = new Date(num(lot.productionDate)).toISOString().slice(0, 10);
    console.log(`\nLOTE ${day} ${lot.producingBranchName} · ${lot.shiftName} (${lot.status}) split=${lot.isShiftSplit} líneas=${lines.length} excluidas-presentes=${bad.length}`);
    for (const l of bad) {
      const alloc = (l.soaOrderIds || []).map((id, i) => `${(l.soaBranchNames || [])[i]}=${(l.soaQuantities || [])[i]}`).join(', ');
      console.log(`   ✗ ${l.productName} planned=${l.plannedQuantity} → ${alloc || '(sin desglose)'}`);
    }
  }
})().catch((e) => { console.error(e); process.exit(1); });

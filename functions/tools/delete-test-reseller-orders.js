/**
 * Borra los pedidos de prueba de los revendedores de prueba (ya borrados, ver
 * delete-test-resellers.js). Antes revisa que ningún lote, línea de lote ni anticipo los use.
 *
 * Uso: node delete-test-reseller-orders.js [--apply]
 */
const fs = require('fs');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const base = db.doc('companies/tiO54slG1n9sk6k2ePCD');
const APPLY = process.argv.includes('--apply');
const ORDER_IDS = ['0nX4e7hVbR4NasI5IdHT', 'Zt9q41EPUUogyiNAoGdg', 'aQPAfIB7Ob26ek4cm32H', 's85O7ze4t7rVawqTfiz0', 'vE9piBXJigCBOPJcGkYU'];

(async () => {
  const ids = new Set(ORDER_IDS);
  const allLots = (await base.collection('productionLots').get()).docs;
  const lots = allLots.filter((d) => (d.data().customerOrderIds || []).some((x) => ids.has(x)));
  // Sin índice de grupo para customerOrderId: se recorren las líneas de cada lote.
  const lines = (await Promise.all(allLots.map((l) => l.ref.collection('lines').get())))
    .flatMap((q) => q.docs).filter((d) => ids.has(d.data().customerOrderId));
  const deposits = (await base.collection('customerDeposits').where('customerOrderId', 'in', ORDER_IDS).get()).docs;
  console.log(`Lotes que los usan: ${lots.length} · líneas de lote: ${lines.length} · anticipos: ${deposits.length}`);
  lots.forEach((d) => console.log('  lote', d.id)); lines.forEach((d) => console.log('  línea', d.ref.path));
  deposits.forEach((d) => console.log('  anticipo', d.id));
  if (lots.length || lines.length || deposits.length) { console.log('Hay referencias: no se borra.'); return; }

  const backup = [];
  for (const id of ORDER_IDS) {
    const ref = base.collection('customerOrders').doc(id);
    const doc = await ref.get();
    if (!doc.exists) continue;
    const ls = (await ref.collection('lines').get()).docs;
    backup.push({ id, data: doc.data(), lines: ls.map((l) => ({ id: l.id, ...l.data() })) });
    console.log(`- ${id} ${doc.data().customerName} ${doc.data().status} (${ls.length} líneas)`);
    if (APPLY) { for (const l of ls) await l.ref.delete(); await ref.delete(); }
  }
  if (!APPLY) { console.log('Solo vista. --apply para borrar.'); return; }
  const file = `backup_pedidos_revendedores_${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
  fs.writeFileSync(file, JSON.stringify(backup, null, 2));
  console.log(`Borrados ${backup.length} pedidos. Respaldo: ${file}`);
})().catch((e) => { console.error(e); process.exit(1); });

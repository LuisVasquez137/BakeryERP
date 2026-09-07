/**
 * Qué quedaría huérfano si se borran las compras: líneas, abonos, lotes FEFO y movimientos
 * de Kardex que las referencian, además del stock actual por sucursal.
 *
 * Solo lee. No escribe nada.
 *
 * Uso:  node inspect-purchase-footprint.js [--company=<id>]
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();

const companyArg = process.argv.find((a) => a.startsWith('--company='));
const COMPANY = companyArg ? companyArg.split('=')[1] : 'tiO54slG1n9sk6k2ePCD';

(async () => {
  const c = db.collection('companies').doc(COMPANY);

  const purchases = await c.collection('purchases').get();
  const purchaseIds = new Set(purchases.docs.map((d) => d.id));
  console.log(`COMPRAS: ${purchases.size}`);

  let lines = 0;
  let payments = 0;
  for (const doc of purchases.docs) {
    lines += (await doc.ref.collection('lines').get()).size;
    payments += (await doc.ref.collection('payments').get()).size;
  }
  console.log(`  líneas: ${lines} · abonos: ${payments}`);

  const lots = await c.collection('stockLots').get();
  const lotsFromPurchases = lots.docs.filter((d) => purchaseIds.has(d.data().purchaseId));
  console.log(`LOTES FEFO: ${lots.size} (de estas compras: ${lotsFromPurchases.length})`);
  lotsFromPurchases.forEach((d) => {
    const l = d.data();
    console.log(`   ${l.ingredientName} · saldo ${l.remainingQuantity} · vence ${l.expiryDate || 'sin fecha'}`);
  });

  const ingredients = await c.collection('ingredients').get();
  let movTotal = 0;
  let movFromPurchases = 0;
  const byType = {};
  for (const ing of ingredients.docs) {
    const movs = await ing.ref.collection('movements').get();
    movTotal += movs.size;
    movs.docs.forEach((m) => {
      const d = m.data();
      byType[d.type] = (byType[d.type] || 0) + 1;
      if (purchaseIds.has(d.reference)) movFromPurchases++;
    });
  }
  console.log(`KARDEX de insumos: ${movTotal} movimientos ${JSON.stringify(byType)}`);
  console.log(`  que referencian estas compras: ${movFromPurchases}`);

  const stocks = await c.collection('branchStocks').get();
  const conSaldo = stocks.docs.filter((d) => Number(d.data().currentStock) > 0);
  console.log(`STOCK POR SUCURSAL: ${stocks.size} registros, ${conSaldo.length} con saldo`);
  conSaldo.forEach((d) => {
    const s = d.data();
    console.log(`   ${(s.ingredientName || '?').padEnd(42)} ${s.currentStock}  (${s.branchName})`);
  });

  // Reventa: las compras de producto terminado también dejaron movimientos.
  const finished = await c.collection('finishedMovements').get();
  const finFromPurchases = finished.docs.filter((d) => purchaseIds.has(d.data().referenceId));
  console.log(`MOVIMIENTOS DE TERMINADO que referencian estas compras: ${finFromPurchases.length}`);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});

/**
 * Diagnóstico de solo lectura: compras recientes con líneas de reventa y lo que dejaron en
 * `finishedStocks` / `finishedMovements`. No escribe nada.
 *
 * Uso: node inspect-resale-stock.js [--company=<id>]
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const companyArg = process.argv.find((a) => a.startsWith('--company='));
const COMPANY = companyArg ? companyArg.split('=')[1] : 'tiO54slG1n9sk6k2ePCD';
const base = db.collection('companies').doc(COMPANY);

(async () => {
  const purchases = (await base.collection('purchases').get()).docs
    .map((d) => ({ id: d.id, ...d.data() }))
    .sort((a, b) => Number(b.createdAt || b.date || 0) - Number(a.createdAt || a.date || 0))
    .slice(0, 8);
  for (const p of purchases) {
    const lines = (await base.collection('purchases').doc(p.id).collection('lines').get()).docs.map((d) => d.data());
    console.log(`\nPURCHASE ${p.id} kind=${p.kind} status=${p.status} branch=${p.branchName}(${p.branchId}) supplier=${p.supplierName} total=${p.totalAmount} date=${new Date(Number(p.date)).toISOString()} lineKinds=${JSON.stringify(p.lineKinds)}`);
    for (const l of lines) {
      console.log(`   line kind=${l.lineKind} product=${l.productName}(${l.productId}) ingredient=${l.ingredientName} qty=${l.quantityReceived} pk=${l.packagesReceived} fmt=${l.formatId} branchOverride=${l.branchId}`);
    }
  }
  const stocks = (await base.collection('finishedStocks').get()).docs.map((d) => d.data());
  console.log(`\nfinishedStocks: ${stocks.length}`);
  stocks.slice(0, 40).forEach((s) => console.log(`   ${s.branchName}(${s.branchId}) ${s.productName} stock=${s.currentStock}`));
  const moves = (await base.collection('finishedMovements').where('referenceType', '==', 'PURCHASE').get()).docs.map((d) => d.data());
  console.log(`\nfinishedMovements PURCHASE: ${moves.length}`);
  moves.slice(0, 20).forEach((m) => console.log(`   ${m.branchName} ${m.productName} ${m.type} ${m.quantity} ref=${m.referenceId}`));
  const products = (await base.collection('products').where('isInHouseProduced', '==', false).get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  console.log(`\nresale products: ${products.length}`);
  products.forEach((p) => console.log(`   ${p.name}(${p.id}) active=${p.active} linkedIngredient=${p.linkedIngredientId} formats=${JSON.stringify(p.purchaseFormatDescriptions)} supplierIds=${JSON.stringify(p.purchaseFormatSupplierIds)} legacySupplier=${p.supplierId}`));
})().catch((e) => { console.error(e); process.exit(1); });

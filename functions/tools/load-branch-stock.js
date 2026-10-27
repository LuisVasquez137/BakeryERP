/**
 * Carga un conteo físico al inventario de tienda de una sucursal: deja cada producto en la
 * cantidad contada con un AJUSTE por la diferencia (corregir = escribir la diferencia).
 *
 * A diferencia de "Ajustar" en la app (que entra a costo 0 y arrastra el promedio a 0), el
 * ingreso entra al costo promedio que ya tenía el producto en esa sucursal, o a su
 * `purchaseCost` si es de reventa y nunca tuvo stock.
 *
 * El archivo es JSON: { "branchId": "...", "reason": "...", "items": [{ "name": "<nombre exacto
 * del producto>", "qty": 12, "note": "lo que decía la hoja" }] }. Si un nombre no existe o se
 * repite, no se escribe nada.
 *
 * Uso (desde functions/): node tools/load-branch-stock.js <archivo.json> [--apply]
 */
const fs = require('fs');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const COMPANY = 'tiO54slG1n9sk6k2ePCD';
const base = db.collection('companies').doc(COMPANY);
const file = process.argv[2];
const APPLY = process.argv.includes('--apply');
if (!file) { console.error('Falta el archivo'); process.exit(1); }
const spec = JSON.parse(fs.readFileSync(file, 'utf8'));

(async () => {
  const branch = (await base.collection('branches').doc(spec.branchId).get()).data();
  if (!branch) throw new Error(`No existe la sucursal ${spec.branchId}`);
  const products = (await base.collection('products').get()).docs.map((d) => ({ id: d.id, ...d.data() }));

  const seen = new Set();
  const problems = [];
  const rows = spec.items.map((it) => {
    const matches = products.filter((p) => p.name === it.name);
    if (matches.length !== 1) problems.push(`"${it.name}": ${matches.length} productos con ese nombre`);
    const p = matches[0];
    if (p && seen.has(p.id)) problems.push(`"${it.name}" viene dos veces`);
    if (p) seen.add(p.id);
    return { ...it, product: p };
  });
  if (problems.length) { console.error(problems.join('\n')); process.exit(1); }

  console.log(`${branch.name} · ${rows.length} productos · ${APPLY ? 'APLICANDO' : 'EN SECO'}\n`);
  const now = Date.now();
  const refId = `conteo-${spec.branchId.slice(0, 6)}-${new Date(now).toISOString().slice(0, 10)}`;
  for (const r of rows) {
    const p = r.product;
    const stockRef = base.collection('finishedStocks').doc(`${spec.branchId}_${p.id}`);
    const apply = async (tx, write) => {
      const snap = await tx.get(stockRef);
      const cur = snap.exists ? snap.data() : null;
      const before = Number(cur?.currentStock) || 0;
      const avg = Number(cur?.avgUnitCost) || 0;
      const unitCost = avg > 0 ? avg : Number(p.purchaseCost) || 0;
      const delta = r.qty - before;
      const line = `${p.name.padEnd(48)} ${String(before).padStart(5)} → ${String(r.qty).padStart(5)}  costo ${unitCost.toFixed(2)}${r.note ? `  (${r.note})` : ''}`;
      if (!write || Math.abs(delta) < 1e-9) return line;
      const newAvg = delta > 0 && r.qty > 0 ? (before * avg + delta * unitCost) / r.qty : avg;
      tx.set(stockRef, {
        id: stockRef.id,
        branchId: spec.branchId,
        branchName: branch.name,
        productId: p.id,
        productName: p.name,
        currentStock: r.qty,
        avgUnitCost: newAvg,
        updatedAt: now,
      }, { merge: true });
      const moveRef = base.collection('finishedMovements').doc();
      tx.set(moveRef, {
        id: moveRef.id,
        branchId: spec.branchId,
        branchName: branch.name,
        productId: p.id,
        productName: p.name,
        type: 'AJUSTE',
        quantity: delta,
        unitPriceAtMovement: delta > 0 ? unitCost : 0,
        referenceType: 'ADJUSTMENT',
        referenceId: refId,
        reason: spec.reason,
        createdAt: now,
        createdByUserId: '',
        createdByUserName: 'Conteo inicial',
      });
      return line;
    };
    const line = APPLY
      ? await db.runTransaction((tx) => apply(tx, true))
      : await apply({ get: (ref) => ref.get() }, false);
    console.log(line);
  }
  if (!APPLY) console.log('\nEn seco: no se escribió nada. Repetir con --apply.');
  else console.log(`\nListo (referencia ${refId}).`);
})().catch((e) => { console.error(e); process.exit(1); });

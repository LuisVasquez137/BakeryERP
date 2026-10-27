/**
 * Diagnóstico de solo lectura: sucursales, productos para existencia y cuántas piezas da un
 * quintal según su receta y su peso en crudo. No escribe nada.
 *
 * Uso: node inspect-made-for-stock.js [--company=<id>]
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const companyArg = process.argv.find((a) => a.startsWith('--company='));
const COMPANY = companyArg ? companyArg.split('=')[1] : 'tiO54slG1n9sk6k2ePCD';
const base = db.collection('companies').doc(COMPANY);
const GRAMS_PER_QUINTAL = 100 * 453.592;

(async () => {
  const products = (await base.collection('products').get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  const byId = Object.fromEntries(products.map((p) => [p.id, p]));
  const branches = (await base.collection('branches').get()).docs.map((d) => ({ id: d.id, ...d.data() }));
  for (const b of branches) {
    const kept = (b.assignedProductIds || []).map((id) => byId[id]).filter((p) => p && p.isMadeForStock).map((p) => p.name);
    console.log(`BRANCH ${b.name} caps=${JSON.stringify(b.capabilities)} paraExistencia=${JSON.stringify(kept)}`);
  }
  const stockProducts = products.filter((p) => p.isMadeForStock);
  const recipes = (await base.collection('recipes').get()).docs;
  for (const p of stockProducts) {
    let line = `PRODUCT ${p.name} min=${p.plantStockMinimum} raw=${p.rawWeightGrams} unit=${p.unit} transformFrom=${p.transformFromProductName || ''}`;
    for (const r of recipes) {
      const yields = (await r.ref.collection('yields').get()).docs.map((d) => d.data());
      if (!yields.some((y) => y.productId === p.id)) continue;
      const comps = (await r.ref.collection('components').get()).docs.map((d) => d.data());
      const sum = comps.reduce((s, c) => s + Number(c.percentage || 0), 0);
      const hasBase = comps.some((c) => c.isBase);
      const upq = p.rawWeightGrams && hasBase ? GRAMS_PER_QUINTAL * sum / 100 / p.rawWeightGrams : null;
      line += ` | receta=${r.data().name} suma%=${sum} base=${hasBase} piezasPorQuintal=${upq ? upq.toFixed(1) : 'no se puede'}`;
    }
    console.log(line);
  }
})();

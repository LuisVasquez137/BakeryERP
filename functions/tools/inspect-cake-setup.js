/**
 * Solo lectura: cómo está configurado un pastel con bizcocho (producto, adornos, recetas que
 * lo generan, preparaciones de bizcocho y las últimas líneas de lote que lo traen).
 * Uso: node inspect-cake-setup.js "selva"
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const COMPANY = 'tiO54slG1n9sk6k2ePCD';
const term = (process.argv[2] || 'selva').toLowerCase();
const c = db.collection('companies').doc(COMPANY);
(async () => {
  const products = (await c.collection('products').get()).docs.filter((d) => (d.data().name || '').toLowerCase().includes(term));
  for (const p of products) {
    const d = p.data();
    console.log('PRODUCTO', p.id, d.name, { rawWeightGrams: d.rawWeightGrams, decoPrep: d.decoPreparationIds || d.decorationPreparationIds, decoUnits: d.decoUnitsPerProduct, keys: Object.keys(d).filter((k) => k.toLowerCase().includes('deco')) });
    for (const k of Object.keys(d).filter((k) => k.toLowerCase().includes('deco'))) console.log('   ', k, JSON.stringify(d[k]));
  }
  const recipes = (await c.collection('recipes').get()).docs;
  for (const r of recipes) {
    const y = (await r.ref.collection('yields').get()).docs.map((x) => x.data());
    const hit = y.filter((x) => products.some((p) => p.id === x.productId));
    if (hit.length || (r.data().name || '').toLowerCase().includes(term)) console.log('RECETA', r.id, r.data().name, 'genera:', y.map((x) => x.productName || x.productId).join(', '));
  }
  const preps = (await c.collection('preparations').get()).docs;
  for (const p of preps) { const d = p.data(); if (['BATTER', 'BASE'].includes(d.usage)) console.log('PREP', d.usage, p.id, d.name, { batter: d.batterPreparationName, g: d.batterGramsPerUnit, min: d.stockMinimumUnits, branchIds: d.branchIds }); }
})().catch((e) => { console.error(e); process.exit(1); });

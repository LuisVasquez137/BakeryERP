/**
 * Saca un pastel de "Productos que genera" de las recetas que lo tienen: el pastel que va
 * sobre bizcocho ya trae su masa en el bizcocho, y dejarlo en la receta descontaría la masa
 * dos veces. Respaldo de los rendimientos borrados en backup_yields_*.json; yieldCount se
 * recalcula contando la subcolección.
 * Uso: node remove-cake-from-recipes.js <productId> [--company=<id>]
 */
const fs = require('fs');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const companyArg = process.argv.find((a) => a.startsWith('--company='));
const COMPANY = companyArg ? companyArg.split('=')[1] : 'tiO54slG1n9sk6k2ePCD';
const PRODUCT = process.argv[2];
if (!PRODUCT) { console.error('Falta productId'); process.exit(1); }
const c = db.collection('companies').doc(COMPANY);
(async () => {
  const backup = [];
  for (const r of (await c.collection('recipes').get()).docs) {
    const hits = await r.ref.collection('yields').where('productId', '==', PRODUCT).get();
    if (hits.empty) continue;
    hits.docs.forEach((y) => backup.push({ recipeId: r.id, recipeName: r.data().name, yieldId: y.id, data: y.data() }));
  }
  const file = `backup_yields_${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
  fs.writeFileSync(file, JSON.stringify(backup, null, 2));
  console.log(`Respaldo: ${file} (${backup.length})`);
  for (const b of backup) {
    const ref = c.collection('recipes').doc(b.recipeId);
    await ref.collection('yields').doc(b.yieldId).delete();
    const count = (await ref.collection('yields').get()).size;
    await ref.update({ yieldCount: count, updatedAt: Date.now() });
    console.log(`${b.recipeName}: quitado ${b.data.productName}, ahora genera ${count}`);
  }
})().catch((e) => { console.error(e); process.exit(1); });

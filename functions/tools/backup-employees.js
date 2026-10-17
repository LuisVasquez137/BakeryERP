/**
 * Respaldo JSON de las fichas de colaboradores (ficha pública + private/*), antes de una
 * migración. Sale a backup_employees_<fecha>.json (gitignored: trae DPI y salarios).
 *   node backup-employees.js [--company=<id>]
 */
const fs = require('fs');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const arg = (k, d) => (process.argv.find((a) => a.startsWith(`--${k}=`)) || '').split('=')[1] || d;
const COMPANY = arg('company', 'tiO54slG1n9sk6k2ePCD');

(async () => {
  const employees = await db.collection(`companies/${COMPANY}/employees`).get();
  const out = [];
  for (const emp of employees.docs) {
    const priv = await emp.ref.collection('private').get();
    out.push({
      id: emp.id,
      data: emp.data(),
      private: Object.fromEntries(priv.docs.map((d) => [d.id, d.data()])),
    });
  }
  const file = `backup_employees_${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
  fs.writeFileSync(file, JSON.stringify(out, null, 2));
  console.log(`${out.length} fichas → ${file}`);
})().catch((e) => { console.error(e); process.exit(1); });

/**
 * Crea (o actualiza) el documento de configuración del puente con ComandaYa.
 *
 * Es lo primero que hay que correr: sin él `ingestExternalSale` rechaza todo, porque no sabe
 * a qué empresa ni a qué sucursal de BakeOS corresponde cada sucursal de ComandaYa.
 *
 * Solo Sabreopan usa esto. Las 3 sucursales del mapa son las que tienen restaurante; las
 * otras 2 de BakeOS no aparecen a propósito — no existen en ComandaYa.
 *
 * Uso:
 *   node tools/setup-comandaya-integration.js --company=<orgId>            (muestra qué haría)
 *   node tools/setup-comandaya-integration.js --company=<orgId> --apply    (escribe)
 *
 * Verifica que cada sucursal de BakeOS del mapa exista antes de escribir: un id mal copiado
 * dejaría ventas entrando a una sucursal fantasma, y eso se descubre semanas después.
 */

const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();

const APPLY = process.argv.includes('--apply');
const companyArg = process.argv.find((a) => a.startsWith('--company='));
const COMPANY_ID = companyArg ? companyArg.split('=')[1] : '';

const RESTAURANT_ID = 'cUVIThV5fCkC3PKfDByD';

/** ComandaYa branchId → BakeOS branchId. El nombre es solo para leer la salida. */
const BRANCHES = [
  { name: 'Democracia',          comandaya: 'BUQ8IT24TFi30k3wejFS', bakeos: '7c72132c7c97052f49228125523aa886' },
  { name: 'Salida de Almolonga', comandaya: 'gUBoON5MANYwjNhXLdD3', bakeos: 'd8d272024e5b75373ba09a7acced5d51' },
  { name: 'Bombero',             comandaya: 'FqwYhNkP63iCIwRnpDu8', bakeos: '47824fd8d75f146b360080b66951ae99' }
];

async function main() {
  if (!COMPANY_ID) {
    console.error('Falta --company=<orgId de Sabreopan en BakeOS>');
    process.exit(1);
  }

  console.log(`Empresa: ${COMPANY_ID}`);
  console.log(`Restaurante en ComandaYa: ${RESTAURANT_ID}\n`);

  let allOk = true;
  const branchMap = {};

  for (const b of BRANCHES) {
    const snap = await db.doc(`companies/${COMPANY_ID}/branches/${b.bakeos}`).get();
    if (!snap.exists) {
      console.error(`  ✗ ${b.name}: la sucursal ${b.bakeos} NO existe en BakeOS`);
      allOk = false;
      continue;
    }
    console.log(`  ✓ ${b.name}: ${b.comandaya} → ${b.bakeos} ("${snap.data().name || '?'}")`);
    branchMap[b.comandaya] = b.bakeos;
  }

  if (!allOk) {
    console.error('\nHay sucursales que no existen. No se escribe nada.');
    process.exit(1);
  }

  const config = {
    companyId: COMPANY_ID,
    enabled: true,
    timeZone: 'America/Guatemala',
    externalTenantId: RESTAURANT_ID,
    branchMap,
    updatedAt: Date.now()
  };

  console.log(`\n${JSON.stringify(config, null, 2)}`);

  if (!APPLY) {
    console.log('\n(simulación — volvé a correrlo con --apply para escribirlo)');
    return;
  }

  await db.doc('config/comandayaIntegration').set(config, { merge: true });
  console.log('\n✓ Escrito en config/comandayaIntegration');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

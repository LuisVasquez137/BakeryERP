#!/usr/bin/env node
/**
 * Marca como "pendiente de avisar a ComandaYa" los DTEs de producción que BakeOS certificó
 * antes de que existiera el aviso. No manda nada por su cuenta: el job felReintentarPendientes
 * (cada 5 minutos) los encuentra y los reporta, y ComandaYa no cuenta dos veces el mismo DTE.
 *
 *   node functions/tools/fel-backfill-comandaya.js
 */

const req = require("module").createRequire(require("path").join(__dirname, "..", "package.json"));
const { initializeApp } = req("firebase-admin/app");
const { getFirestore } = req("firebase-admin/firestore");

initializeApp({ projectId: "bakery-erp-saas" });
const db = getFirestore();

async function main() {
  const config = (await db.doc("config/comandayaIntegration").get()).data() || {};
  if (!config.companyId) throw new Error("La integración con ComandaYa no tiene companyId");
  const dtes = await db.collection(`companies/${config.companyId}/dtes`).get();
  let marcados = 0;
  for (const d of dtes.docs) {
    const x = d.data();
    // Certificados y anulados: los dos gastaron su DTE en Digifact.
    if (!x.uuid || x.ambiente !== "PRODUCCION" || x.comandayaReportado !== undefined) continue;
    await d.ref.set({ comandayaReportado: false }, { merge: true });
    console.log(`  ✔ ${d.id} (${x.serie}-${x.numero}, Q${x.granTotal})`);
    marcados++;
  }
  console.log(`${marcados} DTE(s) quedan para avisar a ComandaYa en el próximo reintento.`);
}

main().catch((e) => { console.error("✖", e.message); process.exit(1); });

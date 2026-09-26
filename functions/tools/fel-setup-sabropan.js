#!/usr/bin/env node
/**
 * Crea (o actualiza) el perfil FEL de Sabro Pan en BakeOS y le asigna sus sucursales.
 * Idempotente: si ya hay un perfil con ese NIT lo reutiliza en vez de duplicarlo.
 *
 * NO escribe credenciales: la contraseña de Digifact se pone desde la app (Facturación →
 * el NIT → Credenciales), que la prueba contra Digifact antes de guardarla.
 *
 *   node functions/tools/fel-setup-sabropan.js
 */

const req = require("module").createRequire(require("path").join(__dirname, "..", "package.json"));
const { initializeApp } = req("firebase-admin/app");
const { getFirestore } = req("firebase-admin/firestore");

initializeApp({ projectId: "bakery-erp-saas" });
const db = getFirestore();

const COMPANY_ID = "tiO54slG1n9sk6k2ePCD"; // Sabropan
const NIT = "59325216";
// Mismos códigos de establecimiento que ComandaYa. Calvario y Salida facturan con otro NIT.
const ESTABLECIMIENTOS = {
  "47824fd8d75f146b360080b66951ae99": "1", // Bombero
  "e18b0bbbd304263921b55963878867c7": "2", // Pacajá
  "7c72132c7c97052f49228125523aa886": "3", // Democracia
};

async function main() {
  const col = db.collection(`companies/${COMPANY_ID}/felProfiles`);
  const existente = (await col.where("nit", "==", NIT).limit(1).get()).docs[0];
  const ref = existente ? existente.ref : col.doc();

  await ref.set({
    id: ref.id,
    nit: NIT,
    nombreEmisor: "VÁSQUEZ,FLORES,,LUIS,FERNANDO",
    nombreComercial: "SABRO PAN",
    correo: "luisvasquez1307@gmail.com",
    direccion: "4 CALLE 33-05 ADICIONAL ZONA 8",
    municipio: "QUETZALTENANGO",
    departamento: "QUETZALTENANGO",
    codigoPostal: "09001",
    regimenISR: "OPCIONAL",
    afiliacionIVA: "PEQ",
    active: true,
    updatedAt: Date.now(),
  }, { merge: true });
  console.log(`${existente ? "Actualizado" : "Creado"} perfil ${ref.id} (NIT ${NIT})`);

  for (const [branchId, codigo] of Object.entries(ESTABLECIMIENTOS)) {
    const bref = db.doc(`companies/${COMPANY_ID}/branches/${branchId}`);
    const snap = await bref.get();
    if (!snap.exists) { console.warn(`  ✖ sucursal ${branchId} no existe`); continue; }
    await bref.update({ felProfileId: ref.id, felCodigoEstablecimiento: codigo, updatedAt: Date.now() });
    console.log(`  ✔ ${snap.data().name} → establecimiento ${codigo}`);
  }
}

main().catch((e) => { console.error("✖", e.message); process.exit(1); });

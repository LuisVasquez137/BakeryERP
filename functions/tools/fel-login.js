#!/usr/bin/env node
/**
 * Solo prueba el login contra Digifact: no certifica nada ni consume DTEs.
 * Pide la contraseña en la consola sin mostrarla (o la toma de DIGIFACT_PASSWORD).
 *
 *   node tools/fel-login.js [NIT] [TEST]
 */

const { DigifactClient } = require("../fel/digifactClient");
const { URLS } = require("../fel/felCredenciales");
const { pedirOculto } = require("./consola");

const nit = process.argv[2] || process.env.DIGIFACT_NIT || "59325216";
const ambiente = (process.argv[3] || process.env.DIGIFACT_AMBIENTE) === "TEST" ? "TEST" : "PRODUCCION";

(async () => {
  const password = process.env.DIGIFACT_PASSWORD || (await pedirOculto(`Contraseña Digifact (NIT ${nit}): `));
  if (!password) { console.error("No se escribió contraseña."); process.exit(1); }
  try {
    await new DigifactClient({ baseUrl: URLS[ambiente], taxId: nit, user: nit, password, ambiente }).getToken(true);
    console.log(`✔ Login correcto (${ambiente}, NIT ${nit})`);
  } catch (e) {
    console.error(`✖ Falló el login (${ambiente}):`, e.message);
    process.exit(1);
  }
})();

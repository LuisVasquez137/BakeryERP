#!/usr/bin/env node
/**
 * fel-probar.js con los datos de emisor de Sabro Pan (perfil fiscal ipA7Rm2phbiyNX8qahd5 de
 * ComandaYa), en PRODUCCION y anulando al final. Pide la contraseña en la consola.
 *
 *   node tools/fel-probar-sabropan.js
 *
 * ⚠️ Emite una factura REAL de Q1.90 ante la SAT y la anula.
 */

const defaults = {
  DIGIFACT_NIT: "59325216",
  DIGIFACT_AMBIENTE: "PRODUCCION",
  NOMBRE_EMISOR: "VÁSQUEZ,FLORES,,LUIS,FERNANDO",
  NOMBRE_COMERCIAL: "SABRO PAN",
  DIRECCION: "4 CALLE 33-05 ADICIONAL ZONA 8",
  CODIGO_POSTAL: "09001",
  MUNICIPIO: "QUETZALTENANGO",
  DEPARTAMENTO: "QUETZALTENANGO",
  ESTABLECIMIENTO: "1",
  AFILIACION_IVA: "PEQ",
  REGIMEN_ISR: "OPCIONAL",
  ANULAR: "1",
};
for (const [k, v] of Object.entries(defaults)) {
  if (!process.env[k]) process.env[k] = v;
}

require("./fel-probar");

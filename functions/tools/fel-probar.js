#!/usr/bin/env node
/**
 * Prueba de punta a punta contra Digifact, sin desplegar nada y sin tocar Firestore:
 * login → consulta de NIT → certificación de una factura de muestra → (opcional) anulación.
 *
 * Las credenciales van por variables de entorno para que no queden en el código ni en
 * Firestore. Por defecto usa el ambiente TEST.
 *
 *   DIGIFACT_NIT=5932216 DIGIFACT_USER=... DIGIFACT_PASSWORD=... \
 *   [DIGIFACT_AMBIENTE=TEST] [ESTABLECIMIENTO=1] [ANULAR=1] [NIT_CONSULTA=...] \
 *   node tools/fel-probar.js
 *
 * ⚠️ Con DIGIFACT_AMBIENTE=PRODUCCION la factura es REAL ante la SAT y consume un DTE del
 * paquete. Para producción, usar ANULAR=1 o no correrlo.
 */

const { DigifactClient, serieYNumero } = require("../fel/digifactClient");
const { URLS } = require("../fel/felCredenciales");
const { buildFacturaNuc, fechaEmisionParaAnular } = require("../fel/nucXmlBuilder");
const { pedirOculto } = require("./consola");

async function main() {
  const nit = process.env.DIGIFACT_NIT;
  const user = process.env.DIGIFACT_USER || nit;
  const password = process.env.DIGIFACT_PASSWORD || (nit ? await pedirOculto(`Contraseña Digifact (NIT ${nit}): `) : "");
  const ambiente = process.env.DIGIFACT_AMBIENTE === "PRODUCCION" ? "PRODUCCION" : "TEST";
  if (!nit || !user || !password) {
    console.error("Faltan DIGIFACT_NIT, DIGIFACT_USER o DIGIFACT_PASSWORD.");
    process.exit(1);
  }
  console.log(`Ambiente: ${ambiente}  NIT: ${nit}`);

  const client = new DigifactClient({ baseUrl: URLS[ambiente], taxId: nit, user, password, ambiente });
  await client.getToken(true);
  console.log("✔ Login correcto");

  if (process.env.NIT_CONSULTA) {
    const info = await client.consultarNIT(process.env.NIT_CONSULTA);
    console.log("✔ Consulta de NIT:", info || "sin datos");
  }

  const emitidaEn = Date.now();
  const { xml, granTotal, docType } = buildFacturaNuc({
    emisor: {
      nit,
      nombreEmisor: process.env.NOMBRE_EMISOR || "PRUEBA BAKEOS",
      nombreComercial: process.env.NOMBRE_COMERCIAL || "PRUEBA BAKEOS",
      direccion: process.env.DIRECCION || "CIUDAD",
      codigoPostal: process.env.CODIGO_POSTAL,
      municipio: process.env.MUNICIPIO,
      departamento: process.env.DEPARTAMENTO,
      correo: process.env.CORREO,
      codigoEstablecimiento: process.env.ESTABLECIMIENTO || "1",
      afiliacionIVA: process.env.AFILIACION_IVA || "GEN",
      regimenISR: process.env.REGIMEN_ISR || "OPCIONAL",
    },
    receptor: { id: "CF", nombre: "CONSUMIDOR FINAL" },
    // Montos mínimos a propósito: en PRODUCCION esta factura es real.
    lines: [
      { description: "Pan de Q0.50", qty: 2, unitPrice: 0.5 },
      { description: "Prueba con descuento", qty: 1, unitPrice: 1.0, discount: 0.1 },
    ],
    referenciaInterna: `BKOS-PRUEBA-${emitidaEn}`,
    observaciones: "Prueba de integración BakeOS",
    // La misma fecha se manda al anular: CancelFelGT la compara con la del documento.
    fechaHora: `${fechaEmisionParaAnular(emitidaEn)}-06:00`,
  });
  console.log(`Certificando ${docType} de muestra por Q${granTotal}…`);
  const resp = await client.certificarDocumento(nit, xml);
  if (!resp.success) {
    console.error("✖ Rechazada:", resp.message, resp.description, resp.errors);
    process.exit(2);
  }
  const { serie, numero } = serieYNumero(resp.xmlCertificado, resp);
  console.log(`✔ Certificada. Serie ${serie}  Número ${numero}  Autorización ${resp.authNumber}`);

  if (process.env.ANULAR === "1") {
    const anul = await client.anularDocumento(nit, {
      autorizacion: resp.authNumber,
      idReceptor: "CF",
      fechaEmision: fechaEmisionParaAnular(emitidaEn),
      motivo: "Prueba de integracion BakeOS",
    });
    console.log(anul.success ? "✔ Anulada" : `✖ No se pudo anular: ${anul.message} ${anul.description || ""} ${JSON.stringify(anul.errors || "")}`);
  }
}

main().catch((e) => { console.error("✖", e.message); process.exit(1); });

// Pruebas de la parte pura de la facturación: `node --test fel/`
const test = require("node:test");
const assert = require("node:assert/strict");
const {
  buildFacturaNuc, buildItems, prorateDiscount, referenciaInterna, numeroALetras, idReceptorParaXml,
} = require("./nucXmlBuilder");

const emisor = {
  nit: "593221-6",
  nombreEmisor: "EMPRESA DE PRUEBA",
  nombreComercial: "Panadería Bombero",
  direccion: "Zona 1",
  codigoEstablecimiento: "1",
  afiliacionIVA: "GEN",
  regimenISR: "OPCIONAL",
};

const valor = (xml, tag) => [...xml.matchAll(new RegExp(`<${tag}>([^<]*)</${tag}>`, "g"))].map((m) => Number(m[1]));

test("cada item cumple Total = Qty × Price − Descuento, e IVA = Total − Total/1.12", () => {
  const { items, granTotal, totalIVA } = buildItems([
    { description: "Pan de Q0.50", qty: 30, unitPrice: 0.5 },
    { description: "Galletas", qty: 2, unitPrice: 12.5, discount: 2.5 },
  ]);
  assert.equal(items.length, 2);
  assert.equal(granTotal, 15 + 22.5);
  const iva1 = 15 - Math.round(15 / 1.12 * 100) / 100;
  const iva2 = 22.5 - Math.round(22.5 / 1.12 * 100) / 100;
  assert.equal(totalIVA, Math.round((iva1 + iva2) * 100) / 100);
  assert.match(items[1], /<Discount>\s*<Amount>2\.500000<\/Amount>/);
});

test("los totales del documento son la suma exacta de los items", () => {
  const { xml, granTotal, totalIVA, docType } = buildFacturaNuc({
    emisor,
    receptor: { id: "CF", nombre: "CONSUMIDOR FINAL" },
    lines: [
      { description: "Pastel", qty: 1, unitPrice: 85 },
      { description: "Café", qty: 3, unitPrice: 7.25, discount: 1.1 },
    ],
    referenciaInterna: "BKOS-TEST-1",
  });
  assert.equal(docType, "FACT");
  const totalItems = valor(xml, "TotalItem").reduce((a, b) => a + b, 0);
  assert.equal(Math.round(totalItems * 100) / 100, granTotal);
  const ivaItems = [...xml.matchAll(/<Tax>[\s\S]*?<Amount>([^<]*)<\/Amount>/g)].map((m) => Number(m[1]));
  assert.equal(Math.round(ivaItems.reduce((a, b) => a + b, 0) * 100) / 100, totalIVA);
  assert.match(xml, /<InvoiceTotal>105\.650000<\/InvoiceTotal>/);
  assert.match(xml, /<TaxID>5932216<\/TaxID>/, "el NIT va sin guion ni ceros");
  assert.match(xml, /VALIDAR_REFERENCIA_INTERNA" Value="VALIDAR"/);
});

test("pequeño contribuyente: FPEQ, sin impuestos y una sola frase", () => {
  const { xml, docType } = buildFacturaNuc({
    emisor: { ...emisor, afiliacionIVA: "PEQ" },
    receptor: { id: "CF" },
    lines: [{ description: "Pan", qty: 10, unitPrice: 1 }],
    referenciaInterna: "BKOS-TEST-2",
  });
  assert.equal(docType, "FPEQ");
  assert.doesNotMatch(xml, /<Taxes>/);
  assert.equal((xml.match(/TipoFrase/g) || []).length, 1);
});

test("sin detalle: una sola línea por el mismo total", () => {
  const { xml, granTotal } = buildFacturaNuc({
    emisor,
    receptor: { id: "CF" },
    lines: [{ description: "A", qty: 3, unitPrice: 4.33 }, { description: "B", qty: 1, unitPrice: 10 }],
    referenciaInterna: "BKOS-TEST-3",
    descripcionUnica: "Venta de productos",
  });
  assert.equal((xml.match(/<Item /g) || []).length, 1);
  assert.equal(granTotal, 22.99);
});

test("las líneas en cero no entran (la SAT no acepta items sin monto)", () => {
  const { items } = buildItems([
    { description: "Regalo", qty: 1, unitPrice: 5, discount: 5 },
    { description: "Pan", qty: 2, unitPrice: 1 },
  ]);
  assert.equal(items.length, 1);
});

test("el descuento global se reparte al centavo entre las líneas", () => {
  const lines = prorateDiscount([
    { qty: 1, unitPrice: 10 }, { qty: 1, unitPrice: 10 }, { qty: 1, unitPrice: 10 },
  ], 1);
  const total = lines.reduce((a, l) => a + l.discount, 0);
  assert.equal(Math.round(total * 100), 100);
  assert.deepEqual(lines.map((l) => l.discount).sort(), [0.33, 0.33, 0.34]);
});

test("la referencia interna es estable ante el orden de las ventas y distinta por cobro", () => {
  assert.equal(referenciaInterna("c1", ["b", "a"]), referenciaInterna("c1", ["a", "b"]));
  assert.notEqual(referenciaInterna("c1", ["a"]), referenciaInterna("c1", ["a", "b"]));
  assert.ok(referenciaInterna("c1", ["a"]).startsWith("BKOS-"));
  // Volver a facturar tras anular cambia la referencia; la versión 0 es la de siempre.
  assert.equal(referenciaInterna("c1", ["a"], 0), referenciaInterna("c1", ["a"]));
  assert.notEqual(referenciaInterna("c1", ["a"], 1), referenciaInterna("c1", ["a"]));
  assert.notEqual(referenciaInterna("c1", ["a"], 2), referenciaInterna("c1", ["a"], 1));
});

test("receptor y montos en letras", () => {
  assert.equal(idReceptorParaXml("1234567-K"), "1234567K");
  assert.equal(idReceptorParaXml(""), "CF");
  assert.equal(numeroALetras(197.5), "CIENTO NOVENTA Y SIETE QUETZALES CON 50/100");
  assert.equal(numeroALetras(0.5), "CERO QUETZALES CON 50/100");
});

// functions/fel/nucXmlBuilder.js
//
// Arma el cuerpo NUC (`<Root>…`) que certifica Digifact en POST {base}/v2/transform/nuc.
//
// Portado de ComandaYa (OrderApp/functions/fel/nucXmlBuilder.js), donde se certificó en
// vivo. La diferencia de fondo: ComandaYa arma los items desde platillos de una orden y
// NUNCA manda descuentos, así que una orden con promoción se facturaba por más de lo que
// se cobró. BakeOS trabaja con líneas genéricas de venta, cada una con su descuento, y lo
// declara en `<Discounts>` como exige el esquema: la factura dice exactamente lo cobrado.
//
// Matemática (ejemplo oficial "NUC 1 - FACT mas 1 item.xml"):
//   Price       = precio unitario CON IVA
//   TotalItem   = Qty × Price − Descuento
//   Gravable    = TotalItem / 1.12,   IVA = TotalItem − Gravable
// Cada línea se redondea a centavos y los totales se suman de las líneas ya redondeadas,
// así <TotalTaxes> y <GrandTotal> cuadran exactos con los items (la SAT lo compara).

const crypto = require("crypto");

const r2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;

/**
 * @param {object}   p
 * @param {object}   p.emisor     { nit, nombreEmisor, nombreComercial, correo, direccion,
 *                                  municipio, departamento, codigoPostal,
 *                                  codigoEstablecimiento, afiliacionIVA, regimenISR }
 * @param {object}   p.receptor   { id: NIT|CUI|'CF', nombre }
 * @param {object[]} p.lines      [{ description, qty, unitPrice, discount, type }]
 *                                unitPrice con IVA incluido; discount en quetzales.
 * @param {string}   p.referenciaInterna  única por documento; va en modo VALIDAR.
 * @param {string}   [p.observaciones]
 * @param {string}   [p.descripcionUnica] factura "sin detalle": una sola línea por el total.
 * @param {string}   [p.fechaHora]        ISO con -06:00 (por defecto, ahora en Guatemala).
 */
function buildFacturaNuc({ emisor, receptor, lines, referenciaInterna, observaciones, descripcionUnica, fechaHora }) {
  const peq = esPequenoContribuyente(emisor);
  const docType = peq ? "FPEQ" : "FACT";
  const conIva = !peq;

  let { items, granTotal, totalIVA } = buildItems(lines, conIva);
  if (!items.length) throw new Error("La venta no tiene líneas con monto para facturar.");

  // Sin detalle: mismo total, una sola línea. El IVA se recalcula sobre el total para que
  // el item y <TotalTaxes> coincidan al centavo.
  if (descripcionUnica) {
    const c = itemConsolidado(descripcionUnica, granTotal, conIva);
    items = [c.xml];
    totalIVA = c.iva;
  }

  if (!referenciaInterna) throw new Error("Falta la referencia interna del documento.");

  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<Root>
  <Version>1.00</Version>
  <CountryCode>GT</CountryCode>
  <Header>
    <DocType>${docType}</DocType>
    <IssuedDateTime>${fechaHora || nowGuatemalaISO()}</IssuedDateTime>
    <Currency>GTQ</Currency>
  </Header>
  <Seller>
    <TaxID>${esc(nitParaXml(emisor.nit))}</TaxID>
    <TaxIDAdditionalInfo>
      <Info Name="AfiliacionIVA" Value="${esc(afiliacionIvaNuc(emisor))}"/>
    </TaxIDAdditionalInfo>
    <Name>${esc(emisor.nombreEmisor || "")}</Name>
${buildContacto(emisor.correo)}    <AdditionlInfo>
${buildFrases(emisor)}
    </AdditionlInfo>
    <BranchInfo>
      <Code>${esc(String(emisor.codigoEstablecimiento || "1"))}</Code>
      <Name>${esc(emisor.nombreComercial || emisor.nombreEmisor || "")}</Name>
      <AddressInfo>
        <Address>${esc(emisor.direccion || "CIUDAD")}</Address>
        <City>${esc(emisor.codigoPostal || "01001")}</City>
        <District>${esc(emisor.municipio || "Guatemala")}</District>
        <State>${esc(emisor.departamento || "Guatemala")}</State>
        <Country>GT</Country>
      </AddressInfo>
    </BranchInfo>
  </Seller>
  <Buyer>
    <TaxID>${esc(idReceptorParaXml(receptor?.id))}</TaxID>
    <Name>${esc(receptor?.nombre || "CONSUMIDOR FINAL")}</Name>
    <AddressInfo>
      <Address>CIUDAD</Address>
      <City>01001</City>
      <District>Guatemala</District>
      <State>Guatemala</State>
      <Country>GT</Country>
    </AddressInfo>
  </Buyer>
  <Items>
${items.join("\n")}
  </Items>
  <Totals>
${conIva ? `    <TotalTaxes>
      <TotalTax>
        <Description>IVA</Description>
        <Amount>${totalIVA.toFixed(6)}</Amount>
      </TotalTax>
    </TotalTaxes>
` : ""}    <GrandTotal>
      <InvoiceTotal>${granTotal.toFixed(6)}</InvoiceTotal>
    </GrandTotal>
  </Totals>
  <AdditionalDocumentInfo>
    <AdditionalInfo>
      <Code>${esc(referenciaInterna)}</Code>
      <Type>ADENDA</Type>
      <AditionalData>
        <Data Name="INFORMACION_ADICIONAL">
          <Info Name="OBSERVACIONES" Value="${esc(observaciones || "-")}"/>
          <Info Name="CANTIDAD_LETRAS" Value="${esc(numeroALetras(granTotal))}"/>
        </Data>
      </AditionalData>
      <AditionalInfo>
        <Info Name="VALIDAR_REFERENCIA_INTERNA" Value="VALIDAR"/>
      </AditionalInfo>
    </AdditionalInfo>
  </AdditionalDocumentInfo>
</Root>`;

  return { xml, docType, granTotal, totalIVA, frases: frasesLegibles(emisor) };
}

/**
 * Items NUC desde líneas de venta. Se omiten las que quedan en cero (un regalo, una línea
 * descontada al 100%): la SAT no acepta items sin monto.
 */
function buildItems(lines, conIva = true) {
  const items = [];
  let granTotal = 0;
  let totalIVA = 0;
  let n = 1;
  for (const line of lines || []) {
    const qty = Number(line.qty) || 0;
    const unitPrice = r2(Number(line.unitPrice) || 0);
    if (qty <= 0 || unitPrice <= 0) continue;
    const bruto = r2(qty * unitPrice);
    const descuento = Math.min(r2(Number(line.discount) || 0), bruto);
    const total = r2(bruto - descuento);
    if (total <= 0) continue;
    const gravable = r2(total / 1.12);
    const iva = r2(total - gravable);
    granTotal += total;
    if (conIva) totalIVA += iva;

    const descuentoXml = descuento > 0 ? `
      <Discounts>
        <Discount>
          <Amount>${descuento.toFixed(6)}</Amount>
        </Discount>
      </Discounts>` : "";
    const impuestosXml = conIva ? `
      <Taxes>
        <Tax>
          <Code>1</Code>
          <Description>IVA</Description>
          <TaxableAmount>${gravable.toFixed(6)}</TaxableAmount>
          <Amount>${iva.toFixed(6)}</Amount>
        </Tax>
      </Taxes>` : "";

    items.push(`    <Item Number="${n++}">
      <Type>${line.type === "Servicio" ? "Servicio" : "Bien"}</Type>
      <Description>${esc(String(line.description || "Producto").substring(0, 500))}</Description>
      <Qty>${qty.toFixed(6)}</Qty>
      <UnitOfMeasure>UNI</UnitOfMeasure>
      <Price>${unitPrice.toFixed(6)}</Price>${descuentoXml}${impuestosXml}
      <Totals>
        <TotalItem>${total.toFixed(6)}</TotalItem>
      </Totals>
    </Item>`);
  }
  return { items, granTotal: r2(granTotal), totalIVA: r2(totalIVA) };
}

function itemConsolidado(descripcion, granTotal, conIva) {
  const gravable = conIva ? r2(granTotal / 1.12) : granTotal;
  const iva = conIva ? r2(granTotal - gravable) : 0;
  const impuestosXml = conIva ? `
      <Taxes>
        <Tax>
          <Code>1</Code>
          <Description>IVA</Description>
          <TaxableAmount>${gravable.toFixed(6)}</TaxableAmount>
          <Amount>${iva.toFixed(6)}</Amount>
        </Tax>
      </Taxes>` : "";
  return {
    iva,
    xml: `    <Item Number="1">
      <Type>Bien</Type>
      <Description>${esc(String(descripcion).substring(0, 500))}</Description>
      <Qty>1.000000</Qty>
      <UnitOfMeasure>UNI</UnitOfMeasure>
      <Price>${granTotal.toFixed(6)}</Price>${impuestosXml}
      <Totals>
        <TotalItem>${granTotal.toFixed(6)}</TotalItem>
      </Totals>
    </Item>`,
  };
}

/**
 * Reparte un descuento de toda la venta entre sus líneas, en proporción a su importe, al
 * centavo (mayor residuo). Para ventas cuyo descuento no quedó guardado por línea: la SAT
 * pide el descuento en cada item, no uno global.
 */
function prorateDiscount(lines, totalDiscount) {
  const cents = Math.round((Number(totalDiscount) || 0) * 100);
  const gross = lines.map((l) => Math.round((Number(l.qty) || 0) * (Number(l.unitPrice) || 0) * 100));
  const sum = gross.reduce((a, b) => a + b, 0);
  if (cents <= 0 || sum <= 0) return lines.map((l) => ({ ...l }));
  const target = Math.min(cents, sum);
  const exact = gross.map((g) => (g * target) / sum);
  const floors = exact.map(Math.floor);
  let remaining = target - floors.reduce((a, b) => a + b, 0);
  const order = exact.map((e, i) => [e - floors[i], i]).sort((a, b) => b[0] - a[0]);
  for (const [, i] of order) {
    if (remaining <= 0) break;
    if (floors[i] < gross[i]) { floors[i] += 1; remaining -= 1; }
  }
  return lines.map((l, i) => ({ ...l, discount: r2((Number(l.discount) || 0) + floors[i] / 100) }));
}

// ── Emisor / receptor ───────────────────────────────────────────────────────

function buildContacto(correo) {
  const limpio = String(correo || "").trim();
  // Si se emite <Email> tiene que cumplir el patrón del esquema; vacío da code 3010.
  if (!/^[a-zA-ZñÑ0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(limpio)) return "";
  return `    <Contact>
      <EmailList>
        <Email>${esc(limpio)}</Email>
      </EmailList>
    </Contact>
`;
}

/**
 * Frases por régimen. PEQ: una sola, 3/1 (la SAT rechaza dos). GEN: TipoFrase 1, escenario
 * 2 si es Opcional Simplificado (retención definitiva), 1 si es General/Utilidades.
 */
function buildFrases(emisor) {
  if (esPequenoContribuyente(emisor)) {
    return `      <Info Name="TipoFrase" Data="1" Value="3"/>
      <Info Name="Escenario" Data="1" Value="1"/>`;
  }
  return `      <Info Name="TipoFrase" Data="1" Value="1"/>
      <Info Name="Escenario" Data="1" Value="${escenarioIsr(emisor)}"/>`;
}

function escenarioIsr(emisor) {
  const regimen = String(emisor?.regimenISR || "OPCIONAL").toUpperCase();
  return regimen.startsWith("GEN") || regimen.includes("UTILIDAD") ? "1" : "2";
}

/** Las mismas frases en texto, para imprimirlas en el ticket. */
function frasesLegibles(emisor) {
  if (esPequenoContribuyente(emisor)) return ["No genera derecho a crédito fiscal"];
  return [escenarioIsr(emisor) === "1"
    ? "Sujeto a pagos trimestrales"
    : "Sujeto a retención definitiva del Impuesto Sobre la Renta"];
}

function esPequenoContribuyente(emisor) {
  const v = String(emisor?.afiliacionIVA || "").toUpperCase();
  return v === "PEQ" || v === "PEC";
}

function afiliacionIvaNuc(emisor) {
  return esPequenoContribuyente(emisor) ? "PEQ" : String(emisor?.afiliacionIVA || "GEN").toUpperCase();
}

/** NIT dentro del XML: sin guion ni espacios, CON la K, sin relleno de ceros. */
function nitParaXml(s) {
  return String(s || "").replace(/[^0-9Kk]/g, "").toUpperCase();
}

/** Receptor: NIT, CUI de 13 dígitos, pasaporte o 'CF'. Solo se quitan guiones y espacios. */
function idReceptorParaXml(valor) {
  return String(valor || "").replace(/[\s-]/g, "").toUpperCase() || "CF";
}

/**
 * Referencia interna estable ante reintentos y única por documento: prefijo de BakeOS +
 * empresa + huella de las ventas que cubre. El prefijo evita chocar con ComandaYa, que
 * factura con el mismo NIT y también valida referencias.
 */
function referenciaInterna(companyId, saleIds, version = 0) {
  // La versión cambia la huella al volver a facturar un cobro cuya factura se anuló: Digifact
  // valida referencias y rechazaría la segunda con la misma. La versión 0 no suma nada, así
  // las facturas ya emitidas conservan su referencia.
  const huella = crypto.createHash("sha1")
    .update([...saleIds].map(String).sort().join("|") + (version > 0 ? `|v${version}` : ""))
    .digest("hex").slice(0, 12).toUpperCase();
  return `BKOS-${String(companyId).slice(0, 12)}-${huella}`.slice(0, 60);
}

function esc(str) {
  if (str === null || str === undefined) return "";
  return String(str)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&apos;");
}

/** Guatemala es UTC-6 exacto, sin horario de verano. */
function nowGuatemalaISO(date = new Date()) {
  const gt = new Date(date.getTime() - 6 * 3600 * 1000);
  const p = (n) => String(n).padStart(2, "0");
  return `${gt.getUTCFullYear()}-${p(gt.getUTCMonth() + 1)}-${p(gt.getUTCDate())}` +
    `T${p(gt.getUTCHours())}:${p(gt.getUTCMinutes())}:${p(gt.getUTCSeconds())}-06:00`;
}

/** Fecha de emisión para CancelFelGT: 'YYYY-MM-DDTHH:mm:ss' en hora de Guatemala, sin zona. */
function fechaEmisionParaAnular(millis) {
  return nowGuatemalaISO(new Date(millis || Date.now())).slice(0, 19);
}

/** 197.5 → "CIENTO NOVENTA Y SIETE QUETZALES CON 50/100" */
function numeroALetras(monto) {
  const entero = Math.floor(monto);
  const centavos = Math.round((monto - entero) * 100);
  const UNI = ["", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
    "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE", "VEINTE"];
  const DEC = ["", "", "VEINTI", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"];
  const CEN = ["", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
    "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"];
  const menor1000 = (x) => {
    if (x === 0) return "";
    if (x === 100) return "CIEN";
    let out = "";
    const c = Math.floor(x / 100), resto = x % 100;
    if (c) out += CEN[c] + " ";
    if (resto <= 20) out += UNI[resto];
    else {
      const d = Math.floor(resto / 10), u = resto % 10;
      out += d === 2 ? (u ? "VEINTI" + UNI[u] : "VEINTE") : DEC[d] + (u ? " Y " + UNI[u] : "");
    }
    return out.trim();
  };
  const aLetras = (x) => {
    if (x === 0) return "CERO";
    let out = "";
    const millones = Math.floor(x / 1000000), miles = Math.floor((x % 1000000) / 1000), resto = x % 1000;
    if (millones) out += (millones === 1 ? "UN MILLON" : menor1000(millones) + " MILLONES") + " ";
    if (miles) out += (miles === 1 ? "MIL" : menor1000(miles) + " MIL") + " ";
    if (resto) out += menor1000(resto);
    return out.trim();
  };
  return `${aLetras(entero)} QUETZALES CON ${String(centavos).padStart(2, "0")}/100`;
}

module.exports = {
  buildFacturaNuc,
  buildItems,
  prorateDiscount,
  referenciaInterna,
  frasesLegibles,
  numeroALetras,
  nowGuatemalaISO,
  fechaEmisionParaAnular,
  idReceptorParaXml,
  nitParaXml,
};

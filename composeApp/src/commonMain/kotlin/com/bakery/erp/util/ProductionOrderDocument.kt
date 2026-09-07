package com.bakery.erp.util

import com.bakery.erp.domain.PrintMediaSize
import com.bakery.erp.domain.PrintableDocument
import com.bakery.erp.model.ProductionOrderPrintData

/**
 * Arma el documento imprimible con las dos representaciones a la vez: la impresora
 * la elige la plataforma (térmica en Android, hoja en web), no quien construye la orden.
 */
fun ProductionOrderPrintData.toPrintableDocument(): PrintableDocument = PrintableDocument(
    jobName = buildString {
        append("Orden de produccion")
        if (branchName.isNotBlank()) append(" - ").append(branchName)
        if (dateLabel.isNotBlank()) append(" ").append(dateLabel)
    },
    html = buildProductionOrderHtml(this),
    mediaSize = PrintMediaSize.PAGE,
    escPosByWidthMm = mapOf(
        58 to buildProductionOrderEscPos(this, 58),
        80 to buildProductionOrderEscPos(this, 80)
    )
)

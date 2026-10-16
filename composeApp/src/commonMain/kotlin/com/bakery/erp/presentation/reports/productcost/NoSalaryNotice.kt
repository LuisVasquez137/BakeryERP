package com.bakery.erp.presentation.reports.productcost

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.theme.BrandColors

/**
 * Los salarios viven en la parte privada de la ficha (`employees/{id}/private/pay`) y solo los
 * lee quien tiene "Ver nómina y salario". Sin ese permiso el reporte sale sin mano de obra: se
 * dice aquí para que nadie tome ese costo por completo.
 */
@Composable
internal fun NoSalaryNotice(modifier: Modifier = Modifier) {
    Surface(
        color = BrandColors.AccentSoft,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = BrandColors.AccentIcon)
            Spacer(Modifier.width(10.dp))
            Text(
                "Sin salarios: este cálculo no incluye la mano de obra porque tu usuario no tiene " +
                    "el permiso \"Ver nómina y salario\".",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.Primary
            )
        }
    }
}

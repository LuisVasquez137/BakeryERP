package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
actual fun DocumentPickerButton(
    onDocumentSelected: (bytes: ByteArray, filename: String) -> Unit,
    modifier: Modifier,
    content: @Composable (() -> Unit) -> Unit
) {
    content {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = ".pdf,.jpg,.jpeg,.png,.gif,.webp"
        input.onchange = { _ ->
            val file = input.files?.get(0)
            if (file != null) {
                val reader = FileReader()
                reader.onload = { event ->
                    val result = event.target.asDynamic().result
                    val uint8 = js("new Uint8Array(result)")
                    val len = uint8.length as Int
                    val bytes = ByteArray(len) { i -> (uint8[i] as Int).toByte() }
                    onDocumentSelected(bytes, file.name)
                    Unit
                }
                reader.readAsArrayBuffer(file)
            }
            Unit
        }
        input.click()
    }
}

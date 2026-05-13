package com.bakery.erp.di

actual fun initKoin() {
    // Firebase no está soportado en el target wasmJs.
    // Koin arranca sin módulos en este target.
    // Cuando GitLive publique soporte WASM, conectar appModule aquí.
}

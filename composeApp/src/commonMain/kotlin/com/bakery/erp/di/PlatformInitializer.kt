package com.bakery.erp.di

// Se llama antes de initKoin() en cada plataforma.
// Permite inicializar Firebase con config específica de plataforma (ej: web).
expect fun initializePlatform()

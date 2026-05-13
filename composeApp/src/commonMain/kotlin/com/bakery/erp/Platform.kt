package com.bakery.erp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
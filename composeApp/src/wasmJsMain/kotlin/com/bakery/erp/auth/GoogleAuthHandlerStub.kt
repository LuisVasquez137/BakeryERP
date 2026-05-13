package com.bakery.erp.auth

import com.bakery.erp.domain.GoogleAuthHandler

class GoogleAuthHandlerStub : GoogleAuthHandler {
    override suspend fun getIdToken(): String? = null
}

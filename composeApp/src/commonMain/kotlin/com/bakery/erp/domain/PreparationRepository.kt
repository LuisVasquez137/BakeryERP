package com.bakery.erp.domain

import com.bakery.erp.model.Preparation

interface PreparationRepository {
    suspend fun getPreparations(companyId: String): List<Preparation>
    suspend fun getPreparation(companyId: String, preparationId: String): Preparation
    suspend fun addPreparation(companyId: String, preparation: Preparation)
    suspend fun updatePreparation(companyId: String, preparation: Preparation)
    suspend fun deletePreparation(companyId: String, preparationId: String)
}

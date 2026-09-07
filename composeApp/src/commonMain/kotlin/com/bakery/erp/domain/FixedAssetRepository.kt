package com.bakery.erp.domain

import com.bakery.erp.model.FixedAsset

interface FixedAssetRepository {
    suspend fun getAssets(companyId: String): List<FixedAsset>
    suspend fun getAsset(companyId: String, assetId: String): FixedAsset?
    suspend fun saveAsset(companyId: String, asset: FixedAsset): FixedAsset
    suspend fun deleteAsset(companyId: String, assetId: String)
}

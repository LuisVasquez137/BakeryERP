package com.bakery.erp.domain

import com.bakery.erp.model.Branch

interface BranchRepository {
    suspend fun getBranches(companyId: String): List<Branch>
    suspend fun getBranch(companyId: String, branchId: String): Branch?
    suspend fun addBranch(companyId: String, branch: Branch)
    suspend fun updateBranch(companyId: String, branch: Branch)
    suspend fun deleteBranch(companyId: String, branchId: String)
}

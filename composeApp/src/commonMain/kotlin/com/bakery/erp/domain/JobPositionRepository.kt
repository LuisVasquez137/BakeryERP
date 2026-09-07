package com.bakery.erp.domain

import com.bakery.erp.model.JobPosition

interface JobPositionRepository {
    suspend fun getJobPositions(companyId: String): List<JobPosition>
    suspend fun getJobPosition(companyId: String, positionId: String): JobPosition?
    suspend fun saveJobPosition(companyId: String, position: JobPosition)
    suspend fun deleteJobPosition(companyId: String, positionId: String)
}

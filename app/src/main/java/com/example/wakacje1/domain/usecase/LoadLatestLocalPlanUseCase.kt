package com.example.wakacje1.domain.usecase

import android.content.Context
import com.example.wakacje1.data.local.PlanStorage
import com.example.wakacje1.data.local.StoredPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoadLatestLocalPlanUseCase {
    suspend fun execute(ctx: Context, uid: String): StoredPlan? {
        return withContext(Dispatchers.IO) {
            PlanStorage.loadLatestPlan(ctx, uid)
        }
    }
}

package com.example.wakacje1.presentation.vacation

import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.usecase.ExportPlanPdfUseCase

/**
 * Eksport planu do HTML (druk PDF realizuje UI przez UiEvent.PrintPdf).
 */
class VacationExportManager(
    private val exportPlanPdfUseCase: ExportPlanPdfUseCase
) {
    suspend fun buildHtml(
        destinationName: String?,
        tripStartDateMillis: Long?,
        plan: List<DayPlan>
    ): String {
        return exportPlanPdfUseCase.execute(destinationName, tripStartDateMillis, plan)
    }
}

package com.example.wakacje1.domain.usecase

import android.content.Context
import com.example.wakacje1.data.export.PdfExporter
import com.example.wakacje1.domain.model.DayPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportPlanPdfUseCase {

    suspend fun execute(
        ctx: Context,
        destinationName: String?,
        tripStartDateMillis: Long?,
        plan: List<DayPlan>
    ): String {
        if (plan.isEmpty()) return "Brak planu do eksportu."

        return withContext(Dispatchers.IO) {
            try {
                val fileName = "plan_${System.currentTimeMillis()}.pdf"
                PdfExporter.exportPlanToDownloads(
                    context = ctx,
                    fileName = fileName,
                    destinationName = destinationName,
                    tripStartDateMillis = tripStartDateMillis,
                    plan = plan
                )
                "PDF zapisany w Pobrane: $fileName"
            } catch (e: Exception) {
                "Błąd PDF: ${e.message ?: "?"}"
            }
        }
    }
}

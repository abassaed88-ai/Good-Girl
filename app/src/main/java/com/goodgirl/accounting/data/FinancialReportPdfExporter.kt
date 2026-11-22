package com.goodgirl.accounting.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.goodgirl.accounting.domain.FinancialReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exports the currently calculated report using the Android document picker destination. */
object FinancialReportPdfExporter {
    suspend fun write(
        context: Context,
        destination: Uri,
        report: FinancialReport,
        periodLabel: String
    ) = withContext(Dispatchers.IO) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 30
        val footerSpace = 28
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 12f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val content = buildContent(report, periodLabel, System.currentTimeMillis())
        val layout = StaticLayout.Builder
            .obtain(content, 0, content.length, textPaint, pageWidth - margin * 2)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(2f, 1f)
            .setIncludePad(true)
            .build()

        val firstLineHeight = if (layout.lineCount > 0) {
            (layout.getLineBottom(0) - layout.getLineTop(0)).coerceAtLeast(1)
        } else {
            18
        }
        val availableHeight = pageHeight - margin * 2 - footerSpace
        val contentHeight = (availableHeight / firstLineHeight).coerceAtLeast(1) * firstLineHeight
        val pageCount = ceil(layout.height.toDouble() / contentHeight.toDouble()).toInt().coerceAtLeast(1)
        val document = PdfDocument()
        try {
            for (pageNumber in 0 until pageCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.save()
                canvas.translate(margin.toFloat(), (margin - pageNumber * contentHeight).toFloat())
                layout.draw(canvas)
                canvas.restore()
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF64748B.toInt()
                    textSize = 10f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    "Good Girl  •  ${pageNumber + 1} / $pageCount",
                    pageWidth / 2f,
                    (pageHeight - 14).toFloat(),
                    footerPaint
                )
                document.finishPage(page)
            }
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IllegalStateException("تعذر فتح ملف PDF للكتابة")
            output.use { stream ->
                document.writeTo(stream)
                stream.flush()
            }
        } finally {
            document.close()
        }
    }

    internal fun buildContent(
        report: FinancialReport,
        periodLabel: String,
        generatedAt: Long
    ): String {
        val date = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(generatedAt))
        val builder = StringBuilder()
        builder.append("Good Girl\n")
        builder.append("تقرير الأرباح والحركة المالية\n")
        builder.append("الفترة: ").append(periodLabel).append("\n")
        builder.append("تاريخ إنشاء التقرير: ").append(date).append("\n")
        builder.append("----------------------------------------\n")
        builder.append("الملخص المالي حسب العملة\n")
        if (report.currencySummaries.isEmpty()) {
            builder.append("لا توجد بيانات مالية ضمن الفترة المحددة.\n")
        } else {
            report.currencySummaries.forEach { summary ->
                builder.append("العملة: ").append(summary.currencyCode).append("\n")
                builder.append("مبيعات فواتير البيع: ").append(summary.sales.toPlainString()).append("\n")
                builder.append("مشتريات فواتير الشراء: ").append(summary.purchases.toPlainString()).append("\n")
                builder.append("تكلفة البضاعة المباعة: ").append(summary.costOfGoodsSold.toPlainString()).append("\n")
                builder.append("مجمل الربح: ").append(summary.grossProfit.toPlainString()).append("\n")
                builder.append("إيرادات أخرى: ").append(summary.otherIncome.toPlainString()).append("\n")
                builder.append("المصروفات: ").append(summary.expenses.toPlainString()).append("\n")
                builder.append("صافي الربح: ").append(summary.netProfit.toPlainString()).append("\n")
                builder.append("----------------------------------------\n")
            }
        }
        builder.append("تحليل المبيعات حسب المادة\n")
        if (report.products.isEmpty()) {
            builder.append("لا توجد مبيعات مواد ضمن الفترة المحددة.\n")
        } else {
            report.products.forEachIndexed { index, product ->
                builder.append(index + 1).append(") ").append(product.productName)
                    .append(" (").append(product.currencyCode).append(")\n")
                builder.append("الكمية بوحدة الأساس: ").append(product.quantity.stripTrailingZeros().toPlainString()).append("\n")
                builder.append("صافي المبيعات: ").append(product.revenue.toPlainString()).append("\n")
                builder.append("تكلفة البضاعة: ").append(product.cost.toPlainString()).append("\n")
                builder.append("الربح: ").append(product.profit.toPlainString()).append("\n")
            }
        }
        builder.append("\nملاحظة: تعرض العملات بشكل مستقل، ولا يجري تحويلها أو جمعها تلقائياً.")
        return builder.toString()
    }
}

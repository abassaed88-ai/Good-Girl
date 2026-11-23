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
import com.goodgirl.accounting.domain.CashFlowStatement
import com.goodgirl.accounting.domain.CashFlowStatementCalculator
import com.goodgirl.accounting.domain.GeneralLedgerFinancialStatements
import com.goodgirl.accounting.domain.TrialBalance
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exports the general-ledger statements as an Arabic, multi-page PDF. */
object GeneralLedgerStatementsPdfExporter {
    suspend fun write(
        context: Context,
        destination: Uri,
        trialBalance: TrialBalance,
        financialStatements: GeneralLedgerFinancialStatements,
        cashFlowStatement: CashFlowStatement?,
        generatedAt: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 30
        val footerSpace = 28
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val content = buildContent(trialBalance, financialStatements, cashFlowStatement, generatedAt)
        val layout = StaticLayout.Builder
            .obtain(content, 0, content.length, paint, pageWidth - margin * 2)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(2f, 1f)
            .setIncludePad(true)
            .build()
        val lineHeight = if (layout.lineCount > 0) {
            (layout.getLineBottom(0) - layout.getLineTop(0)).coerceAtLeast(1)
        } else 18
        val availableHeight = pageHeight - margin * 2 - footerSpace
        val contentHeight = (availableHeight / lineHeight).coerceAtLeast(1) * lineHeight
        val pageCount = ceil(layout.height.toDouble() / contentHeight.toDouble()).toInt().coerceAtLeast(1)
        val document = PdfDocument()
        try {
            for (pageNumber in 0 until pageCount) {
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber + 1).create()
                val page = document.startPage(info)
                page.canvas.drawColor(android.graphics.Color.WHITE)
                page.canvas.save()
                page.canvas.translate(margin.toFloat(), (margin - pageNumber * contentHeight).toFloat())
                layout.draw(page.canvas)
                page.canvas.restore()
                val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF64748B.toInt()
                    textSize = 10f
                    textAlign = Paint.Align.CENTER
                }
                page.canvas.drawText(
                    "Good Girl  •  ${pageNumber + 1} / $pageCount",
                    pageWidth / 2f,
                    (pageHeight - 14).toFloat(),
                    footer
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
        trialBalance: TrialBalance,
        financialStatements: GeneralLedgerFinancialStatements,
        cashFlowStatement: CashFlowStatement?,
        generatedAt: Long
    ): String {
        val builder = StringBuilder()
        builder.append("Good Girl\n")
        builder.append("القوائم المالية وميزان المراجعة\n")
        builder.append("تاريخ إنشاء التقرير: ")
            .append(SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(generatedAt))).append("\n")
        builder.append("الفترة: ").append(date(financialStatements.periodStartInclusive))
            .append(" — ").append(date(financialStatements.periodEndInclusive)).append("\n")
        builder.append("تاريخ الميزانية: ").append(date(financialStatements.asOfInclusive)).append("\n")
        builder.append("كل عملة معروضة بشكل مستقل دون تحويل أو جمع تلقائي.\n")
        divider(builder)

        builder.append("ميزان المراجعة\n")
        if (trialBalance.rows.isEmpty()) builder.append("لا توجد قيود محاسبية مرحلة.\n")
        trialBalance.currencySummaries.forEach { summary ->
            builder.append("العملة: ").append(summary.currencyCode).append("\n")
            builder.append("إجمالي المدين: ").append(n(summary.totalDebits))
                .append(" | إجمالي الدائن: ").append(n(summary.totalCredits)).append("\n")
            builder.append("الأرصدة المدينة: ").append(n(summary.debitBalances))
                .append(" | الأرصدة الدائنة: ").append(n(summary.creditBalances)).append("\n")
            trialBalance.rows.filter { it.currencyCode == summary.currencyCode }.forEach { row ->
                row(builder, row.accountCode, row.accountName, row.debitBalance, row.creditBalance)
            }
            divider(builder)
        }

        builder.append("قائمة الدخل من دفتر الأستاذ العام\n")
        if (financialStatements.incomeStatements.isEmpty()) builder.append("لا توجد بيانات دخل ضمن الفترة.\n")
        financialStatements.incomeStatements.forEach { statement ->
            builder.append("العملة: ").append(statement.currencyCode).append("\nالإيرادات\n")
            statement.revenues.forEach { row(builder, it.accountCode, it.accountName, it.amount) }
            builder.append("إجمالي الإيرادات: ").append(n(statement.totalRevenue)).append("\nالمصروفات\n")
            statement.expenses.forEach { row(builder, it.accountCode, it.accountName, it.amount) }
            builder.append("إجمالي المصروفات: ").append(n(statement.totalExpenses)).append("\n")
            builder.append("صافي ربح / خسارة الفترة: ").append(n(statement.netIncome)).append("\n")
            divider(builder)
        }

        builder.append("الميزانية العمومية من دفتر الأستاذ العام\n")
        if (financialStatements.balanceSheets.isEmpty()) builder.append("لا توجد أرصدة لإنشاء الميزانية.\n")
        financialStatements.balanceSheets.forEach { sheet ->
            builder.append("العملة: ").append(sheet.currencyCode).append("\nالأصول\n")
            sheet.assets.forEach { row(builder, it.accountCode, it.accountName, it.amount) }
            builder.append("إجمالي الأصول: ").append(n(sheet.totalAssets)).append("\nالالتزامات\n")
            sheet.liabilities.forEach { row(builder, it.accountCode, it.accountName, it.amount) }
            builder.append("إجمالي الالتزامات: ").append(n(sheet.totalLiabilities)).append("\nحقوق الملكية\n")
            sheet.equity.forEach { row(builder, it.accountCode, it.accountName, it.amount) }
            builder.append("إجمالي حقوق الملكية: ").append(n(sheet.totalEquity)).append("\n")
            builder.append("الأرباح المحتجزة / نتيجة الفترات: ").append(n(sheet.retainedEarnings)).append("\n")
            builder.append("إجمالي الالتزامات وحقوق الملكية: ").append(n(sheet.totalLiabilitiesAndEquity)).append("\n")
            builder.append(
                if (sheet.totalAssets.compareTo(sheet.totalLiabilitiesAndEquity) == 0)
                    "حالة المعادلة: متوازنة\n"
                else "تحذير: الميزانية غير متوازنة\n"
            )
            divider(builder)
        }

        builder.append("قائمة التدفقات النقدية المباشرة\n")
        when {
            cashFlowStatement == null ->
                builder.append("تعذر إنشاء قائمة التدفقات النقدية بسبب خطأ في البيانات.\n")
            !cashFlowStatement.coverageAvailable ->
                builder.append("الفترة المطلوبة تسبق حد التغطية التاريخية للأرصدة الافتتاحية.\n")
            else -> {
                if (cashFlowStatement.historyTruncated) {
                    builder.append("تنبيه: يبدأ التقرير من حد التغطية التاريخية: ")
                        .append(date(cashFlowStatement.effectiveStartInclusive)).append("\n")
                }
                cashFlowStatement.statements.forEach { statement ->
                    builder.append("العملة: ").append(statement.currencyCode).append("\n")
                    builder.append("النقدية الافتتاحية: ").append(n(statement.openingCashBalance)).append("\n")
                    statement.activities.forEach { activity ->
                        builder.append(activityLabel(activity.section)).append("\n")
                        builder.append("المقبوضات: ").append(n(activity.cashInflows))
                            .append(" | المدفوعات: ").append(n(activity.cashOutflows)).append("\n")
                        builder.append("صافي النشاط: ").append(n(activity.netCashFlow)).append("\n")
                    }
                    builder.append("صافي التغير في النقدية: ").append(n(statement.netCashChange)).append("\n")
                    builder.append("النقدية الختامية: ").append(n(statement.closingCashBalance)).append("\n")
                    builder.append("رصيد الصندوق الفعلي: ").append(n(statement.actualClosingCashBalance)).append("\n")
                    builder.append("فرق المطابقة: ").append(n(statement.reconciliationDifference)).append("\n")
                    divider(builder)
                }
            }
        }
        builder.append("الأرصدة الافتتاحية المرحّلة لا تعيد بناء أرباح أو تدفقات الفترات السابقة للترقية.")
        return builder.toString()
    }

    private fun row(builder: StringBuilder, code: String, name: String, amount: BigDecimal) {
        builder.append(code).append(" — ").append(name).append(": ").append(n(amount)).append("\n")
    }

    private fun row(
        builder: StringBuilder,
        code: String,
        name: String,
        debit: BigDecimal,
        credit: BigDecimal
    ) {
        builder.append(code).append(" — ").append(name)
            .append(" | مدين: ").append(n(debit))
            .append(" | دائن: ").append(n(credit)).append("\n")
    }

    private fun divider(builder: StringBuilder) {
        builder.append("----------------------------------------\n")
    }

    private fun n(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    private fun date(value: Long): String =
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(value))

    private fun activityLabel(section: String): String = when (section) {
        CashFlowStatementCalculator.OPERATING -> "الأنشطة التشغيلية"
        CashFlowStatementCalculator.INVESTING -> "الأنشطة الاستثمارية"
        CashFlowStatementCalculator.FINANCING -> "الأنشطة التمويلية"
        else -> "حركات نقدية غير مصنفة"
    }
}

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
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import com.goodgirl.accounting.domain.InvoiceDiscountAllocator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Creates an Arabic invoice PDF and writes it to a user-selected SAF destination.
 * No storage permission or network connection is required.
 */
object InvoicePdfExporter {
    suspend fun write(
        context: Context,
        destination: Uri,
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        partyName: String?
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
        val content = buildContent(invoice, lines, partyName)
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
        val linesPerPage = (availableHeight / firstLineHeight).coerceAtLeast(1)
        val contentHeight = linesPerPage * firstLineHeight
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
        invoice: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        partyName: String?
    ): String {
        val date = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(invoice.issuedAt))
        val isSale = invoice.kind == "SALE"
        val title = if (isSale) "فاتورة بيع" else "فاتورة شراء"
        val lineDiscountTotal = lines.fold(java.math.BigDecimal.ZERO) { total, line ->
            total.add(line.discount.toBigDecimal())
        }
        val extraInvoiceDiscount = invoice.discount.toBigDecimal()
            .subtract(lineDiscountTotal)
            .max(java.math.BigDecimal.ZERO)
        val lineNetAmounts = lines.map { it.lineTotal.toBigDecimal() }
        val allocatedInvoiceDiscounts = InvoiceDiscountAllocator.allocate(
            lineNetAmounts,
            extraInvoiceDiscount
        )
        val builder = StringBuilder()
        builder.append("Good Girl\n")
        builder.append(title).append("\n")
        builder.append("رقم الفاتورة: ").append(invoice.invoiceNumber).append("\n")
        builder.append("التاريخ: ").append(date).append("\n")
        if (!partyName.isNullOrBlank()) {
            builder.append(if (isSale) "العميل: " else "المورد: ").append(partyName).append("\n")
        }
        builder.append("العملة: ").append(invoice.currencyCode).append("\n")
        builder.append("سعر الصرف المحفوظ: ").append(invoice.exchangeRateSnapshot).append("\n")
        builder.append("----------------------------------------\n")
        builder.append("تفاصيل المواد\n")
        if (lines.isEmpty()) {
            builder.append("لا توجد تفاصيل مواد لهذه الفاتورة.\n")
        } else {
            lines.forEachIndexed { index, line ->
                val invoiceDiscountShare = allocatedInvoiceDiscounts[index]
                val finalLineNet = lineNetAmounts[index].subtract(invoiceDiscountShare)
                builder.append(index + 1).append(") ").append(line.productNameSnapshot).append("\n")
                builder.append("الكمية: ").append(line.quantity).append(" ").append(line.unitSnapshot)
                    .append("   سعر الوحدة: ").append(line.unitPriceSnapshot).append("\n")
                builder.append("خصم المادة: ").append(line.discount)
                    .append("   خصم الفاتورة الموزع: ").append(invoiceDiscountShare.stripTrailingZeros().toPlainString()).append("\n")
                builder.append("صافي المادة بعد الخصومات: ").append(finalLineNet.stripTrailingZeros().toPlainString()).append("\n")
            }
        }
        builder.append("----------------------------------------\n")
        builder.append("المجموع قبل الخصم: ").append(invoice.subtotal).append(" ").append(invoice.currencyCode).append("\n")
        builder.append("خصم المواد: ").append(lineDiscountTotal.stripTrailingZeros().toPlainString()).append("\n")
        builder.append("خصم الفاتورة: ").append(extraInvoiceDiscount.stripTrailingZeros().toPlainString()).append("\n")
        builder.append("إجمالي الخصم: ").append(invoice.discount).append("\n")
        builder.append("الإجمالي النهائي: ").append(invoice.total).append(" ").append(invoice.currencyCode).append("\n")
        builder.append("المدفوع: ").append(invoice.paid).append(" ").append(invoice.currencyCode).append("\n")
        builder.append("المتبقي: ").append(invoice.remaining).append(" ").append(invoice.currencyCode).append("\n")
        if (invoice.note.isNotBlank()) {
            builder.append("----------------------------------------\n")
            builder.append("ملاحظات: ").append(invoice.note).append("\n")
        }
        builder.append("\nشكراً لتعاملكم مع Good Girl")
        return builder.toString()
    }
}

package com.goodgirl.accounting.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import com.goodgirl.accounting.data.local.ProductBarcodeEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Produces printable A4 sheets of labels in three sizes. PDF rendering runs
 * off the UI thread and works without a network connection.
 */
object ProductBarcodeLabelPdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 18f

    suspend fun write(
        context: Context,
        destination: Uri,
        product: ProductEntity,
        barcodes: List<ProductBarcodeEntity>,
        copiesPerBarcode: Int = 1,
        includePrice: Boolean = true,
        columns: Int = 3,
        rows: Int = 8
    ): Int = withContext(Dispatchers.IO) {
        require(barcodes.isNotEmpty()) { "لا يوجد باركود مسجل لهذه المادة" }
        require(copiesPerBarcode in 1..200) { "عدد النسخ يجب أن يكون بين 1 و200" }
        require(columns in 2..4 && rows in 6..10) { "حجم الملصق غير مدعوم" }

        val labels = barcodes.flatMap { barcode -> List(copiesPerBarcode) { barcode } }
        val labelsPerPage = columns * rows
        val cellWidth = (PAGE_WIDTH - PAGE_MARGIN * 2) / columns
        val cellHeight = (PAGE_HEIGHT - PAGE_MARGIN * 2) / rows
        val document = PdfDocument()

        try {
            labels.chunked(labelsPerPage).forEachIndexed { pageIndex, pageLabels ->
                val pageInfo = PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH,
                    PAGE_HEIGHT,
                    pageIndex + 1
                ).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawColor(android.graphics.Color.WHITE)

                pageLabels.forEachIndexed { index, barcode ->
                    val column = index % columns
                    val row = index / columns
                    val left = PAGE_MARGIN + column * cellWidth
                    val top = PAGE_MARGIN + row * cellHeight
                    drawLabel(
                        canvas = page.canvas,
                        product = product,
                        barcode = barcode,
                        bounds = RectF(left, top, left + cellWidth, top + cellHeight),
                        includePrice = includePrice
                    )
                }
                document.finishPage(page)
            }

            val output = context.contentResolver.openOutputStream(destination, "w")
                ?: throw IllegalStateException("تعذر فتح ملف PDF للكتابة")
            output.use { document.writeTo(it) }
            labels.size
        } finally {
            document.close()
        }
    }

    private fun drawLabel(
        canvas: Canvas,
        product: ProductEntity,
        barcode: ProductBarcodeEntity,
        bounds: RectF,
        includePrice: Boolean
    ) {
        val height = bounds.height()
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        canvas.drawRoundRect(bounds, 5f, 5f, border)

        val contentLeft = bounds.left + 6f
        val contentWidth = bounds.width() - 12f
        drawRtlText(
            canvas = canvas,
            text = product.name,
            left = contentLeft,
            top = bounds.top + height * 0.035f,
            width = contentWidth,
            size = (height * 0.10f).coerceIn(7f, 10f),
            bold = true,
            maxLines = 2
        )
        if (product.sku.isNotBlank()) {
            drawLtrText(
                canvas,
                "SKU: " + product.sku,
                bounds.centerX(),
                bounds.top + height * 0.29f,
                8f
            )
        }
        drawBarcode(
            canvas = canvas,
            value = barcode.barcodeValue,
            format = barcode.format,
            left = contentLeft + 2f,
            top = bounds.top + height * 0.34f,
            width = contentWidth - 4f,
            height = height * 0.34f
        )
        drawLtrText(
            canvas,
            barcode.barcodeValue,
            bounds.centerX(),
            bounds.top + height * 0.79f,
            8f
        )
        if (includePrice) {
            drawRtlText(
                canvas = canvas,
                text = "السعر: ${product.salePrice} ${product.priceCurrencyCode}",
                left = contentLeft,
                top = bounds.top + height * 0.83f,
                width = contentWidth,
                size = (height * 0.085f).coerceIn(7f, 9f),
                bold = true,
                maxLines = 1
            )
        }
    }

    private fun drawBarcode(
        canvas: Canvas,
        value: String,
        format: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val zxingFormat = when (format) {
            "EAN_13" -> BarcodeFormat.EAN_13
            "EAN_8" -> BarcodeFormat.EAN_8
            "UPC_A" -> BarcodeFormat.UPC_A
            "CODE_128" -> BarcodeFormat.CODE_128
            else -> throw IllegalArgumentException("صيغة الباركود غير مدعومة للطباعة")
        }
        val matrix = MultiFormatWriter().encode(
            value,
            zxingFormat,
            420,
            100,
            mapOf(EncodeHintType.MARGIN to 2)
        )
        val scale = width / matrix.width.toFloat()
        val startX = left + (width - matrix.width * scale) / 2f
        val middleRow = matrix.height / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
        }
        for (x in 0 until matrix.width) {
            if (matrix.get(x, middleRow)) {
                val xLeft = startX + x * scale
                canvas.drawRect(xLeft, top, xLeft + scale + 0.15f, top + height, paint)
            }
        }
    }

    private fun drawLtrText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baseline: Float,
        size: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        canvas.drawText(text.take(45), centerX, baseline, paint)
    }

    private fun drawRtlText(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        width: Float,
        size: Float,
        bold: Boolean,
        maxLines: Int
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }
}

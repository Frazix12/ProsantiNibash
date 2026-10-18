package com.irfan.prosantinibash.core.export

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.text.TextPaint
import android.text.StaticLayout
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.irfan.prosantinibash.data.model.*
import com.irfan.prosantinibash.core.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.time.YearMonth

/** PDFs use issuance snapshots; CSV is quoted and neutralizes spreadsheet formulas. */
object Exports {
    fun csvCell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
    suspend fun report(context: Context, book: Book, format: String): File = withContext(Dispatchers.IO) {
        if (format == "csv") return@withContext csv(context,book)
        if (format.startsWith("ledger:")) return@withContext ledger(context,book,format.substringAfter(':'))
        val annual = format.startsWith("annual:")
        val period = format.substringAfter(':',YearMonth.now(BuildingDates.zone).toString())
        val months = if (annual) (1..12).map { YearMonth.of(period.toInt(), it) } else listOf(YearMonth.parse(period))
        val lines = mutableListOf(book.building.name,AppText.get(R.string.financial_report_value, if (annual) DisplayLocale.digits(period) else DisplayLocale.month(months.first())),"")
        months.forEach { month ->
            val s = BookMath.summary(book,month)
            lines += listOf(DisplayLocale.month(month),AppText.get(R.string.expected_rent_value, Money.format(s.expected)), AppText.get(R.string.rent_cash_value, Money.format(s.collected)),
                AppText.get(R.string.current_due_value, Money.format(s.currentDue)), AppText.get(R.string.old_due_value, Money.format(s.oldDue)),
                AppText.get(R.string.expenses_value_550, Money.format(s.expenses)), AppText.get(R.string.net_value, Money.format(s.net)), "")
        }
        if (annual) lines += AppText.get(R.string.annual_net_value, Money.format(BookMath.sum(months.map { BookMath.summary(book,it).net })))
        book.tenancies.filter { BookMath.due(book,it.id)>0 }.forEach { lines +=
            AppText.get(R.string.value_value_value_outstanding, book.flat(it)?.flatNumber, book.tenant(it)?.full_name, Money.format(BookMath.due(book,it.id))) }
        pdf(context,"report-$period",lines)
    }
    suspend fun receipt(context: Context, book: Book, paymentId: String): File = withContext(Dispatchers.IO) {
        val p = book.payments.first { it.id == paymentId }
        val snapshot = p.receipt_snapshot
        fun field(key: String) = snapshot[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val tenant = snapshot["tenant"]?.jsonObject?.get("full_name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val flat = snapshot["flat"]?.jsonObject?.get("flat_number")?.jsonPrimitive?.contentOrNull.orEmpty()
        val lines = mutableListOf(snapshot["building"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: book.building.name,
            AppText.get(R.string.payment_receipt), p.receipt_number, AppText.get(R.string.entry_value, appLabel(p.kind.replace('_', ' '))), AppText.get(R.string.tenant_value, tenant), AppText.get(R.string.flat_value_557, flat),
            AppText.get(R.string.amount_value, Money.format(field("amount_paisa").toLongOrNull() ?: p.amount_paisa)),
            AppText.get(R.string.date_value, DisplayLocale.date(field("payment_date"))), AppText.get(R.string.method_value, appLabel(field("payment_method"))), AppText.get(R.string.reference_value, field("reference")))
        snapshot["allocations"]?.jsonArray?.forEach { allocation ->
            lines += "${DisplayLocale.date(allocation.jsonObject["period_date"]?.jsonPrimitive?.content.orEmpty())}: ${Money.format(allocation.jsonObject["amount_paisa"]!!.jsonPrimitive.long)}"
        }
        lines += listOf(AppText.get(R.string.remaining_value, Money.format(field("remaining_due_paisa").toLongOrNull() ?: 0)),
            AppText.get(R.string.credit_value, Money.format(field("credit_paisa").toLongOrNull() ?: 0)), AppText.get(R.string.original_issuance_snapshot))
        if (p.voided || p.revision>1) lines += AppText.get(R.string.current_record_changed_after_issuance_see_its_audit_history)
        pdf(context,p.receipt_number,lines)
    }
    private fun ledger(context: Context, book: Book, tenancyId: String): File {
        val tenancy=book.tenancies.first { it.id==tenancyId }
        val lines=mutableListOf(book.building.name,AppText.get(R.string.rent_ledger), "${book.flat(tenancy)?.flatNumber} — ${book.tenant(tenancy)?.full_name}",
            AppText.get(R.string.due_value, Money.format(BookMath.due(book,tenancyId))),AppText.get(R.string.credit_value, Money.format(BookMath.credit(book,tenancyId))), "")
        val charges=book.rent_periods.filter { it.tenancy_id==tenancyId }.map {
            it.period_date to AppText.get(R.string.ledger_charge_line, DisplayLocale.date(it.period_date), appLabel(it.kind), Money.format(it.amount_paisa), Money.format(BookMath.paid(book,it.id))) }
        val payments=book.payments.filter { it.tenancy_id==tenancyId }.map {
            it.payment_date to "${DisplayLocale.date(it.payment_date)} ${it.receipt_number} ${appLabel(it.kind)}: ${Money.format(it.amount_paisa)}${if(it.voided) AppText.get(R.string.voided_suffix) else ""}" }
        lines+=(charges+payments).sortedBy { it.first }.map { it.second }
        return pdf(context,"ledger-$tenancyId",lines)
    }
    private fun csv(context: Context, book: Book): File {
        val file = destination(context, "rent-book.csv")
        val tenancies = book.tenancies.associateBy { it.id }
        file.bufferedWriter().use { writer ->
            fun row(values: List<String>) { writer.write(values.joinToString(",", transform = ::csvCell)); writer.write("\r\n") }
            row(listOf("type","id","date","flat","tenant","amount_paisa","details","voided"))
            book.payments.forEach { p -> val t=tenancies.getValue(p.tenancy_id)
                row(listOf("payment",p.id,p.payment_date,book.flat(t)?.flatNumber.orEmpty(),book.tenant(t)?.full_name.orEmpty(),p.amount_paisa.toString(),"${p.kind}: ${p.receipt_number}",p.voided.toString())) }
            book.rent_periods.forEach { r -> val t=tenancies.getValue(r.tenancy_id)
                row(listOf("charge",r.id,r.period_date,book.flat(t)?.flatNumber.orEmpty(),book.tenant(t)?.full_name.orEmpty(),r.amount_paisa.toString(),r.kind,"false")) }
            book.expenses.forEach { row(listOf("expense",it.id,it.expense_date,it.flat_id.orEmpty(),"",it.amount_paisa.toString(),it.description,it.voided.toString())) }
            book.deposit_transactions.forEach { row(listOf("deposit",it.id,it.transaction_date,it.tenancy_id,"",it.amount_paisa.toString(),it.type,"false")) }
        }
        return file
    }
    private fun pdf(context: Context, name: String, lines: List<String>): File {
        val document = PdfDocument()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; textLocale = DisplayLocale.locale }
        // Native text layout shapes Bengali clusters and wraps at text boundaries.
        val text = lines.joinToString("\n")
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 515)
            .setIncludePad(false).setLineSpacing(4f, 1f).build()
        try {
            var first = 0
            var pageNumber = 1
            while (first < layout.lineCount) {
                val top = layout.getLineTop(first)
                var end = first + 1
                while (end < layout.lineCount && layout.getLineBottom(end) - top <= 762) end++
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber++).create())
                page.canvas.save()
                page.canvas.clipRect(40, 40, 555, 40 + layout.getLineTop(end) - top)
                page.canvas.translate(40f, 40f - top)
                layout.draw(page.canvas)
                page.canvas.restore()
                document.finishPage(page)
                first = end
            }
            return destination(context, "$name.pdf").also { file -> file.outputStream().use(document::writeTo) }
        } finally { document.close() }
    }
    fun destination(context: Context, name: String): File {
        val directory = File(context.cacheDir,"exports").apply { mkdirs() }
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"),"_")
        val files = directory.listFiles().orEmpty().filter { it.name != safeName }.sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        files.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 7 * 86_400_000L || total > 100 * 1024 * 1024) {
                val size=file.length(); if (file.delete()) total-=size
            }
        }
        return directory.resolve(safeName)
    }
    fun open(context: Context, file: File, mime: String, share: Boolean = false) {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        val intent=Intent(if(share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            if(share) { type=mime;putExtra(Intent.EXTRA_STREAM,uri) } else setDataAndType(uri,mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent,if(share) AppText.get(R.string.share) else AppText.get(R.string.open)))
    }
    private suspend fun writePdf(file:File,destination:android.os.ParcelFileDescriptor,
        signal:android.os.CancellationSignal) = withContext(Dispatchers.IO) {
        java.io.FileOutputStream(destination.fileDescriptor).use { out -> file.inputStream().use { input ->
            val buffer=ByteArray(8192)
            while(true) {
                if(signal.isCanceled) throw CancellationException("Printing cancelled")
                val count=input.read(buffer);if(count<0) break;out.write(buffer,0,count)
            }
        } }
    }

    fun print(context: Context, file: File) {
        val manager=context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
        manager.print(file.name,object:android.print.PrintDocumentAdapter() {
            override fun onLayout(old:android.print.PrintAttributes?,new:android.print.PrintAttributes?,signal:android.os.CancellationSignal,
                callback:LayoutResultCallback,extras:android.os.Bundle?) {
                if(signal.isCanceled) callback.onLayoutCancelled() else callback.onLayoutFinished(
                    android.print.PrintDocumentInfo.Builder(file.name).setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),true)
            }
            override fun onWrite(pages:Array<android.print.PageRange>,destination:android.os.ParcelFileDescriptor,
                signal:android.os.CancellationSignal,callback:WriteResultCallback) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                scope.launch {
                    try {
                        writePdf(file,destination,signal)
                        if (signal.isCanceled) callback.onWriteCancelled()
                        else callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (_: CancellationException) { callback.onWriteCancelled() }
                    catch (_: Exception) { callback.onWriteFailed(AppText.get(R.string.pdf_could_not_be_printed)) }
                    finally { scope.cancel() }
                }
            }
        },null)
    }

}

package com.irfan.prosantinibash.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.irfan.prosantinibash.core.export.Exports
import com.irfan.prosantinibash.data.model.TenantDocument
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.*
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** New objects precede metadata updates; replacements never destroy the previous document on failure. */
class FilesRepository(private val client: SupabaseClient, private val context: Context, private val book: BookRepository) {
    private val bucket get() = client.storage.from("pn-files")
    suspend fun prepare(uri: Uri, type: String): PickedFile = withContext(Dispatchers.IO) { read(uri, type) }
    suspend fun upload(file: PickedFile, entity: String, id: String, type: String, request: String,
        revision: Long, existing: TenantDocument?, oldPath: String?, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val digest=digest(file.file)
        val path="$entity/$id/$request-$digest.${file.extension}"
        if (!bucket.exists(path)) bucket.uploadAsFlow(path,file.file) { contentType=ContentType.parse(file.mime) }.collect { status ->
            if(status is UploadStatus.Progress && status.contentLength>0) progress(status.totalBytesSend.toFloat()/status.contentLength)
        } else {
            val check = File.createTempFile("upload-check-", ".tmp", context.cacheDir)
            try { bucket.downloadAuthenticatedTo(path, check); require(digest(check) == digest) }
            finally { check.delete() }
        }
        val data=buildJsonObject {
            put("storage_path",path)
            if(entity=="tenants") {
                put("tenant_id",id);put("document_type",type);put("file_name",file.name);put("mime_type",file.mime);put("size_bytes",file.file.length())
                existing?.let { put("id",it.id);put("revision",it.revision) }
            } else { put("entity_type",entity);put("id",id);put("revision",revision) }
        }
        book.execute(if(entity=="tenants") "document_save" else "attachment_save",data,request)
        val remaining=oldPath?.takeIf { it != path }?.let { cleanup(it) }
        file.file.delete()
        remaining
    }
    suspend fun delete(document: TenantDocument, request: String): String? {
        book.execute("document_delete",buildJsonObject { put("id",document.id);put("revision",document.revision) },request)
        return cleanup(document.storage_path)
    }
    suspend fun cleanup(path: String): String? = try {
        bucket.delete(listOf(path)); null
    } catch(error: CancellationException) { throw error }
    catch(_: Exception) { path }
    suspend fun download(path: String, mime: String): File = withContext(Dispatchers.IO) {
        require(path.matches(Regex("(tenants|payments|expenses|maintenance)/[A-Fa-f0-9-]{36}/[A-Za-z0-9._-]+")))
        val file=Exports.destination(context,path.substringAfterLast('/'))
        if (file.exists() && file.length() > 0) { file.setLastModified(System.currentTimeMillis()); return@withContext file }
        val temporary = File.createTempFile("download-", ".tmp", file.parentFile)
        try {
            bucket.downloadAuthenticatedTo(path, temporary)
            val expected = path.substringAfterLast('/').substringBeforeLast('.').substringAfterLast('-')
            if (expected.matches(Regex("[a-f0-9]{64}"))) require(digest(temporary) == expected)
            require(temporary.renameTo(file))
            file
        } finally { temporary.delete() }
    }
    private fun read(uri: Uri, type: String): PickedFile {
        val resolver=context.contentResolver
        val directory = File(context.noBackupFilesDir, "uploads").apply { mkdirs() }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7 * 86_400_000L }?.forEach { it.delete() }
        val file = File.createTempFile("picked-", ".tmp", directory)
        try {
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buffer=ByteArray(8192); var total=0L
                while(true) { val read=input.read(buffer);if(read<0) break
                    total+=read; require(total<=20*1024*1024) { "File exceeds 20 MB." };output.write(buffer,0,read) }
            } } ?: error("File unavailable")
            require(file.length()>0)
        val mime=resolver.getType(uri) ?: "application/octet-stream"
        require(mime in listOf("application/pdf","image/jpeg","image/png","image/webp","text/plain")) { "Choose PDF, JPG, PNG, WebP or text." }
        val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
            if(cursor.moveToFirst()) cursor.getString(0) else null } ?: "Document"
        if(type=="Profile photo") { profile(file); return PickedFile(file,"image/jpeg",name,"jpg") }
        return PickedFile(file,mime,name,when(mime) { "application/pdf"->"pdf";"image/jpeg"->"jpg";"image/png"->"png";"image/webp"->"webp";else->"txt" })
        } catch(error: Exception) { file.delete(); throw error }
    }
    private fun digest(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(8192)
            while(true) { val count=input.read(buffer);if(count<0) break;digest.update(buffer,0,count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun profile(file: File) {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.path,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0)
        var sample=1;while(bounds.outWidth/sample>2048 || bounds.outHeight/sample>2048) sample*=2
        val bitmap=requireNotNull(BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample }))
        try { file.outputStream().use { output -> require(bitmap.compress(Bitmap.CompressFormat.JPEG,85,output)) } }
        finally { bitmap.recycle() }
    }
}
data class PickedFile(val file: File,val mime: String,val name: String,val extension: String)

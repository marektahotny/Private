package sk.planx4.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import sk.planx4.core.model.Project

/** Renders [project] to a PDF in the app's cache dir and launches an Android share sheet for it. */
object ExportHelper {
    fun exportAndShare(context: Context, project: Project) {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "${project.name.filter { it.isLetterOrDigit() }}.pdf")
        PdfExporter.export(project, file)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Zdieľať pôdorys"))
    }
}

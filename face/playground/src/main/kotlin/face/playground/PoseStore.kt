package face.playground

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import face.core.expression.Expression
import face.core.expression.ExpressionCodec
import java.io.File

internal class PoseStore(private val context: Context) {
    private val dir: File? get() = context.getExternalFilesDir(DIR)

    fun loadAll(): List<Expression> =
        dir?.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.mapNotNull { file ->
            runCatching { ExpressionCodec.decode(file.readText()) }
                .onFailure { Log.w(TAG, "skipping ${file.name}", it) }
                .getOrNull()
        }

    fun save(expression: Expression): File? {
        val json = ExpressionCodec.encode(expression)
        Log.i(TAG, "pose '${expression.id}':\n$json")
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(expression.id, json))
        val target = dir?.let { File(it, "${expression.id}.json") } ?: return null
        target.writeText(json)
        return target
    }

    companion object {
        const val DIR = "poses"
        private const val TAG = "FacePlayground"

        fun sanitize(name: String): String = name.trim().lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_')
    }
}

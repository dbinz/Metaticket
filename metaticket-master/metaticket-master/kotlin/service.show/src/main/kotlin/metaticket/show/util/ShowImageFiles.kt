package metaticket.show.util

import org.slf4j.Logger
import java.io.File
import java.io.IOException

/**
 * Safe filesystem helpers for show images (temp upload dir → permanent dir).
 */
object ShowImageFiles {
    /**
     * Moves a temp upload into the permanent image path.
     * Prefer `renameTo`; fall back to copy+delete when rename fails (cross-device / Windows).
     */
    fun moveTempToPermanent(source: File, target: File, logger: Logger) {
        if (!source.exists() || !source.isFile) {
            throw IOException("Temp image missing or not a file: ${source.path}")
        }
        target.parentFile?.mkdirs()
        if (target.exists() && !target.delete()) {
            logger.warn("could not delete existing target before move: {}", target.path)
        }
        if (source.renameTo(target)) {
            return
        }
        logger.warn("renameTo failed for {} → {}; falling back to copy+delete", source.path, target.path)
        source.copyTo(target, overwrite = true)
        if (!source.delete()) {
            logger.warn("copied temp image but failed to delete source: {}", source.path)
        }
        if (!target.exists()) {
            throw IOException("Failed to move temp image to ${target.path}")
        }
    }

    /**
     * Deletes files that resolve under [imagePathTemp] only. Returns how many were deleted.
     */
    fun deleteTempFiles(filePaths: Collection<String>, imagePathTemp: String, logger: Logger): Int {
        if (filePaths.isEmpty()) return 0
        val tempRoot = File(imagePathTemp).canonicalFile
        var deleted = 0
        for (raw in filePaths) {
            if (raw.isBlank()) continue
            val file = try {
                File(raw).canonicalFile
            } catch (e: Exception) {
                logger.warn("skip delete; cannot resolve path {}: {}", raw, e.message)
                continue
            }
            if (!isUnderTempRoot(file, tempRoot)) {
                logger.warn("refuse delete outside temp dir: {} (root={})", file.path, tempRoot.path)
                continue
            }
            if (!file.isFile) {
                logger.debug("temp image already gone: {}", file.path)
                continue
            }
            if (file.delete()) {
                deleted++
                logger.info("deleted temp image {}", file.path)
            } else {
                logger.warn("failed to delete temp image {}", file.path)
            }
        }
        return deleted
    }

    private fun isUnderTempRoot(file: File, tempRoot: File): Boolean {
        val path = file.path
        val root = tempRoot.path
        return path == root || path.startsWith(root + File.separator)
    }
}

package com.example.engine

import android.os.StatFs
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

data class SaveVerificationResult(
    val success: Boolean,
    val sha256: String = "",
    val sizeBytes: Long = 0L,
    val backupFilePath: String? = null,
    val warningMessage: String = "",
    val errorMessage: String = ""
)

object FileSafetyUtils {

    fun computeSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun getAvailableStorageBytes(dir: File): Long {
        return try {
            val stat = StatFs(dir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    fun isLowStorage(dir: File, minBytes: Long = 20L * 1024L * 1024L): Boolean {
        return getAvailableStorageBytes(dir) < minBytes
    }

    fun verifyFileIntegrity(file: File, extension: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val ext = extension.lowercase().trimStart('.')
        return try {
            when (ext) {
                "docx", "xlsx", "pptx", "odt", "ods" -> {
                    ZipFile(file).use { zip ->
                        zip.entries().hasMoreElements()
                    }
                }
                "pdf" -> {
                    FileInputStream(file).use { fis ->
                        val header = ByteArray(5)
                        val count = fis.read(header)
                        count == 5 && String(header, Charsets.US_ASCII).startsWith("%PDF-")
                    }
                }
                else -> file.length() > 0L
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Writes to a temporary file first, verifies file integrity and checksum,
     * optionally backs up the previous file version, and atomically replaces the target file.
     * Never overwrites the original file if writing or verification fails.
     */
    fun atomicSaveFile(
        targetFile: File,
        extension: String,
        expectedLastSha256: String? = null,
        backupDir: File? = null,
        writeToTemp: (File) -> Unit
    ): SaveVerificationResult {
        val parentDir = targetFile.parentFile ?: return SaveVerificationResult(
            success = false,
            errorMessage = "Không xác định được thư mục đích."
        )
        if (!parentDir.exists()) parentDir.mkdirs()

        val lowSpaceWarning = if (isLowStorage(parentDir)) {
            "Cảnh báo: Dung lượng bộ nhớ thiết bị còn dưới 20 MB."
        } else ""

        var externalChangeWarning = ""
        if (targetFile.exists() && !expectedLastSha256.isNullOrEmpty()) {
            val currentDiskSha = computeSha256(targetFile)
            if (currentDiskSha.isNotEmpty() && currentDiskSha != expectedLastSha256) {
                externalChangeWarning = "Phát hiện tệp gốc đã bị thay đổi bởi ứng dụng khác. Đã tự động tạo bản sao lưu trước khi ghi."
            }
        }

        // Create backup if targetFile already exists and backupDir is provided
        var backupPath: String? = null
        if (targetFile.exists() && targetFile.length() > 0L && backupDir != null) {
            try {
                if (!backupDir.exists()) backupDir.mkdirs()
                val backupFile = File(
                    backupDir,
                    "${targetFile.nameWithoutExtension}_v${System.currentTimeMillis()}.${extension.trimStart('.')}"
                )
                targetFile.copyTo(backupFile, overwrite = true)
                backupPath = backupFile.absolutePath
            } catch (_: Exception) {
            }
        }

        val tempFile = File(parentDir, ".${targetFile.name}.tmp_${System.currentTimeMillis()}")
        return try {
            writeToTemp(tempFile)
            if (!verifyFileIntegrity(tempFile, extension)) {
                tempFile.delete()
                return SaveVerificationResult(
                    success = false,
                    errorMessage = "Kiểm tra tính toàn vẹn tệp thất bại sau khi ghi tạm. Tệp gốc được giữ nguyên an toàn."
                )
            }
            // Replace targetFile safely
            if (targetFile.exists()) {
                val oldBackup = File(parentDir, ".${targetFile.name}.old")
                targetFile.copyTo(oldBackup, overwrite = true)
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                oldBackup.delete()
            } else {
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }
            val newSha = computeSha256(targetFile)
            val combinedWarning = listOf(lowSpaceWarning, externalChangeWarning)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            SaveVerificationResult(
                success = true,
                sha256 = newSha,
                sizeBytes = targetFile.length(),
                backupFilePath = backupPath,
                warningMessage = combinedWarning
            )
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            SaveVerificationResult(
                success = false,
                errorMessage = "Lỗi khi lưu tệp: ${e.localizedMessage ?: "Không xác định"}. Tệp gốc không bị ghi đè."
            )
        }
    }
}

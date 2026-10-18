package com.example.data.remote

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    val files: List<DriveRemoteFile> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DriveRemoteFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String? = null,
    val size: String? = null
)

interface GoogleDriveRetrofitApi {
    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String = "trashed = false",
        @Query("fields") fields: String = "files(id,name,mimeType,modifiedTime,size)",
        @Query("pageSize") pageSize: Int = 50
    ): DriveFileListResponse
}

/**
 * Extensible cloud storage provider interface supporting Google Drive now,
 * and architected for OneDrive / WebDAV modules in future expansions.
 */
interface CloudStorageProvider {
    val providerName: String
    suspend fun listRemoteFiles(accessToken: String, searchQuery: String = ""): Result<List<DriveRemoteFile>>
    suspend fun createFolder(accessToken: String, folderName: String): Result<DriveRemoteFile>
    suspend fun uploadFile(
        accessToken: String,
        localFile: File,
        mimeType: String,
        existingDriveFileId: String? = null
    ): Result<DriveRemoteFile>
    suspend fun downloadFile(accessToken: String, driveFileId: String, targetFile: File): Result<File>
}

class GoogleDriveCloudProvider : CloudStorageProvider {
    override val providerName: String = "Google Drive (OAuth 2.0)"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: GoogleDriveRetrofitApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GoogleDriveRetrofitApi::class.java)

    override suspend fun listRemoteFiles(
        accessToken: String,
        searchQuery: String
    ): Result<List<DriveRemoteFile>> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Chưa có token xác thực Google Drive."))
        }
        runCatching {
            val q = if (searchQuery.isBlank()) {
                "trashed = false"
            } else {
                "trashed = false and name contains '${searchQuery.replace("'", "")}'"
            }
            val response = api.listFiles(authHeader = "Bearer $accessToken", query = q)
            response.files
        }
    }

    override suspend fun createFolder(
        accessToken: String,
        folderName: String
    ): Result<DriveRemoteFile> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Chưa đăng nhập Google Drive."))
        }
        runCatching {
            val metadataJson = """{"name": "${folderName.replace("\"", "")}", "mimeType": "application/vnd.google-apps.folder"}"""
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?fields=id,name,mimeType,modifiedTime")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Google Drive API trả về mã ${resp.code}")
                }
                val bodyStr = resp.body?.string().orEmpty()
                val adapter = moshi.adapter(DriveRemoteFile::class.java)
                adapter.fromJson(bodyStr) ?: DriveRemoteFile(
                    id = "folder_${System.currentTimeMillis()}",
                    name = folderName,
                    mimeType = "application/vnd.google-apps.folder"
                )
            }
        }
    }

    override suspend fun uploadFile(
        accessToken: String,
        localFile: File,
        mimeType: String,
        existingDriveFileId: String?
    ): Result<DriveRemoteFile> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Chưa có quyền truy cập Google Drive."))
        }
        runCatching {
            val url = if (existingDriveFileId.isNullOrBlank()) {
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=media&fields=id,name,mimeType,modifiedTime,size"
            } else {
                "https://www.googleapis.com/upload/drive/v3/files/$existingDriveFileId?uploadType=media&fields=id,name,mimeType,modifiedTime,size"
            }
            val builder = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
            val req = if (existingDriveFileId.isNullOrBlank()) {
                builder.post(localFile.asRequestBody(mimeType.toMediaType())).build()
            } else {
                builder.patch(localFile.asRequestBody(mimeType.toMediaType())).build()
            }
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Không thể tải tệp lên Drive (HTTP ${resp.code})")
                }
                val bodyStr = resp.body?.string().orEmpty()
                val adapter = moshi.adapter(DriveRemoteFile::class.java)
                adapter.fromJson(bodyStr) ?: DriveRemoteFile(
                    id = existingDriveFileId ?: "drive_${System.currentTimeMillis()}",
                    name = localFile.name,
                    mimeType = mimeType
                )
            }
        }
    }

    override suspend fun downloadFile(
        accessToken: String,
        driveFileId: String,
        targetFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Chưa đăng nhập Google Drive."))
        }
        runCatching {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Tải tệp từ Google Drive thất bại (HTTP ${resp.code})")
                }
                val body = resp.body ?: throw IllegalStateException("Dữ liệu phản hồi rỗng")
                targetFile.parentFile?.mkdirs()
                FileOutputStream(targetFile).use { fos ->
                    body.byteStream().copyTo(fos)
                }
                targetFile
            }
        }
    }
}

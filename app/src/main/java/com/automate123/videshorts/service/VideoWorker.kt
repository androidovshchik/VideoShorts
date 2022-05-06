package com.automate123.videshorts.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.work.*
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import com.automate123.videshorts.EXTRA_FILENAME
import com.automate123.videshorts.extension.qPath
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@HiltWorker
class VideoWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val rootDir: File
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        with(applicationContext) {
            val dirname = inputData.getString(EXTRA_DIRNAME)!!
            val sessions = mutableListOf<Session>()
            try {
                val workDir = File(rootDir, dirname)
                check(workDir.exists())

                val videoFiles = mutableListOf<File>()
                withContext(Dispatchers.IO) {
                    videoFiles.addAll(workDir.listFiles()
                        ?.filter { it.name.matches("^[0-9]+\\.mp4$".toRegex()) }
                        ?.sorted()
                        .orEmpty())
                    check(videoFiles.isNotEmpty())
                }

                val outputFile = File(workDir, nameFormatter.format(LocalDateTime.now()))

                if (videoFiles.size < 2) {
                    withContext(Dispatchers.IO) {
                        videoFiles.first().copyTo(outputFile, true)
                    }
                    return Result.success(workDataOf(EXTRA_FILENAME to outputFile.name))
                }

                withContext(Dispatchers.IO) {
                    val listFile = File(workDir, "$id.txt")
                    listFile.writeText(videoFiles.joinToString("\n") { "file ${it.qPath}" })

                    sessions.add(FFmpegKit.execute("-y -f concat -safe 0 -i ${listFile.qPath} -c copy ${outputFile.qPath}"))
                    if (!ReturnCode.isSuccess(sessions.last().returnCode)) {
                        throw Throwable(sessions.last().failStackTrace)
                    }
                }
                return Result.success(workDataOf(EXTRA_FILENAME to outputFile.name))
            } catch (e: CancellationException) {
                sessions.forEach {
                    FFmpegKit.cancel(it.sessionId)
                }
            } catch (e: Throwable) {
                Timber.e(e)
            }
        }
        return Result.failure()
    }

    companion object {

        const val NAME = "video"

        private const val EXTRA_DIRNAME = "dirname"

        private val nameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss'.mp4'")

        fun launch(context: Context, dirname: String): LiveData<WorkInfo> {
            val request = OneTimeWorkRequestBuilder<VideoWorker>()
                .setInputData(Data.Builder()
                    .putString(EXTRA_DIRNAME, dirname)
                    .build())
                .build()
            with(WorkManager.getInstance(context)) {
                enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
                return getWorkInfoByIdLiveData(request.id)
            }
        }

        fun cancel(context: Context) {
            with(WorkManager.getInstance(context)) {
                cancelUniqueWork(NAME)
            }
        }
    }
}
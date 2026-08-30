package pixl.rec.ui.vault

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import pixl.rec.core.model.RecorderState
import pixl.rec.service.RecordingService
import pixl.rec.ui.vault.model.RecordingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "VaultViewModel"

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        refreshRecordings()
        observeRecordingFinished()
    }

    private fun observeRecordingFinished() {
        viewModelScope.launch {
            RecordingService.serviceState.collect { state ->
                if (state is RecorderState.Finished) {
                    delay(350)
                    refreshRecordings()
                }
            }
        }
    }

    fun refreshRecordings() {
        viewModelScope.launch {
            _isLoading.value = true
            val items = withContext(Dispatchers.IO) {
                queryMediaStoreRecordings()
            }
            _recordings.value = items
            _isLoading.value = false
        }
    }

    private fun queryMediaStoreRecordings(): List<RecordingItem> {
        val context = getApplication<Application>()
        val result = mutableListOf<RecordingItem>()

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        // Filter for recordings created by PixL REC
        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE 'REC_%.mp4' OR ${MediaStore.Video.Media.RELATIVE_PATH} LIKE '%PixL-REC%'"
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "REC_recording.mp4"
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)

                    val uri = ContentUris.withAppendedId(collectionUri, id)

                    // Load thumbnail if available
                    val thumbnail = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }

                    result.add(
                        RecordingItem(
                            id = id,
                            uri = uri,
                            displayName = name,
                            durationMs = duration,
                            sizeBytes = size,
                            dateAddedSec = dateAdded,
                            width = width,
                            height = height,
                            thumbnail = thumbnail
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to query recordings from MediaStore", e)
        }

        return result
    }

    fun playRecording(context: Context, item: RecordingItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Play Recording"))
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch video player for ${item.uri}", e)
        }
    }

    fun shareRecording(context: Context, item: RecordingItem) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Recording"))
        } catch (e: Exception) {
            Log.e(tag, "Failed to share video ${item.uri}", e)
        }
    }

    fun deleteRecording(context: Context, item: RecordingItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.delete(item.uri, null, null)
                    Log.i(tag, "Deleted recording: ${item.uri}")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to delete recording ${item.uri}", e)
                }
            }
            refreshRecordings()
        }
    }
}

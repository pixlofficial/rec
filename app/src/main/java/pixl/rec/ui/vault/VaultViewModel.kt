package pixl.rec.ui.vault

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pixl.rec.core.model.RecorderState
import pixl.rec.service.RecordingService
import pixl.rec.service.ReplayClipEvent
import pixl.rec.service.ScreenshotEvent
import pixl.rec.ui.vault.model.RecordingItem
import pixl.rec.ui.vault.model.VaultCatalog
import pixl.rec.ui.vault.model.VaultMediaClassifier
import pixl.rec.ui.vault.model.VaultMediaItem
import pixl.rec.ui.vault.model.VaultSummary

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "VaultViewModel"

    private val _catalog = MutableStateFlow(VaultCatalog(isLoading = true))
    val catalog: StateFlow<VaultCatalog> = _catalog.asStateFlow()

    private val _mediaItems = MutableStateFlow<List<VaultMediaItem>>(emptyList())
    val mediaItems: StateFlow<List<VaultMediaItem>> = _mediaItems.asStateFlow()

    private val _summary = MutableStateFlow(VaultSummary.EMPTY)
    val summary: StateFlow<VaultSummary> = _summary.asStateFlow()

    // Backward compatibility for legacy video-only consumers
    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activePlayerItem = MutableStateFlow<VaultMediaItem?>(null)
    val activePlayerItem: StateFlow<VaultMediaItem?> = _activePlayerItem.asStateFlow()

    private val _activePlayerRecording = MutableStateFlow<RecordingItem?>(null)
    val activePlayerRecording: StateFlow<RecordingItem?> = _activePlayerRecording.asStateFlow()

    private val _activeImagePreviewItem = MutableStateFlow<VaultMediaItem?>(null)
    val activeImagePreviewItem: StateFlow<VaultMediaItem?> = _activeImagePreviewItem.asStateFlow()

    private var thumbnailJob: Job? = null
    private val thumbnailCache = LruCache<String, Bitmap>(96)

    init {
        refreshRecordings()
        observeRecordingEvents()
    }

    private fun observeRecordingEvents() {
        // 1. Finished full recording
        viewModelScope.launch {
            RecordingService.serviceState.collect { state ->
                if (state is RecorderState.Finished) {
                    delay(400)
                    refreshRecordings()
                }
            }
        }

        // 2. Saved instant replay clip
        viewModelScope.launch {
            RecordingService.replayClipEvents.collect { event ->
                if (event is ReplayClipEvent.Success) {
                    delay(300)
                    refreshRecordings()
                }
            }
        }

        // 3. Saved in-game screenshot
        viewModelScope.launch {
            RecordingService.screenshotEvents.collect { event ->
                if (event is ScreenshotEvent.Success) {
                    delay(300)
                    refreshRecordings()
                }
            }
        }
    }

    fun openInAppPlayer(item: VaultMediaItem) {
        if (item.isVideo) {
            _activePlayerItem.value = item
            _activePlayerRecording.value = RecordingItem(
                id = item.id,
                uri = item.uri,
                displayName = item.displayName,
                durationMs = item.durationMs,
                sizeBytes = item.sizeBytes,
                dateAddedSec = item.dateAddedSec,
                width = item.width,
                height = item.height,
                thumbnail = item.thumbnail
            )
        }
    }

    fun openInAppPlayer(item: RecordingItem) {
        _activePlayerRecording.value = item
        _activePlayerItem.value = VaultMediaItem.fromRecordingItem(item)
    }

    fun closeInAppPlayer() {
        _activePlayerItem.value = null
        _activePlayerRecording.value = null
    }

    fun openImagePreview(item: VaultMediaItem) {
        if (item.isImage) {
            _activeImagePreviewItem.value = item
        }
    }

    fun closeImagePreview() {
        _activeImagePreviewItem.value = null
    }

    fun refreshRecordings() {
        viewModelScope.launch {
            _isLoading.value = true
            _catalog.value = _catalog.value.copy(isLoading = true)

            val items = withContext(Dispatchers.IO) {
                queryUnifiedCatalog()
            }

            val summary = VaultSummary.fromItems(items)

            _mediaItems.value = items
            _summary.value = summary
            _catalog.value = VaultCatalog(
                items = items,
                summary = summary,
                isLoading = false,
                lastError = null
            )
            _recordings.value = items.filter { it.isVideo }.map { item ->
                RecordingItem(
                    id = item.id,
                    uri = item.uri,
                    displayName = item.displayName,
                    durationMs = item.durationMs,
                    sizeBytes = item.sizeBytes,
                    dateAddedSec = item.dateAddedSec,
                    width = item.width,
                    height = item.height,
                    thumbnail = item.thumbnail
                )
            }
            _isLoading.value = false

            loadThumbnailsAsync(items)
        }
    }

    private fun loadThumbnailsAsync(items: List<VaultMediaItem>) {
        thumbnailJob?.cancel()
        thumbnailJob = viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            for (item in items) {
                if (!isActive) break
                if (item.thumbnail == null) {
                    val cached = thumbnailCache.get(item.uniqueKey)
                    val bitmap = cached ?: try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val size = if (item.isImage) Size(300, 300) else Size(320, 180)
                            context.contentResolver.loadThumbnail(item.uri, size, null)?.also {
                                thumbnailCache.put(item.uniqueKey, it)
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }

                    if (bitmap != null) {
                        _mediaItems.value = _mediaItems.value.map {
                            if (it.uri == item.uri) it.copy(thumbnail = bitmap) else it
                        }
                        _catalog.value = _catalog.value.copy(
                            items = _mediaItems.value
                        )
                        if (item.isVideo) {
                            _recordings.value = _recordings.value.map {
                                if (it.uri == item.uri) it.copy(thumbnail = bitmap) else it
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun queryUnifiedCatalog(): List<VaultMediaItem> = withContext(Dispatchers.IO) {
        val videoDeferred = async { queryVideos() }
        val imageDeferred = async { queryImages() }

        val videos = videoDeferred.await()
        val images = imageDeferred.await()

        (videos + images).sortedByDescending { it.dateAddedSec }
    }

    private fun queryVideos(): List<VaultMediaItem> {
        val context = getApplication<Application>()
        val result = mutableListOf<VaultMediaItem>()

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
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE 'REC_%.mp4' OR " +
                "${MediaStore.Video.Media.DISPLAY_NAME} LIKE 'STREAM_%.mp4' OR " +
                "${MediaStore.Video.Media.DISPLAY_NAME} LIKE 'CLIP_%.mp4' OR " +
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE '%PixL-REC%'"

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
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "REC_video.mp4"
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"

                    val uri = ContentUris.withAppendedId(collectionUri, id)
                    val cachedThumbnail = thumbnailCache.get(uri.toString())
                    val type = VaultMediaClassifier.classify(name, mime)

                    result.add(
                        VaultMediaItem(
                            id = id,
                            uri = uri,
                            displayName = name,
                            mediaType = type,
                            durationMs = duration,
                            sizeBytes = size,
                            dateAddedSec = dateAdded,
                            width = width,
                            height = height,
                            mimeType = mime,
                            thumbnail = cachedThumbnail
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to query videos from MediaStore", e)
        }

        return result
    }

    private fun queryImages(): List<VaultMediaItem> {
        val context = getApplication<Application>()
        val result = mutableListOf<VaultMediaItem>()

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE 'SHOT_%.png' OR " +
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE 'SHOT_%.jpg' OR " +
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE '%PixL-REC%'"

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "SHOT_capture.png"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/png" else "image/png"

                    val uri = ContentUris.withAppendedId(collectionUri, id)
                    val cachedThumbnail = thumbnailCache.get(uri.toString())
                    val type = VaultMediaClassifier.classify(name, mime)

                    result.add(
                        VaultMediaItem(
                            id = id,
                            uri = uri,
                            displayName = name,
                            mediaType = type,
                            durationMs = 0L,
                            sizeBytes = size,
                            dateAddedSec = dateAdded,
                            width = width,
                            height = height,
                            mimeType = mime,
                            thumbnail = cachedThumbnail
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to query screenshots from MediaStore", e)
        }

        return result
    }

    fun playMedia(context: Context, item: VaultMediaItem) {
        if (item.isVideo) {
            openInAppPlayer(item)
        } else {
            openImagePreview(item)
        }
    }

    fun playRecording(context: Context, item: RecordingItem) {
        openInAppPlayer(item)
    }

    fun shareMedia(context: Context, item: VaultMediaItem) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${item.mediaType.displayName}"))
        } catch (e: Exception) {
            Log.e(tag, "Failed to share media ${item.uri}", e)
        }
    }

    fun shareRecording(context: Context, item: RecordingItem) {
        shareMedia(context, VaultMediaItem.fromRecordingItem(item))
    }

    fun deleteMedia(context: Context, item: VaultMediaItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.delete(item.uri, null, null)
                    Log.i(tag, "Deleted media: ${item.uri}")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to delete media ${item.uri}", e)
                }
            }
            refreshRecordings()
        }
    }

    fun deleteRecording(context: Context, item: RecordingItem) {
        deleteMedia(context, VaultMediaItem.fromRecordingItem(item))
    }
}

package com.example.yourswelnes.feature.camera.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourswelnes.feature.home.data.GroupDetailsRepository
import com.example.yourswelnes.feature.home.model.Group
import com.example.yourswelnes.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Reactive state describing whether the selected group has an activity active *right now*.
 * Capture is only permitted when a non-blank keyword maps to the current time window.
 */
data class CameraActivityState(
    val isLoading: Boolean = true,
    val groupName: String? = null,
    val activityName: String? = null
) {
    val canCapture: Boolean get() = !isLoading && !activityName.isNullOrBlank()
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val groupDetailsRepository: GroupDetailsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: Long = savedStateHandle.get<Long>(Destinations.ARG_GROUP_ID) ?: -1L

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Idle)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _activityState = MutableStateFlow(CameraActivityState())
    val activityState: StateFlow<CameraActivityState> = _activityState.asStateFlow()

    private var selectedGroup: Group? = null

    init {
        viewModelScope.launch {
            // Groups were already fetched (and cached) before navigating here, so this is a
            // cache read — no network round-trip needed.
            selectedGroup = groupDetailsRepository.fetchGroups()
                .getOrNull()
                ?.firstOrNull { it.groupId == groupId }

            // Re-evaluate periodically so the shutter unlocks/locks as activity windows
            // open and close while the user sits on the camera screen.
            while (isActive) {
                val next = CameraActivityState(
                    isLoading = false,
                    groupName = selectedGroup?.groupName,
                    activityName = currentActivityKeyword(selectedGroup)
                )
                if (next != _activityState.value) _activityState.value = next
                delay(ACTIVITY_REFRESH_MS)
            }
        }
    }

    fun toggleLens() {
        _lensFacing.value =
            if (_lensFacing.value == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT
            else
                CameraSelector.LENS_FACING_BACK
    }

    fun toggleFlashMode() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun capturePhoto(imageCapture: ImageCapture) {
        if (_uiState.value is CameraUiState.Capturing) return

        // Hard gate: no active activity for this group right now → no photo.
        val activityName = currentActivityKeyword(selectedGroup)
        if (activityName.isNullOrBlank()) {
            Timber.w("Capture blocked — no active activity for the selected group at this time")
            return
        }
        val groupName = selectedGroup?.groupName ?: "Unknown Group"

        _uiState.value = CameraUiState.Capturing

        val tempFile = File.createTempFile("wl_raw_", ".jpg", context.cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        imageCapture.takePicture(
            outputOptions,
            context.mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val uri = addOverlay(tempFile, groupName, activityName)
                            tempFile.delete()
                            _uiState.value = CameraUiState.Captured(uri)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to process captured photo")
                            _uiState.value = CameraUiState.Error("Failed to save photo: ${e.message}")
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "Photo capture failed")
                    _uiState.value = CameraUiState.Error(exception.message ?: "Capture failed")
                }
            }
        )
    }

    /**
     * Returns the keyword of the activity whose window contains the current time for [group],
     * or null when no window is active (or the keyword is blank/undefined).
     */
    private fun currentActivityKeyword(group: Group?): String? {
        if (group == null) return null
        val now = LocalTime.now()
        return group.activities
            .firstOrNull { slot -> !now.isBefore(slot.startTime) && !now.isAfter(slot.endTime) }
            ?.keyword
            ?.takeIf { it.isNotBlank() }
    }

    fun retake() {
        _uiState.value = CameraUiState.Idle
    }

    private fun addOverlay(sourceFile: File, groupName: String, activityName: String): Uri {
        val original = BitmapFactory.decodeFile(sourceFile.absolutePath)
        val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
        original.recycle()

        val canvas = Canvas(mutable)
        val timestamp = SimpleDateFormat("dd MMM yyyy  HH:mm:ss", Locale.getDefault()).format(Date())

        val lines = listOf(timestamp, "Group: $groupName", "Activity: $activityName")

        val textSize = mutable.width * 0.038f
        val lineSpacing = textSize * 1.5f
        val margin = mutable.width * 0.03f
        val horizontalPadding = textSize * 0.5f
        val verticalPadding = textSize * 0.45f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 230
        }

        val textBounds = Rect()
        val maxTextWidth = lines.maxOf { line ->
            textPaint.getTextBounds(line, 0, line.length, textBounds)
            textBounds.width().toFloat()
        }

        val totalTextHeight = textSize + (lines.size - 1) * lineSpacing
        val boxLeft = margin
        val boxBottom = mutable.height.toFloat() - margin
        val boxTop = boxBottom - verticalPadding * 2 - totalTextHeight
        val boxRight = boxLeft + maxTextWidth + horizontalPadding * 2
        val cornerRadius = textSize * 0.25f

        canvas.drawRoundRect(
            RectF(boxLeft, boxTop, boxRight, boxBottom),
            cornerRadius, cornerRadius,
            backgroundPaint
        )

        lines.forEachIndexed { index, line ->
            val x = boxLeft + horizontalPadding
            val y = boxTop + verticalPadding + textSize + index * lineSpacing
            canvas.drawText(line, x, y, textPaint)
        }

        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val outFile = File(outDir, "WL_${System.currentTimeMillis()}.jpg")

        FileOutputStream(outFile).use { stream ->
            mutable.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        mutable.recycle()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    private companion object {
        const val ACTIVITY_REFRESH_MS = 20_000L
    }
}

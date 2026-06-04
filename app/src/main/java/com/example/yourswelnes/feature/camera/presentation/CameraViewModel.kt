package com.example.yourswelnes.feature.camera.presentation

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yourswelnes.feature.home.data.repository.GroupDetailsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val groupDetailsRepository: GroupDetailsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Idle)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

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
                            val activityName = getCurrentActivityName()
                            val uri = addTimestampAndActivity(tempFile, activityName)
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

    private suspend fun getCurrentActivityName(): String? {
        val slots = groupDetailsRepository.fetchGroupDetails().getOrNull() ?: return null
        val now = LocalTime.now()
        return slots.firstOrNull { slot ->
            !now.isBefore(slot.startTime) && !now.isAfter(slot.endTime)
        }?.keyword
    }

    fun retake() {
        _uiState.value = CameraUiState.Idle
    }

    private fun addTimestampAndActivity(sourceFile: File, activityName: String?): Uri {
        val original = BitmapFactory.decodeFile(sourceFile.absolutePath)
        val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
        original.recycle()

        val canvas = Canvas(mutable)
        val timestamp = SimpleDateFormat("yyyy-MM-dd   HH:mm:ss", Locale.getDefault())
            .format(Date())
        
        val displayText = if (activityName != null) "$activityName | $timestamp" else timestamp

        val textSize = mutable.width * 0.038f
        val margin = mutable.width * 0.03f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }

        val textBounds = Rect()
        textPaint.getTextBounds(displayText, 0, displayText.length, textBounds)

        val horizontalPadding = textSize * 0.45f
        val verticalPadding = textSize * 0.32f
        val boxLeft = margin
        val boxBottom = mutable.height - margin
        val boxTop = boxBottom - textBounds.height() - (verticalPadding * 2f)
        val boxRight = boxLeft + textBounds.width() + (horizontalPadding * 2f)
        val cornerRadius = textSize * 0.25f

        canvas.drawRoundRect(
            RectF(boxLeft, boxTop, boxRight, boxBottom),
            cornerRadius,
            cornerRadius,
            backgroundPaint
        )

        val x = boxLeft + horizontalPadding
        val y = boxBottom - verticalPadding
        canvas.drawText(displayText, x, y, textPaint)

        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.filesDir
        val outFile = File(outDir, "WL_${System.currentTimeMillis()}.jpg")

        FileOutputStream(outFile).use { stream ->
            mutable.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        mutable.recycle()

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
    }
}

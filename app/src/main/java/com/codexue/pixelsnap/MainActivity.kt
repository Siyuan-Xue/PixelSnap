package com.codexue.pixelsnap

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.codexue.pixelsnap.ui.theme.ClaudeClay
import com.codexue.pixelsnap.ui.theme.ClaudeGray050
import com.codexue.pixelsnap.ui.theme.ClaudeIvoryDark
import com.codexue.pixelsnap.ui.theme.ClaudeOat
import com.codexue.pixelsnap.ui.theme.ClaudeSlateDark
import com.codexue.pixelsnap.ui.theme.PixelError
import com.codexue.pixelsnap.ui.theme.PixelSnapTheme
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidGraphicsColor

private val RequiredPermissions = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
)

private sealed class CaptureStatus {
    object Starting : CaptureStatus()
    object Ready : CaptureStatus()
    object Saving : CaptureStatus()
    object RecordingVideo : CaptureStatus()
    object Saved : CaptureStatus()
    object Error : CaptureStatus()
}

private data class CapturedPreview(
    val uri: Uri,
    val kind: MediaKind,
    val isPlaying: Boolean = false,
)

private val PreviewFrameMinimumOuterPadding = 32.dp

private class AspectFitTextureView(context: Context) : TextureView(context) {
    private var videoWidth = 0
    private var videoHeight = 0
    private var rotationDegrees = 0

    fun setVideoGeometry(width: Int, height: Int, rotation: Int) {
        val normalizedRotation = rotation.normalizedRightAngleDegrees()
        if (
            videoWidth == width &&
            videoHeight == height &&
            rotationDegrees == normalizedRotation
        ) {
            return
        }
        videoWidth = width
        videoHeight = height
        rotationDegrees = normalizedRotation
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        val maxHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (maxWidth <= 0 || maxHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            setMeasuredDimension(maxWidth, maxHeight)
            return
        }

        val displayWidth = if (rotationDegrees.isQuarterTurn()) videoHeight else videoWidth
        val displayHeight = if (rotationDegrees.isQuarterTurn()) videoWidth else videoHeight
        val scale = min(
            maxWidth.toFloat() / displayWidth.toFloat(),
            maxHeight.toFloat() / displayHeight.toFloat(),
        )
        val measuredWidth = (displayWidth * scale).roundToInt()
            .coerceIn(1, maxWidth)
        val measuredHeight = (displayHeight * scale).roundToInt()
            .coerceIn(1, maxHeight)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            PixelSnapTheme {
                PixelSnapApp()
            }
        }
    }
}

@Composable
private fun PixelSnapApp() {
    val context = LocalContext.current
    var permissionsGranted by remember {
        mutableStateOf(RequiredPermissions.all { context.hasPermission(it) })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsGranted = RequiredPermissions.all { permission ->
            context.hasPermission(permission)
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(RequiredPermissions)
        }
    }

    if (permissionsGranted) {
        PixelSnapCameraRoute()
    } else {
        PermissionPanel(
            onRequestPermission = { permissionLauncher.launch(RequiredPermissions) },
        )
    }
}

@Composable
private fun PixelSnapCameraRoute() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val captureScope = rememberCoroutineScope()
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    var status by remember { mutableStateOf<CaptureStatus>(CaptureStatus.Starting) }
    var cameraPreview by remember { mutableStateOf<Preview?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var capturedPreview by remember { mutableStateOf<CapturedPreview?>(null) }
    var recordingStartedAtMillis by remember { mutableStateOf<Long?>(null) }
    var photoFlashVisible by remember { mutableStateOf(false) }

    LaunchedEffect(previewView, lifecycleOwner) {
        val provider = appContext.cameraProvider()
        provider.unbindAll()

        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val photoCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(rotation)
            .build()
        val movieCapture = VideoCapture.withOutput(Recorder.Builder().build()).also {
            it.targetRotation = rotation
        }

        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                photoCapture,
                movieCapture,
            )
            cameraPreview = preview
            imageCapture = photoCapture
            videoCapture = movieCapture
            status = CaptureStatus.Ready
        } catch (error: Exception) {
            cameraPreview = null
            status = CaptureStatus.Error
        }
    }

    DisposableEffect(cameraPreview, imageCapture, videoCapture) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                cameraPreview?.targetRotation = rotation
                imageCapture?.targetRotation = rotation
                videoCapture?.targetRotation = rotation
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    val currentRecording by rememberUpdatedState(activeRecording)
    DisposableEffect(Unit) {
        onDispose {
            runCatching { currentRecording?.close() }
            val providerFuture = ProcessCameraProvider.getInstance(appContext)
            if (providerFuture.isDone) {
                runCatching { providerFuture.get().unbindAll() }
            }
        }
    }

    LaunchedEffect(capturedPreview?.uri, capturedPreview?.kind) {
        val preview = capturedPreview ?: return@LaunchedEffect
        if (preview.kind != MediaKind.Photo) return@LaunchedEffect
        delay(2_600L)
        if (capturedPreview?.uri == preview.uri) {
            capturedPreview = null
            if (activeRecording == null && status is CaptureStatus.Saved) {
                status = CaptureStatus.Ready
            }
        }
    }

    LaunchedEffect(photoFlashVisible) {
        if (photoFlashVisible) {
            delay(90L)
            photoFlashVisible = false
        }
    }

    fun closePreview() {
        capturedPreview = null
        if (activeRecording == null && status is CaptureStatus.Saved) {
            status = CaptureStatus.Ready
        }
    }

    fun takePhoto() {
        if (status is CaptureStatus.Saving) return
        if (activeRecording != null) return
        val capture = imageCapture ?: run {
            status = CaptureStatus.Error
            return
        }
        val spec = PixelSnapMedia.buildSpec(MediaKind.Photo, System.currentTimeMillis())
        val rawFile = runCatching {
            File.createTempFile("pixelsnap_raw_", ".jpg", appContext.cacheDir)
        }.getOrElse {
            status = CaptureStatus.Error
            return
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(rawFile).build()

        status = CaptureStatus.Saving
        photoFlashVisible = true
        capturedPreview = null
        capture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    captureScope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                appContext.contentResolver.saveWatermarkedPhoto(rawFile, spec)
                            }
                        }
                        rawFile.delete()
                        result
                            .onSuccess { uri ->
                                status = CaptureStatus.Saved
                                capturedPreview = CapturedPreview(uri = uri, kind = MediaKind.Photo)
                            }
                            .onFailure {
                                status = CaptureStatus.Error
                            }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    rawFile.delete()
                    status = CaptureStatus.Error
                }
            },
        )
    }

    @SuppressLint("MissingPermission")
    fun startVideo() {
        if (status is CaptureStatus.Saving) return
        if (activeRecording != null) return
        val capture = videoCapture ?: run {
            status = CaptureStatus.Error
            return
        }
        val spec = PixelSnapMedia.buildSpec(MediaKind.Video, System.currentTimeMillis())
        val outputOptions = MediaStoreOutputOptions.Builder(
            appContext.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )
            .setContentValues(spec.toContentValues())
            .build()

        status = CaptureStatus.Saving
        capturedPreview = null
        val pendingRecording = capture.output
            .prepareRecording(appContext, outputOptions)
            .withAudioEnabled()

        activeRecording = pendingRecording.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    status = CaptureStatus.RecordingVideo
                    recordingStartedAtMillis = SystemClock.elapsedRealtime()
                }

                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    recordingStartedAtMillis = null
                    if (event.hasError()) {
                        status = CaptureStatus.Error
                    } else {
                        status = CaptureStatus.Saved
                        val uri = event.outputResults.outputUri
                        if (uri != Uri.EMPTY) {
                            capturedPreview = CapturedPreview(uri = uri, kind = MediaKind.Video)
                        }
                    }
                }
            }
        }
    }

    fun stopVideo() {
        activeRecording?.stop()
        recordingStartedAtMillis = null
        status = CaptureStatus.Saving
    }

    fun handleTap(isVideoPlayButtonTap: Boolean) {
        val preview = capturedPreview
        when {
            preview?.kind == MediaKind.Photo -> closePreview()
            preview?.kind == MediaKind.Video && !preview.isPlaying && isVideoPlayButtonTap -> {
                capturedPreview = preview.copy(isPlaying = true)
            }
            preview?.kind == MediaKind.Video -> closePreview()
            status is CaptureStatus.Saving -> Unit
            activeRecording != null -> stopVideo()
            else -> takePhoto()
        }
    }

    fun handleLongPress() {
        if (capturedPreview != null) return
        if (status is CaptureStatus.Saving) return
        if (activeRecording != null) {
            stopVideo()
        } else {
            startVideo()
        }
    }

    PixelCameraScreen(
        previewView = previewView,
        status = status,
        capturedPreview = capturedPreview,
        recordingStartedAtMillis = recordingStartedAtMillis,
        photoFlashVisible = photoFlashVisible,
        onTap = ::handleTap,
        onLongPress = ::handleLongPress,
    )
}

@Composable
private fun PixelCameraScreen(
    previewView: PreviewView,
    status: CaptureStatus,
    capturedPreview: CapturedPreview?,
    recordingStartedAtMillis: Long?,
    photoFlashVisible: Boolean,
    onTap: (isVideoPlayButtonTap: Boolean) -> Unit,
    onLongPress: () -> Unit,
) {
    val photoFlashAlpha by animateFloatAsState(
        targetValue = if (photoFlashVisible) 0.72f else 0f,
        animationSpec = tween(durationMillis = if (photoFlashVisible) 28 else 220),
        label = "photoFlashAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds(),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        )
        if (capturedPreview != null) {
            CapturedPreviewOverlay(
                preview = capturedPreview,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (capturedPreview == null) {
            ViewfinderOverlay(status = status)
        }
        RecordingHud(startedAtMillis = recordingStartedAtMillis)
        CaptureFlashOverlay(alpha = photoFlashAlpha)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(status, capturedPreview) {
                    detectTapGestures(
                        onTap = { offset ->
                            val preview = capturedPreview
                            val isVideoPlayButtonTap = preview?.kind == MediaKind.Video &&
                                !preview.isPlaying &&
                                isInsideCenteredSquare(
                                    offset = offset,
                                    width = size.width.toFloat(),
                                    height = size.height.toFloat(),
                                    side = 74.dp.toPx(),
                                )
                            onTap(isVideoPlayButtonTap)
                        },
                        onLongPress = { onLongPress() },
                    )
                },
        )
    }
}

@Composable
private fun ViewfinderOverlay(
    status: CaptureStatus,
    modifier: Modifier = Modifier,
) {
    val guideColor = when (status) {
        CaptureStatus.RecordingVideo -> ClaudeClay.copy(alpha = 0.88f)
        CaptureStatus.Error -> PixelError.copy(alpha = 0.72f)
        else -> ClaudeOat.copy(alpha = 0.72f)
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val cornerInset = 54.dp.toPx()
        val cornerLength = 58.dp.toPx()
        val stroke = 4.dp.toPx()

        fun corner(origin: Offset, horizontal: Float, vertical: Float) {
            val verticalEnd = origin + Offset(0f, vertical * cornerLength)
            val horizontalEnd = origin + Offset(horizontal * cornerLength, 0f)
            val path = Path().apply {
                moveTo(horizontalEnd.x, horizontalEnd.y)
                lineTo(origin.x, origin.y)
                lineTo(verticalEnd.x, verticalEnd.y)
            }
            drawPath(
                path = path,
                color = guideColor,
                style = Stroke(width = stroke),
            )
        }

        corner(Offset(cornerInset, cornerInset), 1f, 1f)
        corner(Offset(size.width - cornerInset, cornerInset), -1f, 1f)
        corner(Offset(cornerInset, size.height - cornerInset), 1f, -1f)
        corner(Offset(size.width - cornerInset, size.height - cornerInset), -1f, -1f)

        val centerSize = 64.dp.toPx()
        drawRect(
            color = guideColor,
            topLeft = Offset(size.width / 2f - centerSize / 2f, size.height / 2f - centerSize / 2f),
            size = ComposeSize(centerSize, centerSize),
            style = Stroke(width = stroke),
        )
    }
}

@Composable
private fun RecordingHud(
    startedAtMillis: Long?,
    modifier: Modifier = Modifier,
) {
    val startedAt = startedAtMillis ?: return
    var nowMillis by remember(startedAt) { mutableStateOf(SystemClock.elapsedRealtime()) }
    var blinkOn by remember(startedAt) { mutableStateOf(true) }
    LaunchedEffect(startedAt) {
        while (true) {
            nowMillis = SystemClock.elapsedRealtime()
            blinkOn = !blinkOn
            delay(500L)
        }
    }
    val dotAlpha by animateFloatAsState(
        targetValue = if (blinkOn) 1f else 0.25f,
        animationSpec = tween(durationMillis = 180),
        label = "recordingDotAlpha",
    )
    val elapsedText = PixelSnapMedia.formatElapsed(nowMillis - startedAt)
    Canvas(modifier = modifier.fillMaxSize()) {
        val radius = 7.dp.toPx()
        val gap = 14.dp.toPx()
        val y = 46.dp.toPx()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ClaudeIvoryDark.toArgb()
            textSize = 18.dp.toPx()
            typeface = Typeface.MONOSPACE
        }
        val textWidth = paint.measureText(elapsedText)
        val groupWidth = radius * 2f + gap + textWidth
        val dotCenter = Offset(size.width / 2f - groupWidth / 2f + radius, y)
        drawCircle(
            color = PixelError.copy(alpha = dotAlpha),
            radius = radius,
            center = dotCenter,
        )
        drawContext.canvas.nativeCanvas.drawText(
            elapsedText,
            dotCenter.x + radius + gap,
            dotCenter.y + 7.dp.toPx(),
            paint,
        )
    }
}

@Composable
private fun CaptureFlashOverlay(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    if (alpha <= 0.01f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(ClaudeGray050.copy(alpha = alpha))
    }
}

@Composable
private fun CapturedPreviewOverlay(
    preview: CapturedPreview,
    modifier: Modifier = Modifier,
) {
    val bitmap by rememberMediaBitmap(preview.uri, preview.kind)
    val videoAspectRatio by rememberVideoAspectRatio(preview.uri, preview.kind)
    val mediaAspectRatio = when (preview.kind) {
        MediaKind.Photo -> bitmap?.aspectRatio()
        MediaKind.Video -> videoAspectRatio ?: bitmap?.aspectRatio()
    }.orDefaultAspectRatio()

    BoxWithConstraints(
        modifier = modifier.background(ClaudeGray050),
        contentAlignment = Alignment.Center,
    ) {
        val frameSize = calculateFittedFrameSize(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            aspectRatio = mediaAspectRatio,
            minimumOuterPadding = PreviewFrameMinimumOuterPadding,
        )
        Box(
            modifier = Modifier
                .size(frameSize.width, frameSize.height)
                .clipToBounds()
                .background(Color.Black),
        ) {
            if (preview.kind == MediaKind.Video && preview.isPlaying) {
                FullScreenVideoPlayer(
                    uri = preview.uri,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val previewBitmap = bitmap
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            PreviewFrameOverlay()
            if (preview.kind == MediaKind.Video && !preview.isPlaying) {
                VideoPreviewPlayButton()
            }
        }
    }
}

private data class PreviewFrameSize(
    val width: Dp,
    val height: Dp,
)

private fun calculateFittedFrameSize(
    maxWidth: Dp,
    maxHeight: Dp,
    aspectRatio: Float,
    minimumOuterPadding: Dp,
): PreviewFrameSize {
    val availableWidth = (maxWidth - minimumOuterPadding * 2f).coerceAtLeast(1.dp)
    val availableHeight = (maxHeight - minimumOuterPadding * 2f).coerceAtLeast(1.dp)
    val availableAspectRatio = availableWidth.value / availableHeight.value
    return if (availableAspectRatio > aspectRatio) {
        PreviewFrameSize(
            width = availableHeight * aspectRatio,
            height = availableHeight,
        )
    } else {
        PreviewFrameSize(
            width = availableWidth,
            height = availableWidth / aspectRatio,
        )
    }
}

private fun ImageBitmap.aspectRatio(): Float? =
    if (width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        null
    }

private fun Float?.orDefaultAspectRatio(): Float =
    if (this != null && isFinite() && this > 0f) this else 1f

@Composable
private fun PreviewFrameOverlay(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val outerInset = 2.dp.toPx()
        val middleInset = 7.dp.toPx()
        val innerInset = 12.dp.toPx()
        val outerStroke = 2.dp.toPx()
        val innerStroke = 1.5.dp.toPx()

        fun framedRect(inset: Float, color: Color, stroke: Float) {
            val rectWidth = size.width - inset * 2f
            val rectHeight = size.height - inset * 2f
            if (rectWidth <= 0f || rectHeight <= 0f) return
            drawRect(
                color = color,
                topLeft = Offset(inset, inset),
                size = ComposeSize(rectWidth, rectHeight),
                style = Stroke(width = stroke),
            )
        }

        framedRect(outerInset, ClaudeSlateDark.copy(alpha = 0.76f), outerStroke)
        framedRect(middleInset, ClaudeClay.copy(alpha = 0.72f), innerStroke)
        framedRect(innerInset, ClaudeSlateDark.copy(alpha = 0.62f), innerStroke)
    }
}

@Composable
private fun FullScreenVideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val initialRotationDegrees = remember(uri) {
        contentResolver.loadVideoRotationDegrees(uri)
    }
    val playerState = remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(uri) {
        onDispose {
            playerState.value?.release()
            playerState.value = null
        }
    }
    AndroidView(
        factory = { viewContext ->
            FrameLayout(viewContext).apply {
                setBackgroundColor(Color.Black.toArgb())
                val textureView = AspectFitTextureView(viewContext).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    )
                    setVideoGeometry(0, 0, initialRotationDegrees)
                }
                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        val player = MediaPlayer().apply {
                            setDataSource(context, uri)
                            setSurface(Surface(surfaceTexture))
                            isLooping = true
                            setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                                textureView.setVideoGeometry(
                                    videoWidth,
                                    videoHeight,
                                    initialRotationDegrees,
                                )
                            }
                            setOnPreparedListener { mediaPlayer ->
                                textureView.setVideoGeometry(
                                    mediaPlayer.videoWidth,
                                    mediaPlayer.videoHeight,
                                    initialRotationDegrees,
                                )
                                mediaPlayer.start()
                            }
                            prepareAsync()
                        }
                        playerState.value?.release()
                        playerState.value = player
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        textureView.requestLayout()
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        playerState.value?.release()
                        playerState.value = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
                addView(textureView)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    )
}

@Composable
private fun VideoPreviewPlayButton(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = 74.dp.toPx()
        val inner = 28.dp.toPx()
        val stroke = 2.dp.toPx()
        val corner = 11.dp.toPx()

        drawRect(
            color = Color.Black.copy(alpha = 0.24f),
            topLeft = Offset(center.x - outer / 2f, center.y - outer / 2f),
            size = ComposeSize(outer, outer),
        )
        drawRect(
            color = ClaudeClay.copy(alpha = 0.82f),
            topLeft = Offset(center.x - outer / 2f, center.y - outer / 2f),
            size = ComposeSize(outer, outer),
            style = Stroke(width = stroke),
        )

        drawLine(
            color = ClaudeIvoryDark.copy(alpha = 0.72f),
            start = Offset(center.x - outer / 2f + corner, center.y - outer / 2f + corner),
            end = Offset(center.x - outer / 2f + corner + inner, center.y - outer / 2f + corner),
            strokeWidth = stroke,
        )
        drawLine(
            color = ClaudeIvoryDark.copy(alpha = 0.72f),
            start = Offset(center.x - outer / 2f + corner, center.y - outer / 2f + corner),
            end = Offset(center.x - outer / 2f + corner, center.y - outer / 2f + corner + inner),
            strokeWidth = stroke,
        )
        drawLine(
            color = ClaudeIvoryDark.copy(alpha = 0.72f),
            start = Offset(center.x + outer / 2f - corner, center.y + outer / 2f - corner),
            end = Offset(center.x + outer / 2f - corner - inner, center.y + outer / 2f - corner),
            strokeWidth = stroke,
        )
        drawLine(
            color = ClaudeIvoryDark.copy(alpha = 0.72f),
            start = Offset(center.x + outer / 2f - corner, center.y + outer / 2f - corner),
            end = Offset(center.x + outer / 2f - corner, center.y + outer / 2f - corner - inner),
            strokeWidth = stroke,
        )

        val play = Path().apply {
            moveTo(center.x - 9.dp.toPx(), center.y - 16.dp.toPx())
            lineTo(center.x - 9.dp.toPx(), center.y + 16.dp.toPx())
            lineTo(center.x + 17.dp.toPx(), center.y)
            close()
        }
        drawPath(
            path = play,
            color = ClaudeClay.copy(alpha = 0.92f),
        )
    }
}

private fun isInsideCenteredSquare(
    offset: Offset,
    width: Float,
    height: Float,
    side: Float,
): Boolean {
    val halfSide = side / 2f
    val centerX = width / 2f
    val centerY = height / 2f
    return offset.x in (centerX - halfSide)..(centerX + halfSide) &&
        offset.y in (centerY - halfSide)..(centerY + halfSide)
}

@Composable
private fun rememberMediaBitmap(uri: Uri, kind: MediaKind): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri, kind) {
        value = withContext(Dispatchers.IO) {
            context.contentResolver.loadPreviewBitmap(uri, kind)?.asImageBitmap()
        }
    }
}

@Composable
private fun rememberVideoAspectRatio(uri: Uri, kind: MediaKind): State<Float?> {
    val context = LocalContext.current
    return produceState<Float?>(initialValue = null, uri, kind) {
        value = if (kind == MediaKind.Video) {
            withContext(Dispatchers.IO) {
                context.contentResolver.loadVideoAspectRatio(uri)
            }
        } else {
            null
        }
    }
}

@Composable
private fun PermissionPanel(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onRequestPermission() })
            },
    ) {
        ViewfinderOverlay(status = CaptureStatus.Error)
    }
}

private fun MediaSpec.toContentValues(isPending: Boolean? = null): ContentValues =
    ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.DATE_TAKEN, capturedAtMillis)
        isPending?.let { pending ->
            put(MediaStore.MediaColumns.IS_PENDING, if (pending) 1 else 0)
        }
    }

private fun ContentResolver.saveWatermarkedPhoto(
    rawFile: File,
    spec: MediaSpec,
): Uri {
    val uri = insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        spec.toContentValues(isPending = true),
    ) ?: error("Unable to create PixelSnap photo.")

    var outputBitmap: Bitmap? = null
    try {
        outputBitmap = rawFile.decodeExifBitmap().drawPixelSnapWatermark(spec.capturedAtMillis)
        openOutputStream(uri, "w")?.use { outputStream ->
            check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, 94, outputStream)) {
                "Unable to write PixelSnap photo."
            }
        } ?: error("Unable to open PixelSnap photo output.")

        val readyValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        check(update(uri, readyValues, null, null) > 0) {
            "Unable to finalize PixelSnap photo."
        }
        return uri
    } catch (error: Throwable) {
        runCatching { delete(uri, null, null) }
        throw error
    } finally {
        outputBitmap?.recycle()
    }
}

private fun File.decodeExifBitmap(): Bitmap {
    val bitmap = BitmapFactory.decodeFile(absolutePath)
        ?: error("Unable to decode PixelSnap photo.")
    val orientation = ExifInterface(absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(-90f)
                preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
        }
    }
    if (matrix.isIdentity) return bitmap

    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true,
    ).also {
        bitmap.recycle()
    }
}

private fun Bitmap.drawPixelSnapWatermark(capturedAtMillis: Long): Bitmap {
    val output = if (config == Bitmap.Config.ARGB_8888 && isMutable) {
        this
    } else {
        copy(Bitmap.Config.ARGB_8888, true).also { recycle() }
    }
    val canvas = AndroidCanvas(output)
    val shortSide = min(output.width, output.height).toFloat().coerceAtLeast(1f)
    val margin = (shortSide * 0.035f).coerceIn(24f, 72f)
    val titleSize = (shortSide * 0.032f).coerceIn(24f, 64f)
    val metaSize = (titleSize * 0.62f).coerceIn(16f, 40f)
    val lineGap = titleSize * 0.3f
    val date = PixelSnapMedia.formatCaptureDate(capturedAtMillis)
    val title = "PixelSnap"
    val meta = "CODEX & XUE \u00B7 $date"

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidGraphicsColor.argb(232, 250, 249, 245)
        textAlign = Paint.Align.RIGHT
        textSize = titleSize
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidGraphicsColor.argb(214, 232, 230, 220)
        textAlign = Paint.Align.RIGHT
        textSize = metaSize
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    val titleStrokePaint = Paint(titlePaint).apply {
        color = AndroidGraphicsColor.argb(132, 20, 20, 19)
        style = Paint.Style.STROKE
        strokeWidth = (titleSize * 0.09f).coerceAtLeast(2f)
        strokeJoin = Paint.Join.MITER
    }
    val metaStrokePaint = Paint(metaPaint).apply {
        color = AndroidGraphicsColor.argb(128, 20, 20, 19)
        style = Paint.Style.STROKE
        strokeWidth = (metaSize * 0.12f).coerceAtLeast(2f)
        strokeJoin = Paint.Join.MITER
    }

    val x = output.width - margin
    val metaBaseline = output.height - margin
    val titleBaseline = metaBaseline - metaSize - lineGap
    canvas.drawText(title, x, titleBaseline, titleStrokePaint)
    canvas.drawText(title, x, titleBaseline, titlePaint)
    canvas.drawText(meta, x, metaBaseline, metaStrokePaint)
    canvas.drawText(meta, x, metaBaseline, metaPaint)
    return output
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
        continuation.invokeOnCancellation { future.cancel(true) }
    }

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> error("PixelSnap requires an Activity context.")
    }

private fun Context.findLifecycleOwner(): LifecycleOwner =
    findActivity() as? LifecycleOwner
        ?: error("PixelSnap requires a lifecycle-aware Activity.")

private fun android.content.ContentResolver.loadPreviewBitmap(uri: Uri, kind: MediaKind): Bitmap? =
    runCatching {
        when (kind) {
            MediaKind.Photo -> ImageDecoder.decodeBitmap(ImageDecoder.createSource(this, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }

            MediaKind.Video -> loadVideoPreviewFrame(uri)
                ?: loadThumbnail(uri, Size(4096, 4096), null)
        }
    }.getOrNull()

private fun android.content.ContentResolver.loadVideoPreviewFrame(uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        openFileDescriptor(uri, "r")?.use { descriptor ->
            retriever.setDataSource(descriptor.fileDescriptor)
            val rotationDegrees = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,
            )?.toIntOrNull().orZeroRightAngleDegrees()
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            frame?.rotateRightAngle(rotationDegrees)
        }
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun android.content.ContentResolver.loadVideoRotationDegrees(uri: Uri): Int {
    val retriever = MediaMetadataRetriever()
    return try {
        openFileDescriptor(uri, "r")?.use { descriptor ->
            retriever.setDataSource(descriptor.fileDescriptor)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                .orZeroRightAngleDegrees()
        } ?: 0
    } catch (_: Exception) {
        0
    } finally {
        retriever.release()
    }
}

private fun android.content.ContentResolver.loadVideoAspectRatio(uri: Uri): Float? {
    val retriever = MediaMetadataRetriever()
    return try {
        openFileDescriptor(uri, "r")?.use { descriptor ->
            retriever.setDataSource(descriptor.fileDescriptor)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: return@use null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: return@use null
            val rotationDegrees = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,
            )?.toIntOrNull().orZeroRightAngleDegrees()
            val displayWidth = if (rotationDegrees.isQuarterTurn()) height else width
            val displayHeight = if (rotationDegrees.isQuarterTurn()) width else height
            if (displayWidth > 0 && displayHeight > 0) {
                displayWidth.toFloat() / displayHeight.toFloat()
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun Bitmap.rotateRightAngle(rotationDegrees: Int): Bitmap {
    val normalizedRotation = rotationDegrees.normalizedRightAngleDegrees()
    if (normalizedRotation == 0) return this
    val rotated = Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        Matrix().apply { postRotate(normalizedRotation.toFloat()) },
        true,
    )
    recycle()
    return rotated
}

private fun Int?.orZeroRightAngleDegrees(): Int =
    this?.normalizedRightAngleDegrees() ?: 0

private fun Int.normalizedRightAngleDegrees(): Int {
    val normalized = ((this % 360) + 360) % 360
    return when (normalized) {
        90, 180, 270 -> normalized
        else -> 0
    }
}

private fun Int.isQuarterTurn(): Boolean =
    this == 90 || this == 270

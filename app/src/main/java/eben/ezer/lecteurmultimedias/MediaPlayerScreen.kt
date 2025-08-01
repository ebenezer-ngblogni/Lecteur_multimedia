@file:OptIn(ExperimentalAnimationApi::class)

package eben.ezer.lecteurmultimedias

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MediaPlayerScreen(
    currentUser: User?,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var currentMediaUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var isAudio by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var snackbarMessage by remember { mutableStateOf("") }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var videoAspectRatio by remember { mutableStateOf(16f / 9f) } // Ratio par défaut
    val snackbarHostState = remember { SnackbarHostState() }

    // Animations
    val playButtonScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "playButtonScale"
    )

    val waveAnimation = rememberInfiniteTransition(label = "waveAnimation")
    val waveScale by waveAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    // Gestionnaire de permissions
    val permissionManager = rememberPermissionHandler(context) { granted ->
        if (granted) {
            snackbarMessage = "Permissions accordées"
        } else {
            snackbarMessage = "Permissions requises pour accéder aux fichiers"
            showPermissionDialog = true
        }
    }

    // Launcher pour sélectionner un fichier
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            loadMedia(
                context = context,
                uri = it,
                onMediaLoaded = { name, isAudioFile, player, video, aspectRatio ->
                    currentMediaUri = it
                    fileName = name
                    isAudio = isAudioFile
                    mediaPlayer = player
                    videoView = video
                    videoAspectRatio = aspectRatio
                    isPlaying = false
                },
                onError = { message ->
                    snackbarMessage = message
                }
            )
        }
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(snackbarMessage)
            snackbarMessage = ""
        }
    }

    // Nettoyage à la fermeture
    DisposableEffect(Unit) {
        onDispose {
            stopMedia(mediaPlayer, videoView) {
                mediaPlayer = null
                videoView = null
                isPlaying = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🎵 Bienvenue, ${currentUser?.username ?: "Utilisateur"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            stopMedia(mediaPlayer, videoView) {
                                mediaPlayer = null
                                videoView = null
                                isPlaying = false
                            }
                            onLogout()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExitToApp,
                            contentDescription = "Déconnexion",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    ).let { MaterialTheme.colorScheme.primary },
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // Zone de lecture vidéo ou visualiseur audio adaptative
                AnimatedContent(
                    targetState = currentMediaUri != null,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) with fadeOut(animationSpec = tween(500))
                    },
                    label = "mediaContent"
                ) { hasMedia ->
                    if (hasMedia && !isAudio && currentMediaUri != null) {
                        // Lecteur vidéo adaptatif
                        AdaptiveVideoPlayer(
                            videoView = videoView,
                            currentMediaUri = currentMediaUri,
                            aspectRatio = videoAspectRatio,
                            onVideoViewChanged = { videoView = it },
                            onError = { snackbarMessage = it },
                            onPlayingChanged = { isPlaying = it }
                        )
                    } else {
                        // Visualiseur audio moderne
                        AudioVisualizer(
                            isAudio = isAudio,
                            isPlaying = isPlaying,
                            waveScale = waveScale,
                            currentMediaUri = currentMediaUri
                        )
                    }
                }

                // Informations sur le fichier
                AnimatedVisibility(
                    visible = fileName.isNotEmpty(),
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    MediaInfoCard(
                        fileName = fileName,
                        isAudio = isAudio,
                        isPlaying = isPlaying
                    )
                }

                // Espacement flexible pour pousser les contrôles vers le bas
                Spacer(modifier = Modifier.height(16.dp))

                // Contrôles de lecture modernes
                MediaControlsCard(
                    currentMediaUri = currentMediaUri,
                    isAudio = isAudio,
                    isPlaying = isPlaying,
                    playButtonScale = playButtonScale,
                    mediaPlayer = mediaPlayer,
                    videoView = videoView,
                    onSelectFile = {
                        if (permissionManager.hasPermissions()) {
                            filePickerLauncher.launch("*/*")
                        } else {
                            permissionManager.requestPermissions()
                        }
                    },
                    onTogglePlayPause = {
                        togglePlayPause(
                            isAudio = isAudio,
                            isPlaying = isPlaying,
                            mediaPlayer = mediaPlayer,
                            videoView = videoView,
                            onPlayingChanged = { isPlaying = it }
                        )
                    },
                    onStop = {
                        stopMedia(mediaPlayer, videoView) {
                            isPlaying = false
                            fileName = ""
                            currentMediaUri = null
                            mediaPlayer = null
                            videoView = null
                        }
                    }
                )
            }
        }
    }

    // Dialog pour les permissions
    if (showPermissionDialog) {
        PermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onOpenSettings = {
                showPermissionDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    permissionManager.permissionHandler.openManageStorageSettings(context)
                } else {
                    permissionManager.permissionHandler.openAppSettings(context)
                }
            }
        )
    }
}

@Composable
fun AdaptiveVideoPlayer(
    videoView: VideoView?,
    currentMediaUri: Uri?,
    aspectRatio: Float,
    onVideoViewChanged: (VideoView) -> Unit,
    onError: (String) -> Unit,
    onPlayingChanged: (Boolean) -> Unit
) {
    // Calcul intelligent de la taille du lecteur vidéo
    val maxWidth = 350.dp
    val maxHeight = 300.dp // Hauteur maximale pour éviter que les boutons disparaissent

    val (videoWidth, videoHeight) = remember(aspectRatio) {
        when {
            aspectRatio >= 1.5f -> {
                // Format paysage (16:9, etc.)
                val height = (maxWidth.value / aspectRatio).dp.coerceAtMost(maxHeight)
                maxWidth to height
            }
            aspectRatio <= 0.7f -> {
                // Format portrait très étroit (9:16, etc.)
                val width = (maxHeight.value * aspectRatio).dp.coerceAtMost(maxWidth)
                width to maxHeight
            }
            else -> {
                // Format carré ou proche du carré
                val size = minOf(maxWidth.value, maxHeight.value).dp
                size to size
            }
        }
    }

    Card(
        modifier = Modifier
            .width(videoWidth)
            .height(videoHeight),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    onVideoViewChanged(this)
                    // Configuration pour maintenir le ratio d'aspect
                    setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        onError("Vidéo prête à être lue")
                    }
                    setOnCompletionListener {
                        onPlayingChanged(false)
                    }
                    setOnErrorListener { _, what, extra ->
                        onError("Erreur de lecture vidéo: $what, $extra")
                        true
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black),
            update = { view ->
                if (view != videoView) {
                    onVideoViewChanged(view)
                    currentMediaUri?.let { uri ->
                        view.setVideoURI(uri)
                    }
                }
            }
        )
    }
}

@Composable
fun AudioVisualizer(
    isAudio: Boolean,
    isPlaying: Boolean,
    waveScale: Float,
    currentMediaUri: Uri?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Effet d'onde pour la musique
            if (isAudio && isPlaying) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size((80 + index * 40).dp)
                            .scale(if (isPlaying) waveScale - index * 0.1f else 1f)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.1f - index * 0.03f
                                ),
                                CircleShape
                            )
                    )
                }
            }

            // Icône principale
            Icon(
                imageVector = when {
                    currentMediaUri == null -> Icons.Rounded.QueueMusic
                    isAudio -> Icons.Rounded.MusicNote
                    else -> Icons.Rounded.VideoFile
                },
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MediaInfoCard(
    fileName: String,
    isAudio: Boolean,
    isPlaying: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isAudio) Icons.Rounded.AudioFile else Icons.Rounded.VideoFile,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isAudio) "Fichier Audio" else "Fichier Vidéo",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Indicateur de statut
            AnimatedVisibility(visible = isPlaying) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "En lecture",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MediaControlsCard(
    currentMediaUri: Uri?,
    isAudio: Boolean,
    isPlaying: Boolean,
    playButtonScale: Float,
    mediaPlayer: MediaPlayer?,
    videoView: VideoView?,
    onSelectFile: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Bouton principal de sélection
            ModernButton(
                onClick = onSelectFile,
                icon = Icons.Rounded.FolderOpen,
                text = "Sélectionner un fichier",
                isPrimary = true
            )

            // Contrôles de lecture
            AnimatedVisibility(
                visible = currentMediaUri != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bouton Play/Pause
                    ModernIconButton(
                        onClick = onTogglePlayPause,
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Lecture",
                        scale = playButtonScale,
                        isPrimary = true
                    )

                    // Bouton Stop
                    ModernIconButton(
                        onClick = onStop,
                        icon = Icons.Rounded.Stop,
                        contentDescription = "Arrêter",
                        isPrimary = false
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Permissions requises",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text("Cette application a besoin d'accéder à vos fichiers multimédias pour fonctionner correctement.")

                if (PermissionHandler.isMIUI()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "⚠️ Appareil MIUI détecté",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sur les appareils Xiaomi/Redmi, vous devez aller dans Paramètres > Autorisations pour accorder l'accès aux fichiers.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Ouvrir Paramètres")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Plus tard")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ModernButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = if (isPrimary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ModernIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    scale: Float = 1f,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .scale(scale),
        shape = CircleShape,
        containerColor = if (isPrimary) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondary
        },
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(28.dp),
            tint = if (isPrimary) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondary
            }
        )
    }
}

// Fonctions utilitaires améliorées
private fun loadMedia(
    context: android.content.Context,
    uri: Uri,
    onMediaLoaded: (String, Boolean, MediaPlayer?, VideoView?, Float) -> Unit,
    onError: (String) -> Unit
) {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri)

    val fileName = getFileName(context, uri)

    when {
        mimeType?.startsWith("audio/") == true -> {
            try {
                val mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    prepareAsync()
                    setOnPreparedListener {
                        onError("Audio prêt à être lu")
                    }
                    setOnCompletionListener {
                        // isPlaying sera géré dans l'UI
                    }
                }
                onMediaLoaded(fileName, true, mediaPlayer, null, 1f)
            } catch (e: IOException) {
                onError("Erreur lors du chargement de l'audio")
            }
        }
        mimeType?.startsWith("video/") == true -> {
            // Récupérer les dimensions de la vidéo pour calculer l'aspect ratio
            val aspectRatio = getVideoAspectRatio(context, uri)
            onMediaLoaded(fileName, false, null, null, aspectRatio)
        }
        else -> {
            onError("Format de fichier non supporté")
        }
    }
}

private fun getVideoAspectRatio(context: android.content.Context, uri: Uri): Float {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(context, uri)

        val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 16f
        val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 9f

        retriever.release()

        width / height
    } catch (e: Exception) {
        16f / 9f // Ratio par défaut en cas d'erreur
    }
}

private fun togglePlayPause(
    isAudio: Boolean,
    isPlaying: Boolean,
    mediaPlayer: MediaPlayer?,
    videoView: VideoView?,
    onPlayingChanged: (Boolean) -> Unit
) {
    if (isAudio) {
        mediaPlayer?.let { player ->
            if (isPlaying) {
                player.pause()
                onPlayingChanged(false)
            } else {
                player.start()
                onPlayingChanged(true)
            }
        }
    } else {
        videoView?.let { view ->
            if (isPlaying) {
                view.pause()
                onPlayingChanged(false)
            } else {
                view.start()
                onPlayingChanged(true)
            }
        }
    }
}

private fun stopMedia(
    mediaPlayer: MediaPlayer?,
    videoView: VideoView?,
    onStopped: () -> Unit
) {
    mediaPlayer?.let {
        if (it.isPlaying) {
            it.stop()
        }
        it.release()
    }
    videoView?.stopPlayback()
    onStopped()
}

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var fileName = "Fichier inconnu"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
    }
    return fileName
}
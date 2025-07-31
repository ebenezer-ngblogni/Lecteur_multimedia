package eben.ezer.lecteurmultimedias

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.widget.VideoView
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlayerScreen(
    currentUser: User?,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var currentMediaUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Aucun fichier sélectionné") }
    var isAudio by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var snackbarMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Launcher pour sélectionner un fichier
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            loadMedia(
                context = context,
                uri = it,
                onMediaLoaded = { name, isAudioFile, player, video ->
                    currentMediaUri = it
                    fileName = name
                    isAudio = isAudioFile
                    mediaPlayer = player
                    videoView = video
                    isPlaying = false
                },
                onError = { message ->
                    snackbarMessage = message
                }
            )
        }
    }

    // Launcher pour les permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            filePickerLauncher.launch("*/*")
        } else {
            snackbarMessage = "Permission requise pour accéder aux fichiers"
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
                        text = "Bienvenue, ${currentUser?.username ?: "Utilisateur"}",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = {
                        stopMedia(mediaPlayer, videoView) {
                            mediaPlayer = null
                            videoView = null
                            isPlaying = false
                        }
                        onLogout()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Déconnexion"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zone de lecture vidéo
            if (!isAudio && currentMediaUri != null) {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            videoView = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) { view ->
                    if (view != videoView) {
                        videoView = view
                        currentMediaUri?.let { uri ->
                            view.setVideoURI(uri)
                            view.setOnPreparedListener {
                                snackbarMessage = "Vidéo prête à être lue"
                            }
                            view.setOnCompletionListener {
                                isPlaying = false
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Informations sur le fichier
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.VideoFile,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = fileName,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Boutons de contrôle
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED) {
                            filePickerLauncher.launch("*/*")
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sélectionner")
                }

                Button(
                    onClick = {
                        togglePlayPause(
                            isAudio = isAudio,
                            isPlaying = isPlaying,
                            mediaPlayer = mediaPlayer,
                            videoView = videoView,
                            onPlayingChanged = { isPlaying = it }
                        )
                    },
                    enabled = currentMediaUri != null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPlaying) "Pause" else "Lecture")
                }

                Button(
                    onClick = {
                        stopMedia(mediaPlayer, videoView) {
                            isPlaying = false
                            fileName = "Aucun fichier sélectionné"
                            currentMediaUri = null
                            mediaPlayer = null
                            videoView = null
                        }
                    },
                    enabled = currentMediaUri != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Arrêter")
                }
            }
        }
    }
}

private fun loadMedia(
    context: android.content.Context,
    uri: Uri,
    onMediaLoaded: (String, Boolean, MediaPlayer?, VideoView?) -> Unit,
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
                onMediaLoaded(fileName, true, mediaPlayer, null)
            } catch (e: IOException) {
                onError("Erreur lors du chargement de l'audio")
            }
        }
        mimeType?.startsWith("video/") == true -> {
            onMediaLoaded(fileName, false, null, null)
        }
        else -> {
            onError("Format de fichier non supporté")
        }
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
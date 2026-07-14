@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.kamux.ultimate

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items

sealed interface Screen {
    object Home : Screen
    object Search : Screen
    data class AlbumDetail(val browseId: String) : Screen
    data class ArtistDetail(val browseId: String) : Screen
}

@Composable
fun KamuxApp(player: Player?, viewModel: KamuxPlayerViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var homeState by remember { mutableStateOf<HomeUiState>(HomeUiState.Loading) }
    var isFullScreenPlayerVisible by remember { mutableStateOf(false) }
    var currentQueue by remember { mutableStateOf<List<QueueTrack>>(emptyList()) }
    var currentMediaId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isQueueLoading by remember { mutableStateOf(false) }

    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Home

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
    }

    fun navigateTo(screen: Screen) {
        backStack.add(screen)
    }

    LaunchedEffect(player) {
        player?.let { viewModel.bindPlayer(it) }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaId = mediaItem?.mediaId
                viewModel.resetLyrics()
            }
            override fun onIsPlayingChanged(isPlayingStatus: Boolean) {
                isPlaying = isPlayingStatus
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    val activeTrack = currentQueue.find { it.videoId == currentMediaId }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val response = KamuxApiService.instance.getHome()
                homeState = if (response.status == "success" && response.data.isNotEmpty()) {
                    HomeUiState.Success(response.data)
                } else {
                    HomeUiState.Error("No se encontraron secciones.")
                }
            } catch (e: Exception) {
                homeState = HomeUiState.Error("Error de red: ${e.message}")
            }
        }
    }

    val handleTrackSelection: (TrackItem) -> Unit = { selectedTrack ->
        val safeVideoId = selectedTrack.videoId
        val safePlaylistId = selectedTrack.playlistId
        val safeBrowseId = selectedTrack.browseId

        if (safeVideoId.isNullOrEmpty() && !safeBrowseId.isNullOrEmpty()) {
            navigateTo(Screen.AlbumDetail(safeBrowseId))
        } else if (safeVideoId.isNullOrEmpty() && safePlaylistId.isNullOrEmpty()) {
            Toast.makeText(context, "Pista no disponible", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                isQueueLoading = true
                try {
                    val queueResponse = KamuxApiService.instance.getQueue(videoId = safeVideoId, playlistId = safePlaylistId)
                    if (queueResponse.status == "success" && queueResponse.tracks.isNotEmpty()) {
                        currentQueue = queueResponse.tracks
                        val mediaItems = currentQueue.mapNotNull { qTrack ->
                            qTrack.videoId?.let { vId ->
                                val metadata = androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(qTrack.title)
                                    .setArtist(qTrack.artists?.firstOrNull()?.name)
                                    .setArtworkUri(android.net.Uri.parse(qTrack.thumbnail?.firstOrNull()?.url ?: ""))
                                    .build()

                                MediaItem.Builder()
                                    .setMediaId(vId)
                                    .setMediaMetadata(metadata)
                                    .build()
                            }
                        }

                        if (player != null) {
                            player.stop()
                            player.clearMediaItems()
                            player.setMediaItems(mediaItems)
                            player.prepare()
                            player.play()
                        } else {
                            Toast.makeText(context, "Motor desconectado", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Fallo al crear radio", Toast.LENGTH_LONG).show()
                } finally {
                    isQueueLoading = false
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                is Screen.Home -> {
                    HomeScreen(
                        state = homeState,
                        onTrackClick = handleTrackSelection,
                        onSearchNavigate = { navigateTo(Screen.Search) }
                    )
                }
                is Screen.Search -> {
                    SearchScreen(
                        viewModel = viewModel,
                        onTrackClick = handleTrackSelection,
                        onAlbumClick = { navigateTo(Screen.AlbumDetail(it)) },
                        onArtistClick = { navigateTo(Screen.ArtistDetail(it)) }
                    )
                }
                is Screen.AlbumDetail -> {
                    AlbumDetailScreen(
                        browseId = (currentScreen as Screen.AlbumDetail).browseId,
                        viewModel = viewModel,
                        onTrackClick = handleTrackSelection,
                        onPlayAlbumComplete = { albumTracks, albumDetails ->
                            scope.launch {
                                isQueueLoading = true
                                currentQueue = albumTracks.map { track ->
                                    QueueTrack(
                                        videoId = track.videoId,
                                        title = track.title,
                                        length = track.duration,
                                        thumbnail = albumDetails.thumbnails,
                                        artists = albumDetails.artists
                                    )
                                }
                                val mediaItems = currentQueue.mapNotNull { qTrack ->
                                    qTrack.videoId?.let { vId ->
                                        val metadata = androidx.media3.common.MediaMetadata.Builder()
                                            .setTitle(qTrack.title)
                                            .setArtist(qTrack.artists?.firstOrNull()?.name)
                                            .setArtworkUri(android.net.Uri.parse(qTrack.thumbnail?.firstOrNull()?.url ?: ""))
                                            .build()

                                        androidx.media3.common.MediaItem.Builder()
                                            .setMediaId(vId)
                                            .setMediaMetadata(metadata)
                                            .build()
                                    }
                                }
                                player?.let { p ->
                                    p.stop()
                                    p.clearMediaItems()
                                    p.setMediaItems(mediaItems)
                                    p.prepare()
                                    p.play()
                                }
                                isQueueLoading = false
                            }
                        }
                    )
                }
                is Screen.ArtistDetail -> {
                    ArtistDetailScreen(
                        browseId = (currentScreen as Screen.ArtistDetail).browseId,
                        viewModel = viewModel,
                        onTrackClick = handleTrackSelection,
                        onAlbumClick = { navigateTo(Screen.AlbumDetail(it)) }
                    )
                }
            }
        }

        if (activeTrack != null && !isFullScreenPlayerVisible) {
            MiniPlayer(
                track = activeTrack, isPlaying = isPlaying, isLoading = isQueueLoading,
                onPlayPauseClick = { if (isPlaying) player?.pause() else player?.play() },
                modifier = Modifier.align(Alignment.BottomCenter).clickable { isFullScreenPlayerVisible = true }
            )
        }

        AnimatedVisibility(
            visible = isFullScreenPlayerVisible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
            modifier = Modifier.zIndex(10f)
        ) {
            activeTrack?.let { track ->
                FullScreenPlayer(
                    track = track,
                    queue = currentQueue,
                    isPlaying = isPlaying,
                    player = player,
                    viewModel = viewModel,
                    onCloseClick = { isFullScreenPlayerVisible = false }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(state: HomeUiState, onTrackClick: (TrackItem) -> Unit, onSearchNavigate: () -> Unit) {
    when (state) {
        is HomeUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = KamuxAccent) }
        is HomeUiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 24.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kamux Ultimate", color = KamuxTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        IconButton(onClick = onSearchNavigate, modifier = Modifier.background(KamuxSurface, CircleShape)) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = KamuxTextPrimary)
                        }
                    }
                }
                items(state.sections) { section ->
                    if (section.title.lowercase().contains("quick") || section.title.lowercase().contains("rápida")) {
                        QuickPicksSection(section, onTrackClick)
                    } else {
                        ListenAgainSection(section, onTrackClick)
                    }
                }
            }
        }
        is HomeUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.message, color = Color.Red) }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    viewModel: KamuxPlayerViewModel,
    onTrackClick: (TrackItem) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val suggestions = viewModel.searchSuggestions
    val results = viewModel.searchResults
    val isLoading = viewModel.isSearchLoading
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.updateSearchQuery(it)
            },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            placeholder = { Text("¿Qué deseas escuchar?", color = KamuxTextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = KamuxAccent) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; viewModel.clearSearch() }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = KamuxTextPrimary)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = KamuxSurface, unfocusedContainerColor = KamuxSurface,
                focusedTextColor = KamuxTextPrimary, unfocusedTextColor = KamuxTextPrimary,
                cursorColor = KamuxAccent
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                viewModel.performGlobalSearch(query)
                keyboardController?.hide()
            })
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = KamuxAccent)
            } else if (results != null) {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(results) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                when (item.resultType) {
                                    "song", "video" -> onTrackClick(TrackItem(videoId = item.videoId, playlistId = null, browseId = null, title = item.title, artists = item.artists, thumbnails = item.thumbnails))
                                    "album" -> item.browseId?.let { onAlbumClick(it) }
                                    "artist" -> item.browseId?.let { onArtistClick(it) }
                                }
                            }.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.thumbnails?.firstOrNull()?.url, contentDescription = null,
                                modifier = Modifier.size(56.dp).clip(if (item.resultType == "artist") CircleShape else RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 16.dp, end = 16.dp)) {
                                Text(item.title ?: item.artistName ?: "Desconocido", color = KamuxTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val subText = when (item.resultType) {
                                    "song" -> "Canción • ${item.artists?.firstOrNull()?.name ?: ""}"
                                    "video" -> "Video • ${item.artists?.firstOrNull()?.name ?: ""}"
                                    "album" -> "Álbum"
                                    "artist" -> "Artista"
                                    else -> item.resultType
                                }
                                Text(subText, color = KamuxTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else if (suggestions.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(suggestions) { text ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                query = text
                                viewModel.performGlobalSearch(text)
                                keyboardController?.hide()
                            }.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = KamuxTextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = text, color = KamuxTextPrimary, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(
    browseId: String,
    viewModel: KamuxPlayerViewModel,
    onTrackClick: (TrackItem) -> Unit,
    onPlayAlbumComplete: (List<AlbumTrack>, AlbumDetails) -> Unit
) {
    val album = viewModel.currentAlbum
    val isLoading = viewModel.isDetailLoading

    LaunchedEffect(browseId) { viewModel.fetchAlbumDetails(browseId) }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = KamuxAccent) }
    } else album?.let { details ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = details.thumbnails?.lastOrNull()?.url, contentDescription = null, modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(details.title, color = KamuxTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Álbum • ${details.year ?: ""}", color = KamuxTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))

                    if (!details.tracks.isNullOrEmpty()) {
                        Button(
                            onClick = {
                                onPlayAlbumComplete(details.tracks, details)
                            },
                            modifier = Modifier.padding(top = 24.dp).height(50.dp).width(220.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KamuxAccent)
                        ) {
                            Text("REPRODUCIR ÁLBUM", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            itemsIndexed(details.tracks) { index, track ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onTrackClick(TrackItem(videoId = track.videoId, playlistId = null, browseId = null, title = track.title, artists = details.artists, thumbnails = details.thumbnails))
                    }.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}", color = KamuxTextSecondary, fontSize = 14.sp, modifier = Modifier.width(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, color = KamuxTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(track.duration ?: "0:00", color = KamuxTextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(browseId: String, viewModel: KamuxPlayerViewModel, onTrackClick: (TrackItem) -> Unit, onAlbumClick: (String) -> Unit) {
    val artist = viewModel.currentArtist
    val isLoading = viewModel.isDetailLoading
    val allAlbums = viewModel.artistAllAlbums
    val isFetchingAllAlbums = viewModel.isAllAlbumsLoading

    LaunchedEffect(browseId) { viewModel.fetchArtistDetails(browseId) }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = KamuxAccent) }
    } else artist?.let { details ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = details.thumbnails?.lastOrNull()?.url, contentDescription = null, modifier = Modifier.size(140.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(details.name, color = KamuxTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${details.subscribers ?: "0"} suscriptores", color = KamuxTextSecondary, fontSize = 14.sp)
                }
            }

            if (!details.songs?.results.isNullOrEmpty()) {
                item { Text("Canciones populares", color = KamuxTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) }
                items(details.songs!!.results) { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onTrackClick(TrackItem(videoId = track.videoId, playlistId = null, browseId = null, title = track.title, artists = track.artists, thumbnails = track.thumbnail))
                        }.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = track.thumbnail?.firstOrNull()?.url, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                        Column(modifier = Modifier.weight(1f).padding(start = 16.dp, end = 16.dp)) {
                            Text(track.title ?: "", color = KamuxTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(track.length ?: "", color = KamuxTextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (!details.albums?.results.isNullOrEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Álbumes", color = KamuxTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                        if (details.albums?.params != null && details.albums?.browseId != null && allAlbums == null) {
                            if (isFetchingAllAlbums) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = KamuxAccent, strokeWidth = 2.dp)
                            } else {
                                Text("Ver todos", color = KamuxAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                    viewModel.fetchAllArtistAlbums(details.albums.browseId, details.albums.params)
                                }.padding(4.dp))
                            }
                        }
                    }
                }
                item {
                    val albumsToShow = allAlbums ?: details.albums!!.results
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(albumsToShow) { album ->
                            Column(modifier = Modifier.width(140.dp).clickable { album.browseId?.let { onAlbumClick(it) } }) {
                                AsyncImage(model = album.thumbnails?.lastOrNull()?.url, contentDescription = null, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                Text(album.title, color = KamuxTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                Text(album.year ?: "", color = KamuxTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenPlayer(
    track: QueueTrack,
    queue: List<QueueTrack>,
    isPlaying: Boolean,
    player: Player?,
    onCloseClick: () -> Unit,
    viewModel: KamuxPlayerViewModel
) {
    var expandedPanel by remember { mutableStateOf("PLAYER") }

    val currentPositionMs = viewModel.currentPositionMs
    val durationMs = viewModel.durationMs
    val lyricsState = viewModel.lyricsState

    LaunchedEffect(expandedPanel) {
        if (expandedPanel == "LYRICS" && lyricsState is LyricsState.Idle) {
            val artist = track.artists?.firstOrNull()?.name ?: ""
            viewModel.fetchLyrics(track.title, artist)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(KamuxBackground)) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 32.dp)) {
            IconButton(onClick = onCloseClick, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimizar", tint = KamuxTextPrimary, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            AsyncImage(
                model = track.thumbnail?.lastOrNull()?.url, contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text(text = track.title ?: "Desconocido", color = KamuxTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = track.artists?.firstOrNull()?.name ?: "Unknown", color = KamuxTextSecondary, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(32.dp))
            Slider(
                value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                onValueChange = { newPercent ->
                    val seekPosMs = (newPercent * durationMs).toLong()
                    viewModel.seekTo(player, seekPosMs)
                },
                colors = SliderDefaults.colors(thumbColor = KamuxAccent, activeTrackColor = KamuxAccent, inactiveTrackColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                fun formatTime(ms: Long): String {
                    if (ms <= 0) return "0:00"
                    val totalSeconds = ms / 1000
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    return String.format("%d:%02d", minutes, seconds)
                }
                Text(formatTime(currentPositionMs), color = KamuxTextSecondary, fontSize = 12.sp)
                Text(formatTime(durationMs), color = KamuxTextSecondary, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { player?.seekToPreviousMediaItem() }, colors = ButtonDefaults.buttonColors(Color.Transparent)) {
                    Text("⏮", color = KamuxTextPrimary, fontSize = 36.sp)
                }
                Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(KamuxAccent).clickable { if (isPlaying) player?.pause() else player?.play() }, contentAlignment = Alignment.Center) {
                    Text(if (isPlaying) "⏸" else "▶", color = Color.Black, fontSize = 40.sp)
                }
                Button(onClick = { player?.seekToNextMediaItem() }, colors = ButtonDefaults.buttonColors(Color.Transparent)) {
                    Text("⏭", color = KamuxTextPrimary, fontSize = 36.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Text(text = "A CONTINUACIÓN", color = KamuxTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { expandedPanel = "QUEUE" }.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp))
                Text(text = "LETRA", color = KamuxTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { expandedPanel = "LYRICS" }.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp))
            }
        }

        AnimatedVisibility(
            visible = expandedPanel == "QUEUE" || expandedPanel == "LYRICS",
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
            modifier = Modifier.zIndex(5f)
        ) {
            Column(modifier = Modifier.fillMaxSize().background(KamuxSurface).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = track.thumbnail?.firstOrNull()?.url, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp)) {
                        Text(track.title ?: "", color = KamuxTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artists?.firstOrNull()?.name ?: "", color = KamuxTextSecondary, fontSize = 14.sp, maxLines = 1)
                    }
                    Button(onClick = { if (isPlaying) player?.pause() else player?.play() }, colors = ButtonDefaults.buttonColors(Color.Transparent), contentPadding = PaddingValues(0.dp)) {
                        Text(if (isPlaying) "⏸" else "▶", color = KamuxTextPrimary, fontSize = 28.sp)
                    }
                }

                TabRow(
                    selectedTabIndex = if (expandedPanel == "QUEUE") 0 else 1,
                    containerColor = Color.Transparent, contentColor = KamuxTextPrimary,
                    indicator = { tabPositions -> TabRowDefaults.Indicator(modifier = Modifier.tabIndicatorOffset(tabPositions[if (expandedPanel == "QUEUE") 0 else 1]), color = KamuxAccent) }
                ) {
                    Tab(selected = expandedPanel == "QUEUE", onClick = { expandedPanel = "QUEUE" }, text = { Text("A CONTINUACIÓN", fontSize = 12.sp, color = if (expandedPanel == "QUEUE") KamuxTextPrimary else KamuxTextSecondary, fontWeight = FontWeight.Bold) })
                    Tab(selected = expandedPanel == "LYRICS", onClick = { expandedPanel = "LYRICS" }, text = { Text("LETRA", fontSize = 12.sp, color = if (expandedPanel == "LYRICS") KamuxTextPrimary else KamuxTextSecondary, fontWeight = FontWeight.Bold) })
                }

                if (expandedPanel == "QUEUE") {
                    QueueList(queue, track, player)
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (lyricsState) {
                            is LyricsState.Idle -> Text("Preparando...", color = KamuxTextSecondary)
                            is LyricsState.Loading -> CircularProgressIndicator(color = KamuxAccent)
                            is LyricsState.Error -> Text((lyricsState as LyricsState.Error).message, color = Color.Red, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp))
                            is LyricsState.Success -> {
                                val lyricsData = (lyricsState as LyricsState.Success).data

                                val activeLineIndex = if (lyricsData.hasTimestamps && !lyricsData.lyricsLines.isNullOrEmpty()) {
                                    lyricsData.lyricsLines.indexOfLast { currentPositionMs >= it.startTimeMs }
                                } else {
                                    -1
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 80.dp)
                                ) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp, bottom = 24.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (lyricsData.hasTimestamps) "Tipo: Sincronizada" else "Tipo: Plana",
                                                color = KamuxAccent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }

                                    if (lyricsData.hasTimestamps && !lyricsData.lyricsLines.isNullOrEmpty()) {
                                        itemsIndexed(lyricsData.lyricsLines) { index, line ->
                                            val isActive = index == activeLineIndex

                                            Text(
                                                text = line.text,
                                                color = if (isActive) KamuxAccent else KamuxTextSecondary,
                                                fontSize = if (isActive) 22.sp else 18.sp,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                                            )
                                        }
                                    } else {
                                        item {
                                            Text(
                                                text = lyricsData.lyricsText ?: "Letra no disponible.",
                                                color = KamuxTextPrimary,
                                                fontSize = 18.sp,
                                                lineHeight = 28.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).clickable { expandedPanel = "PLAYER" }.zIndex(10f))
        }
    }
}

@Composable
fun QueueList(queue: List<QueueTrack>, currentTrack: QueueTrack, player: Player?) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp, start = 16.dp, end = 16.dp)) {
        item { Text("Reproduciendo desde\nTu fila", color = KamuxTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp)) }
        itemsIndexed(queue) { index, qTrack ->
            val isSelected = qTrack.videoId == currentTrack.videoId
            Row(modifier = Modifier.fillMaxWidth().clickable { player?.seekTo(index, C.TIME_UNSET); player?.play() }.padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = qTrack.thumbnail?.firstOrNull()?.url, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp)) {
                    Text(qTrack.title ?: "", color = if (isSelected) KamuxAccent else KamuxTextPrimary, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${qTrack.artists?.firstOrNull()?.name} • ${qTrack.length}", color = KamuxTextSecondary, fontSize = 14.sp, maxLines = 1)
                }
                if (isSelected) Text("||", color = KamuxAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold) else Text("=", color = Color.DarkGray, fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPicksSection(section: HomeSection, onTrackClick: (TrackItem) -> Unit) {
    val chunkedTracks = (section.contents ?: emptyList()).chunked(4)
    val pagerState = rememberPagerState(pageCount = { chunkedTracks.size })
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(section.title, color = KamuxTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp))
        HorizontalPager(state = pagerState) { page ->
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                chunkedTracks.getOrNull(page)?.forEach { track -> QuickPickRow(track) { onTrackClick(track) } }
            }
        }
    }
}

@Composable
fun QuickPickRow(track: TrackItem, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = track.thumbnails?.firstOrNull()?.url, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp)) {
            Text(track.title ?: "", color = KamuxTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artists?.firstOrNull()?.name ?: "", color = KamuxTextSecondary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListenAgainSection(section: HomeSection, onTrackClick: (TrackItem) -> Unit) {
    val chunkedGridTracks = (section.contents ?: emptyList()).take(27).chunked(9)
    val pagerState = rememberPagerState(pageCount = { chunkedGridTracks.size })
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(section.title, color = KamuxTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp))
        HorizontalPager(state = pagerState) { page ->
            LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(360.dp).padding(start = 16.dp, end = 16.dp)) {
                items(chunkedGridTracks.getOrNull(page) ?: emptyList()) { track ->
                    ListenAgainCard(track) { onTrackClick(track) }
                }
            }
        }
    }
}

@Composable
fun ListenAgainCard(track: TrackItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick() }, shape = RoundedCornerShape(8.dp)) {
        Box(modifier = Modifier.fillMaxSize().background(KamuxSurface)) {
            AsyncImage(model = track.thumbnails?.lastOrNull()?.url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 100f)))
            Text(track.title ?: "", color = KamuxTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp))
        }
    }
}

@Composable
fun MiniPlayer(track: QueueTrack, isPlaying: Boolean, isLoading: Boolean, onPlayPauseClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(KamuxSurface).padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = track.thumbnail?.firstOrNull()?.url, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp)) {
            Text(track.title ?: "", color = KamuxTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track.artists?.firstOrNull()?.name ?: "", color = KamuxTextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        if (isLoading) {
            CircularProgressIndicator(color = KamuxAccent, modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
        } else {
            Button(onClick = onPlayPauseClick, colors = ButtonDefaults.buttonColors(Color.Transparent), contentPadding = PaddingValues(0.dp)) {
                Text(if (isPlaying) "⏸" else "▶", color = KamuxTextPrimary, fontSize = 28.sp)
            }
        }
    }
}

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(val sections: List<HomeSection>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
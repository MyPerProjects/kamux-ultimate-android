package com.kamux.ultimate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class KamuxPlayerViewModel : ViewModel() {

    var currentPositionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set

    var lyricsState by mutableStateOf<LyricsState>(LyricsState.Idle)
        private set

    fun bindPlayer(player: Player) {
        viewModelScope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    currentPositionMs = player.currentPosition
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                delay(1000L)
            }
        }
    }

    fun seekTo(player: Player?, positionMs: Long) {
        player?.seekTo(positionMs)
        currentPositionMs = positionMs
    }

    fun fetchLyrics(trackName: String?, artistName: String?) {
        if (trackName.isNullOrEmpty() || artistName.isNullOrEmpty()) {
            lyricsState = LyricsState.Error("Faltan metadatos para buscar la letra.")
            return
        }

        lyricsState = LyricsState.Loading
        viewModelScope.launch {
            try {
                val response = KamuxApiService.instance.getLyrics(trackName, artistName)
                if (response.status == "success") {
                    lyricsState = LyricsState.Success(response)
                } else {
                    lyricsState = LyricsState.Error("Letra no encontrada.")
                }
            } catch (e: Exception) {
                lyricsState = LyricsState.Error("Error de red: ${e.message}")
            }
        }
    }

    fun resetLyrics() {
        lyricsState = LyricsState.Idle
    }

    var searchSuggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var searchResults by mutableStateOf<List<SearchItem>?>(null)
        private set
    var isSearchLoading by mutableStateOf(false)
        private set

    var currentAlbum by mutableStateOf<AlbumDetails?>(null)
        private set
    var currentArtist by mutableStateOf<ArtistDetails?>(null)
        private set
    var isDetailLoading by mutableStateOf(false)
        private set

    private var suggestionJob: Job? = null

    var artistAllAlbums by mutableStateOf<List<ArtistAlbumItem>?>(null)
        private set
    var isAllAlbumsLoading by mutableStateOf(false)
        private set

    fun fetchAllArtistAlbums(channelId: String, params: String) {
        isAllAlbumsLoading = true
        viewModelScope.launch {
            try {
                val response = KamuxApiService.instance.getArtistAlbums(channelId, params)
                if (response.status == "success") artistAllAlbums = response.data
            } catch (e: Exception) {
            } finally {
                isAllAlbumsLoading = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        suggestionJob?.cancel()
        if (query.length < 2) {
            searchSuggestions = emptyList()
            return
        }

        suggestionJob = viewModelScope.launch {
            delay(350L)
            try {
                val response = KamuxApiService.instance.getSearchSuggestions(query)
                if (response.status == "success") {
                    searchSuggestions = response.data
                }
            } catch (e: Exception) {
                searchSuggestions = emptyList()
            }
        }
    }

    fun performGlobalSearch(query: String) {
        suggestionJob?.cancel()
        searchSuggestions = emptyList()
        if (query.isBlank()) return

        isSearchLoading = true
        viewModelScope.launch {
            try {
                val response = KamuxApiService.instance.searchCatalog(query)
                searchResults = if (response.status == "success") response.data else emptyList()
            } catch (e: Exception) {
                searchResults = emptyList()
            } finally {
                isSearchLoading = false
            }
        }
    }

    fun fetchAlbumDetails(browseId: String) {
        isDetailLoading = true
        currentAlbum = null
        viewModelScope.launch {
            try {
                val response = KamuxApiService.instance.getAlbumDetails(browseId)
                if (response.status == "success") currentAlbum = response.data
            } catch (e: Exception) {
            } finally {
                isDetailLoading = false
            }
        }
    }

    fun fetchArtistDetails(browseId: String) {
        isDetailLoading = true
        currentArtist = null
        artistAllAlbums = null
        viewModelScope.launch {
            try {
                val response = KamuxApiService.instance.getArtistDetails(browseId)
                if (response.status == "success") currentArtist = response.data
            } catch (e: Exception) {
            } finally {
                isDetailLoading = false
            }
        }
    }

    fun clearSearch() {
        searchResults = null
        searchSuggestions = emptyList()
    }
}

sealed interface LyricsState {
    object Idle : LyricsState
    object Loading : LyricsState
    data class Success(val data: LyricsResponse) : LyricsState
    data class Error(val message: String) : LyricsState
}
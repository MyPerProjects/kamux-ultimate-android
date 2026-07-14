package com.kamux.ultimate

import com.google.gson.annotations.SerializedName

data class HomeResponse(
    @SerializedName("status") val status: String,
    @SerializedName("count") val count: Int,
    @SerializedName("data") val data: List<HomeSection>
)

data class HomeSection(
    @SerializedName("title") val title: String,
    @SerializedName("contents") val contents: List<TrackItem>?
)

data class TrackItem(
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("playlistId") val playlistId: String?,
    @SerializedName("browseId") val browseId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artists") val artists: List<ArtistItem>?,
    @SerializedName("thumbnails") val thumbnails: List<ThumbnailItem>?
)

data class ArtistItem(
    @SerializedName("name") val name: String
)

data class ThumbnailItem(
    @SerializedName("url") val url: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int
)

data class QueueResponse(
    @SerializedName("status") val status: String,
    @SerializedName("playlistId") val playlistId: String?,
    @SerializedName("lyricsId") val lyricsId: String?,
    @SerializedName("tracks") val tracks: List<QueueTrack>
)

data class QueueTrack(
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("length") val length: String?,
    @SerializedName(value = "thumbnail", alternate = ["thumbnails"]) val thumbnail: List<ThumbnailItem>?,
    @SerializedName("artists") val artists: List<ArtistItem>?
)

data class LyricsResponse(
    @SerializedName("status") val status: String,
    @SerializedName("hasTimestamps") val hasTimestamps: Boolean,
    @SerializedName("source") val source: String?,
    @SerializedName("lyrics") val lyricsText: String?, // para letra plana
    @SerializedName("lyricsLines") val lyricsLines: List<LyricLine>? // para letra sincronizada
)

data class LyricLine(
    @SerializedName("text") val text: String,
    @SerializedName("startTimeMs") val startTimeMs: Long,
    @SerializedName("endTimeMs") val endTimeMs: Long
)

data class SuggestionsResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<String>
)

data class SearchResponse(
    @SerializedName("status") val status: String,
    @SerializedName("count") val count: Int,
    @SerializedName("data") val data: List<SearchItem>
)

data class SearchItem(
    @SerializedName("resultType") val resultType: String,
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("browseId") val browseId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artist") val artistName: String?,
    @SerializedName("artists") val artists: List<ArtistItem>?,
    @SerializedName("thumbnails") val thumbnails: List<ThumbnailItem>?
)

// Detalle de un Álbum Completo
data class AlbumResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: AlbumDetails
)

data class AlbumDetails(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("year") val year: String?,
    @SerializedName("audioPlaylistId") val audioPlaylistId: String?,
    @SerializedName("artists") val artists: List<ArtistItem>?,
    @SerializedName("thumbnails") val thumbnails: List<ThumbnailItem>?,
    @SerializedName("tracks") val tracks: List<AlbumTrack>
)

data class AlbumTrack(
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("title") val title: String,
    @SerializedName("duration") val duration: String?,
    @SerializedName("artists") val artists: List<ArtistItem>?
)

// Detalle de un Artista
data class ArtistResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: ArtistDetails
)

data class ArtistDetails(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("subscribers") val subscribers: String?,
    @SerializedName("thumbnails") val thumbnails: List<ThumbnailItem>?,
    @SerializedName("songs") val songs: ArtistSongsSection?,
    @SerializedName("albums") val albums: ArtistAlbumsSection?
)

data class ArtistSongsSection(
    @SerializedName("results") val results: List<QueueTrack>
)

data class ArtistAlbumsSection(
    @SerializedName("browseId") val browseId: String?,
    @SerializedName("params") val params: String?,
    @SerializedName("results") val results: List<ArtistAlbumItem>
)

data class ArtistAlbumItem(
    @SerializedName("browseId") val browseId: String?,
    @SerializedName("title") val title: String,
    @SerializedName("year") val year: String?,
    @SerializedName("thumbnails") val thumbnails: List<ThumbnailItem>?
)

data class ArtistAlbumsResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: List<ArtistAlbumItem>
)
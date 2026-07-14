package com.kamux.ultimate

import com.kamux.ultimate.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface KamuxApiService {

    @GET("api/home")
    suspend fun getHome(): HomeResponse

    @GET("api/queue")
    suspend fun getQueue(
        @Query("videoId") videoId: String? = null,
        @Query("playlistId") playlistId: String? = null
    ): QueueResponse

    @GET("api/lyrics")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String
    ): LyricsResponse

    @GET("api/search/suggestions")
    suspend fun getSearchSuggestions(@Query("query") query: String): SuggestionsResponse

    @GET("api/search")
    suspend fun searchCatalog(@Query("query") query: String): SearchResponse

    @GET("api/album")
    suspend fun getAlbumDetails(@Query("browseId") browseId: String): AlbumResponse

    @GET("api/artist")
    suspend fun getArtistDetails(@Query("browseId") browseId: String): ArtistResponse

    @GET("api/artist/albums")
    suspend fun getArtistAlbums(
        @Query("channelId") channelId: String,
        @Query("params") params: String
    ): ArtistAlbumsResponse

    companion object {
        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val instance: KamuxApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BuildConfig.KAMUX_API_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(KamuxApiService::class.java)
        }
    }
}
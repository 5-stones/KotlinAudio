package com.doublesymmetry.kotlinaudio.players

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import com.doublesymmetry.kotlinaudio.DescriptionAdapter
import com.doublesymmetry.kotlinaudio.R
import com.doublesymmetry.kotlinaudio.models.AudioItem
import com.doublesymmetry.kotlinaudio.models.AudioItemTransitionReason
import com.doublesymmetry.kotlinaudio.models.AudioPlayerState
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import java.util.concurrent.TimeUnit

fun isJUnitTest(): Boolean {
    for (element in Thread.currentThread().stackTrace) {
        if (element.className.startsWith("org.junit.")) {
            return true
        }
    }
    return false
}

open class AudioPlayer(private val context: Context) {
    protected val exoPlayer: SimpleExoPlayer = SimpleExoPlayer.Builder(context).build()

    private val mediaSession: MediaSessionCompat = MediaSessionCompat(context, "AudioPlayerSession")
    private val mediaSessionConnector: MediaSessionConnector = MediaSessionConnector(mediaSession)

    private val playerNotificationManager: PlayerNotificationManager
    private val descriptionAdapter = DescriptionAdapter(context, null)

    val event = EventHolder()

    init {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            ""
        }

        if (isJUnitTest()) {
            exoPlayer.setThrowsWhenUsingWrongThread(false)
        }

        mediaSessionConnector.setPlayer(exoPlayer)

        val builder = PlayerNotificationManager.Builder(context, NOTIFICATION_ID, channelId)

        playerNotificationManager = builder
            .setMediaDescriptionAdapter(descriptionAdapter)
            .build()

        if (!isJUnitTest()) {
            playerNotificationManager.apply {
                setPlayer(exoPlayer)
                setMediaSessionToken(mediaSession.sessionToken)
                setUseNextActionInCompactView(true)
                setUsePreviousActionInCompactView(true)
            }
        }

        addPlayerListener()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = CHANNEL_ID
        val channelName = context.getString(R.string.playback_channel_name)
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        channel.description = "Used when playing music"
        channel.setSound(null, null)

        val service = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    open fun load(item: AudioItem, playWhenReady: Boolean = true) {
        exoPlayer.playWhenReady = playWhenReady

        val mediaItem = getMediaItemFromAudioItem(item)
        exoPlayer.addMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    fun togglePlaying() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun play() {
        exoPlayer.prepare()
        exoPlayer.play()

        mediaSession.isActive = true
    }

    fun pause() {
        exoPlayer.pause()
    }

    /**
     * Stops and resets the player. Only call this when you are finished using the player, otherwise use [pause].
     */
    @CallSuper
    open fun stop() {
        descriptionAdapter.release()
        exoPlayer.release()
    }

    fun seek(duration: Long, unit: TimeUnit) {
        val millis = TimeUnit.MILLISECONDS.convert(duration, unit)
        exoPlayer.seekTo(millis)
    }

    protected fun getMediaItemFromAudioItem(audioItem: AudioItem): MediaItem {
        return MediaItem.Builder().setUri(audioItem.audioUrl).setTag(audioItem).build()
    }

    private fun addPlayerListener() {
        exoPlayer.addListener(object: Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    STATE_BUFFERING -> event.updateAudioPlayerState(AudioPlayerState.BUFFERING)
                    STATE_IDLE -> event.updateAudioPlayerState(AudioPlayerState.IDLE)
                    STATE_READY -> event.updateAudioPlayerState(AudioPlayerState.READY)
                    STATE_ENDED -> {
                        TODO()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                when (reason) {
                    MEDIA_ITEM_TRANSITION_REASON_AUTO -> event.updateAudioItemTransition(AudioItemTransitionReason.AUTO)
                    MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> event.updateAudioItemTransition(AudioItemTransitionReason.QUEUE_CHANGED)
                    MEDIA_ITEM_TRANSITION_REASON_REPEAT -> event.updateAudioItemTransition(AudioItemTransitionReason.REPEAT)
                    MEDIA_ITEM_TRANSITION_REASON_SEEK -> event.updateAudioItemTransition(AudioItemTransitionReason.SEEK_TO_ANOTHER_AUDIO_ITEM)
                }
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                if (isLoading) event.updateAudioPlayerState(AudioPlayerState.LOADING)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) event.updateAudioPlayerState(AudioPlayerState.PLAYING)
                else event.updateAudioPlayerState(AudioPlayerState.PAUSED)
            }
        })
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "kotlin_audio_player"
    }
}
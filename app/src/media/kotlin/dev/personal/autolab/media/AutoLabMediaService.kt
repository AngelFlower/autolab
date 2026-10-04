package dev.personal.autolab.media

import android.media.MediaDescription
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.media.MediaBrowserService

/**
 * MediaBrowserService minimo: un unico item "AutoLab" sin audio real. Solo existe para
 * comprobar si el auto lista la app (Fase 7H); tocar el item marca el estado como
 * reproduciendo para que el host no muestre error.
 */
class AutoLabMediaService : MediaBrowserService() {

    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "AutoLab").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = setState(PlaybackState.STATE_PLAYING)
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) =
                    setState(PlaybackState.STATE_PLAYING)
                override fun onPause() = setState(PlaybackState.STATE_PAUSED)
                override fun onStop() = setState(PlaybackState.STATE_STOPPED)
            })
            isActive = true
        }
        setState(PlaybackState.STATE_STOPPED)
        sessionToken = session.sessionToken
    }

    override fun onDestroy() {
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) =
        BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>,
    ) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }
        val item = MediaDescription.Builder()
            .setMediaId(ITEM_ID)
            .setTitle("AutoLab")
            .setSubtitle("Prueba de visibilidad en el auto")
            .build()
        result.sendResult(mutableListOf(MediaBrowser.MediaItem(item, MediaBrowser.MediaItem.FLAG_PLAYABLE)))
    }

    private fun setState(state: Int) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_STOP or PlaybackState.ACTION_PLAY_FROM_MEDIA_ID,
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
    }

    private companion object {
        const val ROOT_ID = "root"
        const val ITEM_ID = "autolab"
    }
}

package com.anggrayudi.storage.media

import android.os.Environment

sealed interface MediaDirectory {
    val folderName: String

    /**
     * Created on 06/09/20
     * @author Anggrayudi H
     */
    enum class Image(override val folderName: String) : MediaDirectory {
        PICTURES(Environment.DIRECTORY_PICTURES),
        DCIM(Environment.DIRECTORY_DCIM)
    }

    /**
     * Created on 06/09/20
     * @author Anggrayudi H
     */
    enum class Video(override val folderName: String) : MediaDirectory {
        MOVIES(Environment.DIRECTORY_MOVIES),
        DCIM(Environment.DIRECTORY_DCIM)
    }

    /**
     * Created on 06/09/20
     * @author Anggrayudi H
     */
    enum class Audio(override val folderName: String) : MediaDirectory {
        MUSIC(Environment.DIRECTORY_MUSIC),
        PODCASTS(Environment.DIRECTORY_PODCASTS),
        RINGTONES(Environment.DIRECTORY_RINGTONES),
        ALARMS(Environment.DIRECTORY_ALARMS),
        NOTIFICATIONS(Environment.DIRECTORY_NOTIFICATIONS)
    }
}
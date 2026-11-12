package com.example

import android.util.Log
import java.io.File

private const val TAG = "AudioTagger"

/**
 * Embeds cover art + title/artist/album metadata into a downloaded track's audio file, using
 * jaudiotagger (pure JVM, no NDK/native build needed - see the doc on [audioTagFileExtensionFor]
 * for why this app has no native module at all otherwise). Only ever called for a container
 * jaudiotagger actually supports (MP4/M4A, MP3) - see [audioTagFileExtensionFor].
 *
 * jaudiotagger identifies a file's format by its extension, not its content, so this works
 * against a temporary copy named with the right extension rather than [DownloadRepository]'s
 * actual `$key.audio` on-disk convention, then atomically replaces the original with the tagged copy -
 * every part of the app that reads a download's `filePath` keeps working unmodified. Fails
 * completely silently (logged, not thrown) on any error: a tagging failure must never break or
 * corrupt an otherwise-successful download, which is why the original file is only ever
 * overwritten after a tag write actually succeeds.
 */
object AudioTagger {

    fun embedIfSupported(targetFile: File, contentType: String?, track: Track, coverArtBytes: ByteArray?) {
        val extension = audioTagFileExtensionFor(contentType) ?: return
        // Tagging is optional. Keep room for its working copy and atomic replacement staging.
        if (targetFile.length() > ((targetFile.parentFile?.usableSpace ?: 0) - 16L * 1024 * 1024) / 2) return
        val workingFile = File(targetFile.parentFile, "${targetFile.name}$extension")
        runCatching {
            targetFile.copyTo(workingFile, overwrite = true)
            writeTags(workingFile, track, coverArtBytes)
            promoteTaggedAudio(workingFile, targetFile)
        }.onFailure { Log.w(TAG, "tag embed failed for \"${track.title}\"", it) }
        workingFile.delete()
    }

    private fun writeTags(file: File, track: Track, coverArtBytes: ByteArray?) {
        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, track.title)
        tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, track.artist)
        tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, track.album)

        if (coverArtBytes != null) {
            // Built directly from raw bytes (StandardArtwork's plain setters), not
            // ArtworkFactory.createArtworkFromFile/URL - those read the image via javax.imageio,
            // which isn't meaningfully available on Android and would defeat the point of a
            // pure-JVM (no native/AWT dependency) tagging approach.
            val artwork = org.jaudiotagger.tag.images.StandardArtwork()
            artwork.binaryData = coverArtBytes
            artwork.mimeType = "image/jpeg"
            artwork.pictureType = 3 // ID3/MP4 "front cover" picture type
            tag.deleteArtworkField()
            tag.setField(artwork)
        }

        audioFile.commit()
    }
}

/** A failed staged replacement restores the original bytes rather than leaving a partial copy. */
internal fun promoteTaggedAudio(working: File, target: File) {
    check(working.parentFile?.canonicalFile != null && working.parentFile?.canonicalFile == target.parentFile?.canonicalFile && working.isFile && working.length() > 0)
    java.io.FileOutputStream(working, true).use { it.fd.sync() }
    replaceFileAtomically(working, target)
}

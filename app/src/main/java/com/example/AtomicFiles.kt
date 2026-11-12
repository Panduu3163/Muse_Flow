package com.example

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Caller owns mutual exclusion. NIO is backported by the existing desugar_jdk_libs_nio dependency. */
internal fun replaceFileAtomically(staged: File, target: File) {
    check(staged.isFile && staged.parentFile?.canonicalFile != null && staged.parentFile?.canonicalFile == target.parentFile?.canonicalFile)
    Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

private fun recoverLegacyAtomicFile(target: File) {
    val backup = File(target.path + ".bak")
    if (backup.isFile) replaceFileAtomically(backup, target)
}

/** Keep the same filenames as Android AtomicFile and recover copies made by its older implementation. */
internal fun writeAtomicFile(target: File, bytes: ByteArray) {
    recoverLegacyAtomicFile(target)
    val staged = File(target.path + ".new")
    target.parentFile?.mkdirs()
    try {
        FileOutputStream(staged).use { output -> output.write(bytes); output.fd.sync() }
        replaceFileAtomically(staged, target)
    } catch (error: Exception) { staged.delete(); throw error }
}

internal fun readAtomicFile(target: File): FileInputStream {
    recoverLegacyAtomicFile(target)
    File(target.path + ".new").delete()
    return FileInputStream(target)
}

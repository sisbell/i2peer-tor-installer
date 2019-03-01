package org.i2peer.tor.installer

import arrow.core.Try
import arrow.core.getOrElse
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class InstallMessage(val text: String)

data class InstallError(val message: String, val e: Throwable? = null)

class InstallComplete()

@ObsoleteCoroutinesApi
suspend fun installTor(installDir: File, eventChannel: SendChannel<Any>) {
    programUnarchiver().send(InstallData(installDir = installDir, event = eventChannel))
}

private data class InstallData(val installDir: File, val event: SendChannel<Any>)

@ObsoleteCoroutinesApi
private fun programUnarchiver() = GlobalScope.actor<InstallData> {
    val data = channel.receive()
    data.event.send(InstallMessage("Unarchiving tor"))

    unzipArchive(data.installDir, Files.getResourceStream(torArchiveName()).getOrElse { null }).fold(
        {
            data.event.send(InstallError("Failed to unzip tor archive", it))
        },
        {
            unzipData(data)
        })
    channel.cancel()
}

private suspend fun unzipData(data: InstallData) {
    unzipArchive(data.installDir, Files.getResourceStream("data.zip").getOrElse { null }).fold(
        {
            data.event.send(InstallError("Failed to unzip tor data", it))
        },
        {
            setTorPermissions(data)
        })
}

private suspend fun setTorPermissions(data: InstallData) {
    val executable = File(data.installDir, torExecutableFileName)
    executable.setPermsRwx().fold(
        {
            data.event.send(InstallError("Failed to set permissions on tor", it))
        }, {
            data.event.send(InstallComplete())
        })
}

private fun unzipArchive(installDir: File, archive: InputStream?): Try<Unit> {
    return Try {
        installDir.createDir()
        val stream = ZipInputStream(archive)
        var entry = stream.nextEntry

        while (entry != null) {
            val f = File(installDir, entry.name)
            if (entry.isDirectory)
                f.mkdirs()
            else
                f.outputStream().use { output -> stream.copyTo(output) }
            stream.closeEntry()
            entry = stream.nextEntry
        }
    }
}

private fun torArchiveName(osType: OsType? = osType()): String {
    return when (osType) {
        OsType.LINUX_32 -> "tor-linux-i686.zip"
        OsType.LINUX_64 -> "tor-linux-x86_64.zip"
        OsType.MAC -> "tor-osx-x86_x64.zip"
        OsType.WINDOWS_32 -> "tor-win32.zip"
        OsType.WINDOWS_64 -> "tor-win64.zip"
        else -> throw RuntimeException("OS Unsupported")
    }
}

private val torExecutableFileName: String = when (osType()) {
    OsType.ANDROID, OsType.LINUX_32, OsType.LINUX_64, OsType.MAC -> "tor"
    OsType.WINDOWS_32, OsType.WINDOWS_64 -> "tor.exe"
    else -> throw RuntimeException("Unsupported OS")
}

/**
 * Detects OS Type. The bit 32/64 is based on the bit of the installed JDK
 */
private fun osType(
    vmName: String = System.getProperty("java.vm.name"),
    osName: String = System.getProperty("os.name"),
    osArch: String = System.getProperty("os.arch")
): OsType {
    if (vmName.contains("Dalvik")) return OsType.ANDROID
    return when {
        osName.contains("Windows") -> {
            if (osArch.contains("64")) OsType.WINDOWS_64 else OsType.WINDOWS_32
        }
        osName.contains("Mac") -> OsType.MAC
        osName.contains("Linux") -> {
            if (osArch.contains("64")) OsType.LINUX_64 else OsType.LINUX_32
        }
        else -> OsType.UNSUPPORTED
    }
}

private enum class OsType {
    WINDOWS_32, WINDOWS_64, LINUX_64, LINUX_32, MAC, ANDROID, UNSUPPORTED
}

private object Files {
    fun getResourceStream(fileName: String): Try<InputStream> = Try { javaClass.getResourceAsStream("/$fileName") }
}

fun File.createDir(): Boolean = exists() || mkdirs()

fun File.setPermsRwx(): Try<Boolean> = Try {
    setReadable(true) && setExecutable(true) && setWritable(false) && setWritable(true, true)
}
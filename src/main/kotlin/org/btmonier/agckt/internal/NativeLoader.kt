package org.btmonier.agckt.internal

import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import org.btmonier.agckt.AgcException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Handles locating and loading the platform-specific AGC shared library.
 *
 * Search order:
 * 1. `jna.library.path` system property (for local dev / manual install)
 * 2. JAR-embedded resource at `native/<platform>/<libname>`
 * 3. System library path
 */
internal object NativeLoader {

    private const val LIB_NAME = "agc"

    @Volatile
    private var instance: AgcLibrary? = null

    fun load(): AgcLibrary = instance ?: synchronized(this) {
        instance ?: run {
            tryExtractFromResources()
            try {
                Native.load(LIB_NAME, AgcLibrary::class.java)
            } catch (e: UnsatisfiedLinkError) {
                throw AgcException(
                    "Failed to load native AGC library ($LIB_NAME). " +
                        "Platform: ${platformDir()}. " +
                        "Set -Djna.library.path to the directory containing the shared library, " +
                        "or ensure it is on the system library path.",
                    e,
                )
            }.also { instance = it }
        }
    }

    private fun tryExtractFromResources() {
        val platform = platformDir()
        val libFileName = System.mapLibraryName(LIB_NAME)
        val resourcePath = "native/$platform/$libFileName"

        val stream: InputStream = NativeLoader::class.java.classLoader
            ?.getResourceAsStream(resourcePath)
            ?: return

        stream.use { input ->
            val tmpDir: Path = Files.createTempDirectory("agckt-native-")
            tmpDir.toFile().deleteOnExit()

            val libFile = tmpDir.resolve(libFileName)
            Files.copy(input, libFile, StandardCopyOption.REPLACE_EXISTING)
            libFile.toFile().deleteOnExit()

            NativeLibrary.addSearchPath(LIB_NAME, tmpDir.toAbsolutePath().toString())
        }
    }

    private fun platformDir(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val osTag = when {
            "linux" in os -> "linux"
            "mac" in os || "darwin" in os -> "darwin"
            "win" in os -> "windows"
            else -> os.replace("\\s+".toRegex(), "-")
        }

        val archTag = when (arch) {
            "amd64", "x86_64" -> "x86-64"
            "aarch64", "arm64" -> "aarch64"
            else -> arch
        }

        return "$osTag-$archTag"
    }
}

# agckt

Kotlin/JVM bindings for [AGC (Assembled Genomes Compressor)](https://github.com/refresh-bio/agc) archives.

**agckt** provides a safe, idiomatic Kotlin API for reading AGC archives via JNA bindings to the native AGC C library. It handles all native memory management internally, so you can focus on your genomics workflow.

## Features

- Open and query AGC archives from any JVM language
- List samples and contigs
- Extract full sequences or arbitrary slices
- Safe resource management via `Closeable` / `use {}`
- Automatic native library loading (bundled in JAR or via system path)
- Zero native code to write -- pure Kotlin over JNA

## Quick Start

```kotlin
import org.btmonier.agckt.AgcArchive
import java.nio.file.Path

AgcArchive.open(Path.of("genomes.agc")).use { archive ->
    // List all samples
    val samples = archive.listSamples()
    println("Samples: $samples")

    // List contigs for a sample
    val contigs = archive.listContigs("GRCh38")
    println("Contigs: $contigs")

    // Get full sequence
    val seq = archive.getSequence("chr1", sample = "GRCh38")
    println("chr1 length: ${seq.length}")

    // Get a slice (0-based, inclusive)
    val slice = archive.getSequence("chr1", start = 1000, end = 1999, sample = "GRCh38")
    println("Slice: ${slice.length} bases")
}
```

## API Reference

### `AgcArchive`

| Member | Description |
|---|---|
| `AgcArchive.open(path, prefetch)` | Open an archive. `prefetch=true` loads into memory for faster queries. |
| `close()` | Release native resources. Safe to call multiple times. |
| `sampleCount` | Number of samples in the archive. |
| `referenceSample` | Reference sample name, or `null`. |
| `listSamples()` | List all sample names. |
| `contigCount(sample)` | Number of contigs in a sample. |
| `listContigs(sample?)` | List contig names. `null` returns contigs across all samples. |
| `contigLength(contig, sample?)` | Length of a contig in bases. |
| `getSequence(contig, sample?)` | Extract the full sequence. |
| `getSequence(contig, start, end, sample?)` | Extract a subsequence (0-based, inclusive). |

### Error Handling

- `AgcException` -- base class for all AGC errors
- `AgcFileNotFoundException` -- archive file not found
- `AgcNativeException` -- native operation failed (bad contig name, ambiguous query, etc.)

### Thread Safety

`AgcArchive` is **not** thread-safe. For concurrent access, either synchronize externally or open a separate handle per thread.

## Building the Native Library

The AGC shared library must be built from source.

### With pixi (recommended)

[pixi](https://pixi.sh) manages all native build dependencies (GCC, cmake, make, git)
automatically via conda-forge -- no manual installs needed:

```bash
pixi run build-native
```

If you don't have pixi yet:

```bash
curl -fsSL https://pixi.sh/install.sh | sh
```

### Without pixi

```bash
cd native
bash build.sh
```

**Requirements:**

- **GCC 11+** (real GCC, not Apple Clang)
- **GNU Make 4+** (`brew install make` on macOS)
- **cmake**, **git**

On macOS: `brew install gcc make`

You can also override the compiler by setting `CC` and `CXX` before running the script:

```bash
CC=gcc-14 CXX=g++-14 bash native/build.sh
```

### What the build does

The script clones AGC v3.2, builds the static archive, and links it into a shared library (`libagc.dylib` / `libagc.so`). The result is automatically copied to `src/main/resources/native/<platform>/` for JAR embedding.

### Manual Library Path

If the native library is installed elsewhere, point JNA to it:

```bash
java -Djna.library.path=/path/to/lib -jar myapp.jar
```

## Development

```bash
# Build everything (compile + lint + test)
./gradlew build

# Run tests only
./gradlew test

# Format code
./gradlew ktlintFormat
```

## Project Structure

```
src/main/kotlin/org/btmonier/agckt/
  AgcArchive.kt          -- public API
  AgcException.kt        -- exception hierarchy
  internal/
    AgcLibrary.kt        -- JNA function mappings
    NativeLoader.kt      -- platform-aware library loading
    PointerUtils.kt      -- char** conversion helpers
    CLibrary.kt          -- libc free() binding
native/
  build.sh               -- builds shared library from AGC source
  include/agc-api.h      -- vendored C API header
```

## License

MIT -- see [LICENSE](LICENSE).

AGC itself is also MIT-licensed. See [refresh-bio/agc](https://github.com/refresh-bio/agc).

# agckt

Kotlin/JVM bindings for [AGC (Assembled Genomes Compressor)](https://github.com/refresh-bio/agc) archives.


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


> [!NOTE]
>
> `AgcArchive` is currently **not** thread-safe. For concurrent access, either synchronize externally or open a separate 
> handle per thread.


## Building the Native Library

The AGC shared library must be built from source.

### With pixi (recommended)

[pixi](https://pixi.sh) manages all native build dependencies (GCC, cmake, make, git)
automatically via conda-forge -- no manual installs needed:

```bash
pixi run build-native
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



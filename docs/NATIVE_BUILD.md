# Building the Native Library

## Overview

agckt requires a platform-specific shared library (`libagc.so` / `libagc.dylib`)
built from the [AGC](https://github.com/refresh-bio/agc) C/C++ source code.

The `native/build.sh` script automates this process.

## Option A: Using Pixi (Recommended)

[Pixi](https://pixi.sh) manages all native toolchain dependencies (GCC, Make,
cmake, etc.) through Conda packages so you don't need to install them manually.

### Install Pixi

```bash
curl -fsSL https://pixi.sh/install.sh | bash
```

### Build the Native Library

```bash
pixi run build-native
```

Pixi resolves the full environment from `pixi.toml` which includes:

* GCC 11+, 
* Make 4+, 
* cmake,
* `agc` (for running benchmarks with the CLI process builder)
* R + `ggplot2` (for plotting benchmark results)


---

## Option B: Manual Setup

### Prerequisites

#### macOS

```bash
brew install gcc make
```

- GCC 11+ (real GCC, not Apple Clang which ships as `/usr/bin/g++`)
- GNU Make 4+ (macOS ships Make 3.81 which is too old)
- cmake (usually available via Xcode Command Line Tools)

#### Linux (Ubuntu/Debian)

```bash
sudo apt-get install build-essential cmake
```

GCC 10+ is typically available by default on Ubuntu 22.04+.

### Build Steps

```bash
cd native
bash build.sh
```

The script will:

1. Clone AGC v3.2 from GitHub (into `native/agc-src/`)
2. Pre-build zlib-ng with tests disabled (workaround for GCC 15 compat)
3. Build `libagc.a` (static archive)
4. Link it with zstd, zlib-ng, and libdeflate into a shared library
5. Copy the result to both `native/lib/` and `src/main/resources/native/<platform>/`

## Output

| Platform     | Library        | Resource path                        |
|--------------|----------------|--------------------------------------|
| Linux x86_64 | `libagc.so`    | `native/linux-x86-64/libagc.so`      |
| macOS x86_64 | `libagc.dylib` | `native/darwin-x86-64/libagc.dylib`  |
| macOS ARM64  | `libagc.dylib` | `native/darwin-aarch64/libagc.dylib` |

## Using a Pre-built Library

If you have a pre-built `libagc.so`/`libagc.dylib`, you can skip the build
and point JNA to it:

```bash
java -Djna.library.path=/path/to/directory/containing/libagc -jar myapp.jar
```

Or place it in the JAR resources at the appropriate platform path.

## Troubleshooting

### "Compiler not supported" during AGC build

The AGC Makefile restricts compiler versions. The build script overrides
the maximum version to 20. If you still hit issues, check that you have
real GCC (not Apple Clang) installed and that `g++-NN` is on your PATH.

### zlib-ng test compilation failures

GCC 15 on macOS has known incompatibilities with macOS SDK headers in
zlib-ng tests. The build script pre-builds zlib-ng with tests disabled.
If you still see issues, try GCC 13: `brew install gcc@13`.

### "Failed to load native AGC library" at runtime

The `NativeLoader` searches in this order:
1. `jna.library.path` system property
2. JAR-embedded resource at `native/<platform>/libagc.{so,dylib}`
3. System library paths

Check that the library exists for your platform and that the file
permissions allow reading.

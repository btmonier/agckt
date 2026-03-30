# Architecture

## Layered Design

agckt uses a three-layer architecture to isolate native complexity from the public API:

```
+---------------------------------------------+
|  Public Kotlin API                           |
|  AgcArchive (Closeable, safe)                |
|  AgcException hierarchy                      |
+---------------------------------------------+
|  Internal Binding Layer                      |
|  AgcLibrary (JNA interface)                  |
|  NativeLoader, PointerUtils, CLibrary        |
+---------------------------------------------+
|  Native Shared Library                       |
|  libagc.so / libagc.dylib                    |
|  (AGC C API compiled as shared object)       |
+---------------------------------------------+
```

### Public API (`org.btmonier.agckt`)

- `AgcArchive` -- the only class users interact with
- No JNA types leak into the public surface
- All return types are standard Kotlin/JVM types (`String`, `List<String>`, `Int`)
- `Closeable` for resource management
- `AtomicBoolean` guard prevents use-after-close and double-free
- `Cleaner` safety net for unreleased handles

### Internal Binding Layer (`org.btmonier.agckt.internal`)

- `AgcLibrary` -- JNA `Library` interface mapping all 10 AGC C functions
- `NativeLoader` -- thread-safe singleton that extracts the platform-specific
  shared library from JAR resources and loads it via JNA
- `PointerUtils` -- converts `char**` native arrays to `List<String>`
- `CLibrary` -- minimal libc `free()` binding for strings allocated by AGC

### Native Library

Built from upstream AGC v3.2 source. The static archive (`libagc.a`) is
re-linked with zstd and zlib-ng into a single shared object.

## Memory Ownership

| Allocation | Deallocation | Scope |
|---|---|---|
| `agc_open` | `agc_close` | Tied to `AgcArchive.close()` |
| `agc_list_sample` | `agc_list_destroy` | Within `listSamples()` try/finally |
| `agc_list_ctg` | `agc_list_destroy` | Within `listContigs()` try/finally |
| `agc_reference_sample` | `free()` | Within `referenceSample` getter |
| `Memory(n)` for seq buf | JVM GC | Scoped to `getSequence()` call |

## Future Extensibility

- The `NativeLoader` can be extended to support alternative backends
  (JNI, Panama FFI) by swapping the `AgcLibrary` implementation
- `getSequence` returns `String` now but could return `CharSequence`
  backed by a direct `ByteBuffer` for zero-copy access
- Coroutine wrappers (`suspend fun`) can be layered on top without
  changing the core API

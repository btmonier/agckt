#!/usr/bin/env bash
#
# Build the AGC shared library from source for use with JNA.
#
# This script:
#   1. Clones the AGC repo at a pinned tag
#   2. Builds the static library (libagc.a)
#   3. Re-links it into a shared library (.so / .dylib)
#   4. Copies the result to native/lib/ and src/main/resources/native/<platform>/
#
# Requirements:
#   - GCC 11+ (real GCC, not Apple Clang)
#   - GNU Make 4+ (install via: brew install make)
#   - cmake, git
#
# Usage:
#   cd native && bash build.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGC_TAG="v3.2"
AGC_REPO="https://github.com/refresh-bio/agc.git"
AGC_SRC_DIR="${SCRIPT_DIR}/agc-src"
OUTPUT_DIR="${SCRIPT_DIR}/lib"

echo "=== agckt native build ==="
echo "AGC tag:    ${AGC_TAG}"
echo "Source dir: ${AGC_SRC_DIR}"
echo "Output dir: ${OUTPUT_DIR}"
echo ""

# --- Detect tools ---
MAKE_CMD="make"
if command -v gmake &>/dev/null; then
    MAKE_CMD="gmake"
fi

UNAME_S="$(uname -s)"

# Detect real GCC (not Apple Clang).
# Priority: CC/CXX env vars → versioned g++-NN → Homebrew → plain g++ (if real GCC) → Linux fallback
CXX_CMD=""
CC_CMD=""

if [ -n "${CXX:-}" ] && [ -n "${CC:-}" ]; then
    CXX_CMD="${CXX}"
    CC_CMD="${CC}"
fi

if [ -z "${CXX_CMD}" ]; then
    for v in 15 14 13 12 11; do
        if command -v "g++-${v}" &>/dev/null; then
            CXX_CMD="g++-${v}"
            CC_CMD="gcc-${v}"
            break
        fi
        if [ -x "/opt/homebrew/bin/g++-${v}" ]; then
            CXX_CMD="/opt/homebrew/bin/g++-${v}"
            CC_CMD="/opt/homebrew/bin/gcc-${v}"
            break
        fi
    done
fi

if [ -z "${CXX_CMD}" ] && command -v g++ &>/dev/null; then
    if g++ --version 2>&1 | grep -qi "Free Software Foundation"; then
        CXX_CMD="g++"
        CC_CMD="gcc"
    fi
fi

if [ -z "${CXX_CMD}" ]; then
    if [ "${UNAME_S}" = "Linux" ]; then
        CXX_CMD="g++"
        CC_CMD="gcc"
    else
        echo "ERROR: Real GCC not found."
        echo "  Option 1: pixi run build-native   (installs GCC via conda-forge)"
        echo "  Option 2: brew install gcc"
        exit 1
    fi
fi

echo "Make:     ${MAKE_CMD}"
echo "CXX:      ${CXX_CMD}"
echo "CC:       ${CC_CMD}"
echo "Platform: ${UNAME_S} $(uname -m)"
echo ""

# --- Step 1: Clone AGC source ---
if [ ! -d "${AGC_SRC_DIR}" ]; then
    echo ">>> Cloning AGC ${AGC_TAG}..."
    git clone --depth 1 --branch "${AGC_TAG}" --recurse-submodules "${AGC_REPO}" "${AGC_SRC_DIR}"
else
    echo ">>> AGC source already present, skipping clone."
fi

# --- Step 2: Pre-build zlib-ng without tests (GCC 15+ has test compile issues) ---
ZLIB_NG_DIR="${AGC_SRC_DIR}/3rd_party/zlib-ng"
ZLIB_NG_BUILD="${ZLIB_NG_DIR}/build-g++/zlib-ng"
if [ ! -f "${ZLIB_NG_BUILD}/libz.a" ]; then
    echo ">>> Pre-building zlib-ng (tests disabled)..."
    cmake -DCMAKE_CXX_COMPILER="${CXX_CMD}" -DCMAKE_C_COMPILER="${CC_CMD}" \
        -B "${ZLIB_NG_BUILD}" -S "${ZLIB_NG_DIR}" \
        -DZLIB_COMPAT=ON -DZLIB_ENABLE_TESTS=OFF -DZLIBNG_ENABLE_TESTS=OFF \
        > /dev/null 2>&1
    cmake --build "${ZLIB_NG_BUILD}" --config Release \
        -j"$(nproc 2>/dev/null || sysctl -n hw.ncpu)" > /dev/null 2>&1
    echo ">>> zlib-ng built."
else
    echo ">>> zlib-ng already built, skipping."
fi

# --- Step 3: Build libagc.a ---
GCC_VERSION=$("${CXX_CMD}" -dumpversion | cut -d. -f1)
echo ">>> Building libagc.a with ${MAKE_CMD} (GCC ${GCC_VERSION})..."
cd "${AGC_SRC_DIR}"
${MAKE_CMD} libagc \
    CXX="${CXX_CMD}" CC="${CC_CMD}" \
    COMPILER_VERSION_GCC_Darwin_arm64_MAX=20 \
    COMPILER_VERSION_GCC_Darwin_x86_64_MAX=20 \
    COMPILER_VERSION_GCC_Linux_x86_64_MAX=20 \
    COMPILER_VERSION_GCC_Linux_aarch64_MAX=20 \
    -j"$(nproc 2>/dev/null || sysctl -n hw.ncpu)"

LIBAGC_A="${AGC_SRC_DIR}/bin/libagc.a"
if [ ! -f "${LIBAGC_A}" ]; then
    echo "ERROR: libagc.a not found at ${LIBAGC_A}"
    exit 1
fi
echo ">>> Built ${LIBAGC_A}"

# --- Step 4: Locate dependencies ---
LIBZSTD="${AGC_SRC_DIR}/3rd_party/zstd/lib/libzstd.a"
LIBZ="${ZLIB_NG_BUILD}/libz.a"
LIBDEFLATE="${AGC_SRC_DIR}/3rd_party/libdeflate/build/libdeflate.a"

for dep in "${LIBZSTD}" "${LIBZ}" "${LIBDEFLATE}"; do
    if [ ! -f "${dep}" ]; then
        echo "WARNING: dependency not found: ${dep}"
    fi
done

# --- Step 5: Link shared library ---
mkdir -p "${OUTPUT_DIR}"
echo ">>> Linking shared library..."

LINK_DEPS="${LIBZSTD}"
[ -f "${LIBZ}" ] && LINK_DEPS="${LINK_DEPS} ${LIBZ}"
[ -f "${LIBDEFLATE}" ] && LINK_DEPS="${LINK_DEPS} ${LIBDEFLATE}"

if [ "${UNAME_S}" = "Darwin" ]; then
    SHARED_LIB="libagc.dylib"
    ${CXX_CMD} -shared -o "${OUTPUT_DIR}/${SHARED_LIB}" \
        -Wl,-all_load "${LIBAGC_A}" \
        ${LINK_DEPS} \
        -lpthread
else
    SHARED_LIB="libagc.so"
    ${CXX_CMD} -shared -o "${OUTPUT_DIR}/${SHARED_LIB}" \
        -Wl,--whole-archive "${LIBAGC_A}" -Wl,--no-whole-archive \
        ${LINK_DEPS} \
        -lpthread -lm
fi

echo ">>> Built: ${OUTPUT_DIR}/${SHARED_LIB}"
ls -lh "${OUTPUT_DIR}/${SHARED_LIB}"

# --- Step 6: Copy to resources for JAR embedding ---
ARCH="$(uname -m)"
case "${UNAME_S}" in
    Darwin) OS_TAG="darwin" ;;
    Linux)  OS_TAG="linux" ;;
    *)      OS_TAG="$(echo "${UNAME_S}" | tr '[:upper:]' '[:lower:]')" ;;
esac
case "${ARCH}" in
    x86_64|amd64) ARCH_TAG="x86-64" ;;
    aarch64|arm64) ARCH_TAG="aarch64" ;;
    *) ARCH_TAG="${ARCH}" ;;
esac

RESOURCE_DIR="${SCRIPT_DIR}/../src/main/resources/native/${OS_TAG}-${ARCH_TAG}"
mkdir -p "${RESOURCE_DIR}"
cp "${OUTPUT_DIR}/${SHARED_LIB}" "${RESOURCE_DIR}/"

echo ""
echo "=== Build complete ==="
echo "Shared library: ${OUTPUT_DIR}/${SHARED_LIB}"
echo "JAR resource:   ${RESOURCE_DIR}/${SHARED_LIB}"
echo ""
echo "Verify exported symbols:"
nm -gU "${OUTPUT_DIR}/${SHARED_LIB}" | grep " _agc_"

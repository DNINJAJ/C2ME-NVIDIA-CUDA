# C2ME NVIDIA CUDA — Publishing Guide

This document is the release checklist for the CUDA addon.

## Project identity

- Display name: **C2ME NVIDIA CUDA**
- Fabric mod ID: `c2me-opts-accel-cuda`
- Maintainer: **Jospas**
- Base project and credits: **C2ME by RelativityMC / ishland**
- Minecraft: `26.2`
- Loader: Fabric
- Intended platform: NVIDIA GPUs with CUDA support

The Fabric mod ID is intentionally unchanged so that existing dependencies and installations remain compatible.

## What belongs in the public GitHub repository

Include the source tree, the module README, build files, license notices, and this publishing guide. Do not include:

- `build/` or `.gradle/` directories;
- Minecraft instance folders, logs, saves, or screenshots containing personal data;
- local absolute paths or machine-specific configuration;
- the CUDA SDK archive or DLLs unless their redistribution terms have been reviewed and the files are intentionally packaged.

The repository uses mixed licensing. Files inherited from C2ME retain their original headers and terms. CUDA-specific contributions are licensed according to their individual headers; do not replace the upstream notices with a blanket license.

## Release artifact for Modrinth or CurseForge

Upload the remapped JAR produced by:

```text
c2me-opts-accel-cuda/build/libs/c2me-fabric-opts-accel-cuda-mc26.2-<version>.jar
```

Do not upload `fabric.mod.json` separately: it is already inside the JAR. The source JAR is optional and should only be uploaded if it contains the same license notices and source intended for publication.

## Important runtime note

The addon uses NVIDIA's driver API and NVRTC to compile generated CUDA kernels. A public release must either document how users obtain the matching NVRTC runtime legally or include a separately reviewed runtime package. The NVIDIA display driver supplies `nvcuda.dll`; that is different from the NVRTC compiler DLLs.

The documented layout is:

```text
.minecraft/c2me-cuda/nvrtc/nvrtc64_130_0.dll
.minecraft/c2me-cuda/nvrtc/nvrtc-builtins64_133.dll
```

Users may alternatively set `-Dc2me.cuda.nvrtc.dir` to the directory containing those two files.

## Suggested first release

Use an **Alpha** or **Beta** release channel until users on more than one NVIDIA driver version have tested it. The changelog should state that this is an experimental CUDA backend, identify the required Minecraft/Fabric version, and link back to the source repository.

# Modrinth project description

## C2ME NVIDIA CUDA

An experimental NVIDIA CUDA backend for C2ME world generation on Fabric.

This addon offloads supported density-function and terrain-generation work to NVIDIA GPUs through CUDA while retaining C2ME's chunk-generation pipeline.

### Requirements

- Minecraft `26.2`
- Fabric Loader
- Java 25
- A compatible NVIDIA GPU and driver
- C2ME matching the release version
- The NVRTC runtime required by the release instructions

### Installation

1. Install the matching Fabric version and C2ME.
2. Install the CUDA backend JAR in the `mods` folder.
3. Follow the release's NVRTC setup instructions.
4. Start the game and check the log for `CUDA Device` initialization.
5. Back up worlds before testing experimental world-generation software.

### NVRTC setup for Windows users

The NVIDIA display driver alone is not enough. Follow these exact steps:

1. Download the official [CUDA NVRTC for Windows x86_64 package](https://developer.download.nvidia.com/compute/cuda/redist/cuda_nvrtc/windows-x86_64/cuda_nvrtc-windows-x86_64-13.0.48-archive.zip).
2. Open the ZIP and then open its `bin` folder.
3. Copy `nvrtc64_130_0.dll` and `nvrtc-builtins64_133.dll`.
4. In Prism Launcher, right-click your instance and choose **Folder**.
5. Open the instance's `minecraft` folder and create `c2me-cuda\nvrtc`.
6. Paste both DLLs into that folder. The final paths must be `minecraft/c2me-cuda/nvrtc/nvrtc64_130_0.dll` and `minecraft/c2me-cuda/nvrtc/nvrtc-builtins64_133.dll`.
7. Start Minecraft normally. Do not rename the DLLs.

The official NVIDIA package contains both files together. Advanced users can instead set `-Dc2me.cuda.nvrtc.dir` to the folder containing both DLLs.

### Status

This is an experimental community addon. It has been tested on an NVIDIA GeForce RTX 5060 with Minecraft `26.2`, but hardware, driver, and world-generation datapack compatibility may vary.

### Credits

Created and maintained by **Jospas**. Based on **C2ME by RelativityMC / ishland**. Please preserve these credits in derivative releases.

### Source and licensing

The source is available on GitHub. The module contains inherited C2ME code and CUDA-specific contributions under mixed licensing; see the repository's license notices and individual source headers.

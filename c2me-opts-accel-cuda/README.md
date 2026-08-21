# C2ME NVIDIA CUDA

CUDA backend for C2ME world generation on NVIDIA GPUs.

## Credits

Maintained by **Jospas**. This addon is based on C2ME by **RelativityMC / ishland**, Please retain these credits in any redistribution or derivative release.

## Licensing

This module contains a mixture of original C2ME code and CUDA-specific contributions. The original C2ME files retain the license notices provided by their authors. CUDA-specific files contributed by Jospas are intended to be released under the MIT License where their file headers identify that license.

See the repository-level license files and individual source headers for the authoritative terms. This project is non-commercial and community-maintained.

## Compatibility

The Fabric mod ID remains `c2me-opts-accel-cuda` for compatibility with existing dependencies and installations. The public display name is **C2ME NVIDIA CUDA**.

## NVIDIA runtime setup

The NVIDIA display driver provides `nvcuda.dll`, but the addon also needs the two NVRTC runtime files used to compile its generated kernels:

```text
c2me-cuda/nvrtc/nvrtc64_130_0.dll
c2me-cuda/nvrtc/nvrtc-builtins64_133.dll
```

Download the official [CUDA NVRTC for Windows x86_64 package](https://developer.download.nvidia.com/compute/cuda/redist/cuda_nvrtc/windows-x86_64/cuda_nvrtc-windows-x86_64-13.0.48-archive.zip), open its `bin` folder, and copy `nvrtc64_130_0.dll` and `nvrtc-builtins64_133.dll` into `minecraft/c2me-cuda/nvrtc/` inside your instance. In Prism Launcher, right-click the instance and choose **Folder** to find it. Do not rename the DLLs. Advanced users can set `-Dc2me.cuda.nvrtc.dir=<absolute-path-to-the-folder>` instead.

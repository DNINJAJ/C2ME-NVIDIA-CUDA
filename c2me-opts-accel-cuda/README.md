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

Place that folder under the Minecraft instance directory, or set the JVM property `-Dc2me.cuda.nvrtc.dir=<absolute-path-to-the-folder>`. Obtain the files from a compatible NVIDIA CUDA Toolkit/runtime distribution and follow its redistribution terms.

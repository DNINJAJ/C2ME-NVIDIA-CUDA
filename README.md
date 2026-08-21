# C2ME NVIDIA CUDA

## English

Community addon that adds an NVIDIA CUDA backend to C2ME for accelerating parts of Minecraft world generation.

This project started as a fun experiment because the author was bored, and is maintained by **Jospas**.

### Credits

This addon is based on **C2ME**, created and maintained by **RelativityMC / ishland**. Please preserve the original project credits and license notices.

- Original project: [RelativityMC/C2ME-fabric](https://github.com/RelativityMC/C2ME-fabric)
- CUDA addon author: **Jospas**

### Compatibility

- Minecraft 26.2
- Fabric
- Java 25
- CUDA-compatible NVIDIA GPU

The mod ID remains `c2me-opts-accel-cuda` for compatibility with existing installations. The displayed name is **C2ME NVIDIA CUDA**.

### NVIDIA runtime

In addition to the NVIDIA driver, the addon needs these NVRTC files:

```text
c2me-cuda/nvrtc/nvrtc64_130_0.dll
c2me-cuda/nvrtc/nvrtc-builtins64_133.dll
```

Place them in the Minecraft instance directory or use `-Dc2me.cuda.nvrtc.dir=<absolute-path>`.

### Building

```text
./gradlew :c2me-opts-accel-cuda:build
```

The license terms for original files and CUDA contributions are indicated in the files themselves and in `LICENSE-NOTICE.md`.

---

## Português

Addon comunitário que adiciona um backend NVIDIA CUDA ao C2ME para acelerar partes da geração de mundo do Minecraft.

Este projeto começou como um experimento feito por diversão, porque o autor estava entediado, e é mantido por **Jospas**.

### Créditos

Este addon é baseado no **C2ME**, criado e mantido por **RelativityMC / ishland**. Preserve os créditos do projeto original e os avisos de licença.

- Projeto original: [RelativityMC/C2ME-fabric](https://github.com/RelativityMC/C2ME-fabric)
- Autor do addon CUDA: **Jospas**

### Compatibilidade

- Minecraft 26.2
- Fabric
- Java 25
- GPU NVIDIA compatível com CUDA

O ID do mod continua sendo `c2me-opts-accel-cuda` para manter a compatibilidade com instalações existentes. O nome exibido é **C2ME NVIDIA CUDA**.

### Runtime NVIDIA

Além do driver NVIDIA, o addon precisa destes arquivos NVRTC:

```text
c2me-cuda/nvrtc/nvrtc64_130_0.dll
c2me-cuda/nvrtc/nvrtc-builtins64_133.dll
```

Coloque-os na pasta da instância do Minecraft ou use `-Dc2me.cuda.nvrtc.dir=<caminho-absoluto>`.

### Compilação

```text
./gradlew :c2me-opts-accel-cuda:build
```

Os termos de licença dos arquivos originais e das contribuições CUDA estão indicados nos próprios arquivos e em `LICENSE-NOTICE.md`.

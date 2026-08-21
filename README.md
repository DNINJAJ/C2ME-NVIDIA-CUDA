# C2ME NVIDIA CUDA

Addon comunitário que adiciona um backend NVIDIA CUDA ao C2ME para acelerar partes da geração de mundo no Minecraft.

Este projeto nasceu como um experimento feito por diversão, porque o autor estava entediado, e é mantido por **Jospas**.

## Créditos

Este addon é baseado no **C2ME**, criado e mantido por **RelativityMC / ishland**. Todos os créditos do projeto original e seus avisos de licença devem ser preservados.

- Projeto original: [RelativityMC/C2ME-fabric](https://github.com/RelativityMC/C2ME-fabric)
- Autor do addon CUDA: **Jospas**

## Compatibilidade

- Minecraft 26.2
- Fabric
- Java 25
- GPU NVIDIA compatível com CUDA

O ID do mod continua sendo `c2me-opts-accel-cuda` para manter a compatibilidade com as instalações existentes. O nome exibido é **C2ME NVIDIA CUDA**.

## Runtime NVIDIA

Além do driver NVIDIA, o addon precisa dos arquivos NVRTC:

```text
c2me-cuda/nvrtc/nvrtc64_130_0.dll
c2me-cuda/nvrtc/nvrtc-builtins64_133.dll
```

Coloque-os na pasta da instância do Minecraft ou use `-Dc2me.cuda.nvrtc.dir=<caminho-absoluto>`.

## Compilação

```text
./gradlew :c2me-opts-accel-cuda:build
```

Os termos de licença dos arquivos originais e das contribuições CUDA estão indicados nos próprios arquivos e em `LICENSE-NOTICE.md`.

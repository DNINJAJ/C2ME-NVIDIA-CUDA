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

### Required mods

Install these together:

1. [Fabric API](https://modrinth.com/mod/fabric-api)
2. [C2ME for Fabric](https://modrinth.com/mod/c2me-fabric), using the Minecraft `26.2` release
3. The separate **C2ME DFC** file from that same release. Its full name is **Concurrent Chunk Management Engine (Optimizations/Density Function Compiler)** and its filename starts with `c2me-fabric-opts-dfc-mc26.2-`.
4. **C2ME NVIDIA CUDA**, the addon from this project

On the C2ME Modrinth page, open the `26.2` release and download both the main C2ME file and the separate file containing `opts-dfc` in its name. Do not install the OpenCL addon for this CUDA setup.

### NVIDIA runtime

The NVIDIA display driver alone is not enough. The addon needs two NVRTC files to compile its CUDA kernels.

#### Easy Windows setup

1. Download the official NVIDIA NVRTC package: [CUDA NVRTC for Windows x86_64](https://developer.download.nvidia.com/compute/cuda/redist/cuda_nvrtc/windows-x86_64/cuda_nvrtc-windows-x86_64-13.0.48-archive.zip).
2. Open the downloaded ZIP file.
3. Inside the ZIP, open the `bin` folder and copy these two files:

```text
nvrtc64_130_0.dll
nvrtc-builtins64_133.dll
```

4. Open your Prism Launcher instance folder. In Prism, right-click the instance and choose **Folder**.
5. Inside the instance's `minecraft` folder, create these folders:

```text
minecraft/c2me-cuda/nvrtc/
```

6. Put both DLLs inside that `nvrtc` folder. The final paths must be:

```text
minecraft/c2me-cuda/nvrtc/nvrtc64_130_0.dll
minecraft/c2me-cuda/nvrtc/nvrtc-builtins64_133.dll
```

7. Start Minecraft normally. Do not rename the DLLs.

The official NVIDIA package contains the two DLLs together; NVIDIA does not provide them as separate individual download pages. The package is for Windows x86_64 and CUDA 13.0. NVIDIA's [NVRTC documentation](https://docs.nvidia.com/cuda/archive/13.0.0/nvrtc/index.html) describes the Windows DLL layout.

Advanced users can instead set `-Dc2me.cuda.nvrtc.dir=<absolute-path-to-the-folder>` as a JVM argument, pointing directly to the folder containing both DLLs.

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

### Mods obrigatórios

Instale estes juntos:

1. [Fabric API](https://modrinth.com/mod/fabric-api)
2. [C2ME para Fabric](https://modrinth.com/mod/c2me-fabric), usando a versão do Minecraft `26.2`
3. O arquivo separado **C2ME DFC** da mesma versão. O nome completo é **Concurrent Chunk Management Engine (Optimizations/Density Function Compiler)** e o nome do arquivo começa com `c2me-fabric-opts-dfc-mc26.2-`.
4. **C2ME NVIDIA CUDA**, o addon deste projeto

Na página do C2ME no Modrinth, abra a versão `26.2` e baixe tanto o arquivo principal do C2ME quanto o arquivo separado que contém `opts-dfc` no nome. Não instale o addon OpenCL nesta configuração CUDA.

### Runtime NVIDIA

O driver NVIDIA sozinho não é suficiente. O addon precisa de dois arquivos NVRTC para compilar os kernels CUDA.

#### Instalação fácil no Windows

1. Baixe o pacote oficial NVRTC da NVIDIA: [CUDA NVRTC para Windows x86_64](https://developer.download.nvidia.com/compute/cuda/redist/cuda_nvrtc/windows-x86_64/cuda_nvrtc-windows-x86_64-13.0.48-archive.zip).
2. Abra o arquivo ZIP baixado.
3. Dentro do ZIP, abra a pasta `bin` e copie estes dois arquivos:

```text
nvrtc64_130_0.dll
nvrtc-builtins64_133.dll
```

4. Abra a pasta da instância no Prism Launcher. No Prism, clique com o botão direito na instância e escolha **Pasta**.
5. Dentro da pasta `minecraft` da instância, crie:

```text
minecraft/c2me-cuda/nvrtc/
```

6. Coloque os dois DLLs dentro dessa pasta. Os caminhos finais devem ser:

```text
minecraft/c2me-cuda/nvrtc/nvrtc64_130_0.dll
minecraft/c2me-cuda/nvrtc/nvrtc-builtins64_133.dll
```

7. Inicie o Minecraft normalmente. Não renomeie os DLLs.

O pacote oficial da NVIDIA contém os dois DLLs juntos; a NVIDIA não oferece páginas separadas de download para cada arquivo. O pacote é para Windows x86_64 e CUDA 13.0. A [documentação NVRTC da NVIDIA](https://docs.nvidia.com/cuda/archive/13.0.0/nvrtc/index.html) explica a estrutura dos DLLs no Windows.

Usuários avançados podem usar `-Dc2me.cuda.nvrtc.dir=<caminho-absoluto-da-pasta>` como argumento da JVM, apontando diretamente para a pasta que contém os dois DLLs.

### Compilação

```text
./gradlew :c2me-opts-accel-cuda:build
```

Os termos de licença dos arquivos originais e das contribuições CUDA estão indicados nos próprios arquivos e em `LICENSE-NOTICE.md`.

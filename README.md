# FM Fusion Overlay (MVP)

Assistente Android experimental para **Yu-Gi-Oh! Forbidden Memories (PS1)** rodando no DuckStation.

## O que faz

- Lê localmente a ROM BIN/ISO escolhida pelo usuário.
- Extrai `SLUS_014.11` e `WA_MRG.MRG` diretamente do ISO9660 do disco.
- Gera as 722 miniaturas 40x32 e carrega a tabela de fusões da própria ROM.
- Usa MediaProjection para capturar a tela do Android.
- Assume o layout padrão 4:3 do Forbidden Memories e reconhece as 5 artes da mão por correlação de miniaturas.
- Mostra um botão flutuante `FM`; ao tocar, exibe as fusões e cadeias possíveis, ordenadas por ATK do resultado.
- Não envia a tela ou a ROM para a internet.

## Compatibilidade inicial

A primeira versão foi escrita para a edição NTSC-U que contém `SLUS_014.11`. O layout de reconhecimento foi calibrado com DuckStation em tela cheia e viewport 4:3 usando as posições nativas das cartas `(x=30+60*n, y=157, 40x32)`.

## Compilar

Abra a pasta no Android Studio (JDK 17 / Android SDK 35) e execute **Build > Build APK(s)**.

Também há um workflow em `.github/workflows/build-apk.yml` que compila `app-debug.apk` no GitHub Actions.

## Instalação / uso

1. Instale o APK.
2. Abra o app e escolha o mesmo `.bin`/`.iso` usado pelo DuckStation.
3. Aguarde `722 cartas prontas`.
4. Conceda a permissão de sobreposição.
5. Toque em **Iniciar assistente** e aceite a captura de tela.
6. Abra o DuckStation e entre num duelo.
7. Com as cinco cartas visíveis, toque no botão flutuante **FM**.

## Observações

Este é um MVP e pode precisar de pequenos ajustes de posição/limiar em filtros gráficos específicos do DuckStation. O app usa dados extraídos da ROM do próprio usuário e não distribui ROM, BIOS ou artes do jogo.

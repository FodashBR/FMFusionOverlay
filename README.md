# FM Fusion Overlay (MVP)

Assistente Android experimental para **Yu-Gi-Oh! Forbidden Memories (PS1)** rodando no DuckStation.

## Edição Europe SLES_039.47 (CHD)

No caso da edição Europe em CHD, importe o arquivo `.fmf` preparado a partir do disco do usuário.
O DuckStation continua usando o CHD original; o app armazena apenas os dados necessários
para identificar as 722 cartas e consultar suas fusões. O arquivo `.fmf` contém miniaturas
extraídas da cópia do usuário e deve ser mantido privado, fora do repositório público.
As miniaturas dessa edição usam blocos de 0x4000 bytes e começam em 0x169000 + 0x3560
no `WA_MRG.MRG`. As regras de fusão desta edição ficam em 0xDEB000; os atributos ficam no
executável SLES_039.47 em 0x1C4A44. O nome das cartas no arquivo `.fmf` foi associado
por número usando um catálogo de referência.

## O que faz

- Importa um arquivo `.fmf` da edição Europe ou lê uma ROM BIN/ISO NTSC-U escolhida pelo usuário.
- Para a opção NTSC-U BIN/ISO, extrai `SLUS_014.11` e `WA_MRG.MRG` diretamente do ISO9660 do disco.
- Gera as 722 miniaturas 40x32 e carrega a tabela de fusões da própria ROM.
- Usa MediaProjection para capturar a tela do Android.
- Usa o layout 4:3 do Forbidden Memories e busca pequenas variações de posição das cinco cartas antes de comparar as miniaturas.
- Mostra um botão flutuante `FM`; ao tocar, exibe as fusões e cadeias possíveis, ordenadas por ATK do resultado.
- Permite registrar manualmente qual carta da mão foi colocada em cada uma das cinco posições da sua mesa, retirar cartas e limpar a mesa ao começar outro duelo. Esse registro dura enquanto o assistente estiver em execução.
- Compara cada carta registrada na sua mesa com as cartas disponíveis na mão e exibe as fusões possíveis, inclusive cadeias de duas ou mais cartas, com a carta da mesa como primeiro ingrediente.
- Após realizar uma fusão sugerida entre mesa e mão, permite tocar no resultado para substituir a carta registrada naquela posição e marcar os ingredientes da mão como usados.
- Não envia a tela ou a ROM para a internet.

## Compatibilidade inicial

O suporte à edição europeia foi conferido com uma captura da partida em DuckStation. O layout de reconhecimento usa viewport 4:3 e as posições nativas das cartas `(x=30+60*n, y=157, 40x32)`, com busca de pequenos deslocamentos. A precisão em outras telas ou filtros gráficos ainda precisa ser testada no aparelho.

## Compilar

Abra a pasta no Android Studio (JDK 17 / Android SDK 35) e execute **Build > Build APK(s)**.

Também há um workflow em `.github/workflows/build-apk.yml` que compila `app-debug.apk` no GitHub Actions.

## Instalação / uso

1. Instale o APK.
2. Para a edição Europe em CHD, importe o `.fmf` fornecido separadamente. Para NTSC-U, selecione o `.bin`/`.iso`. O cache europeu antigo, gerado antes da correção da tabela de fusões, é rejeitado; importe o arquivo com nome `-corrigido.fmf`.
3. Aguarde `722 cartas prontas`.
4. Conceda a permissão de sobreposição.
5. Toque em **Iniciar assistente** e aceite a captura de tela.
6. Abra o DuckStation e entre num duelo.
7. Com as cinco cartas visíveis, toque no botão flutuante **FM**.
8. Se a identificação ficar errada, toque em **Salvar captura para ajuste** e compartilhe a imagem salva em `Imagens/FM Fusion Overlay` para calibração. A imagem é salva no celular somente quando você pedir.
9. Antes de atualizar a captura da mão, toque numa posição vazia da mesa e escolha a carta que jogou. Ela passa a ser considerada jogada, sai das combinações da mão e é usada nas fusões com a mesa. Toque numa posição ocupada para remover a carta quando ela sair do campo. Ao começar outro duelo, use **Novo duelo · limpar mesa**.
10. A seção **Fusões com cartas da mesa** indica a posição e a sequência a usar. Depois de fazer a fusão no jogo, toque no resultado sugerido para atualizar o registro da mesa; caso a mão mude, toque em **FM** para capturá-la novamente.

## Observações

Este é um MVP e pode precisar de pequenos ajustes de posição/limiar em filtros gráficos específicos do DuckStation. O app usa dados extraídos da ROM do próprio usuário e não distribui ROM, BIOS ou artes do jogo.
O registro da mesa ainda depende da seleção do jogador: a captura de uma mão, sozinha, não informa em qual casa a carta foi jogada. As fusões com a mesa usam apenas as suas cartas registradas e as cartas disponíveis na última captura da mão. Não é necessário identificar cartas do adversário para consultar fusões próprias.

# Chat Heads — Minecraft 1.8.9 (Forge 11.15.1.2318)

Mostra a cabeça do remetente ao lado de cada mensagem do chat. 100% client-side: não instala nada no servidor e não
envia nenhum pacote extra. Reimplementação para a arquitetura do 1.8.9 (sem Mixin, sem coremod, sem ASM).

## STATUS HONESTO — leia antes de usar

| Item | Estado |
|---|---|
| Código-fonte completo (parser, identificação, cache, render, GUI, config, comandos) | escrito |
| Lógica pura (parser + pontuação de confiança + offsets de texto) | **testada**: 27/27 casos (`LogicSmokeTest`) |
| Compilação contra Forge 1.8.9 (`gradle build`) | **NÃO executada** — o ambiente em que isto foi escrito não alcança os servidores do Forge e só tem Java 21 |
| Execução no jogo (SP/MP/OptiFine/outros mods) | **NÃO testada** |
| JAR final | **não gerado** (gere com `gradle build`, abaixo) |
| Análise do `chat_heads-0.15.7-forge-1.20.jar` | **NÃO feita** — o arquivo não chegou (pasta de uploads vazia). Reenvie se quiser que eu compare |

A implementação foi escrita contra a API do MCP `stable_22` / 1.8.9 de memória e **precisa do primeiro build para
validar nomes de símbolos**. Veja "Pontos a validar no primeiro build".

## Como compilar

Requisitos: **JDK 8** e **Gradle 2.14.1** (ForgeGradle 2.1 não roda em Gradle mais novo).

```
gradle wrapper --gradle-version 2.14.1     # gera gradlew + wrapper jar (uma vez)
./gradlew setupDecompWorkspace             # baixa Forge/MCP e decompila (demora)
./gradlew build                            # gera build/libs/ChatHeads-1.8.9-1.0.0.jar
./gradlew runClient                        # abre o Minecraft de desenvolvimento
```
Instalação: coloque o JAR em `.minecraft/mods` com o Forge 1.8.9 (11.15.1.2318).
Se o ForgeGradle 2.1 reclamar de repositórios (jcenter/files.minecraftforge.net são legados), me envie o log.

## Uso

- **Mods → Chat Heads → Config**, ou `/chatheads` (abre a tela), ou uma tecla (em Controles → Misc; vem sem tecla).
- `/chatheads on|off|status|why|debug|reload|clearcache`
- `/chatheads nick <apelido> <jogador|uuid>` — associa manualmente um apelido do servidor a um jogador;
  `/chatheads unnick <apelido>`, `/chatheads nicks`.
- Arquivos: `config/chatheads.cfg` (opções, inclui `minConfidence`), `config/chatheads_nicknames.properties`.

## Arquitetura

```
ChatHeads.java                    @Mod (clientSideOnly), ciclo de vida
client/ChatHeadsClient            singletons, ticks, atalho, isolamento de falhas (5 erros => desliga a si mesmo)
chat/ChatHook                     troca GuiIngame.persistantChatGUI por HeadsGuiNewChat (uma vez)
chat/HeadsGuiNewChat              ÚNICO ponto de integração com o chat vanilla
chat/ChatMessageParser            (puro) acha o "cabeçalho" da linha e os tokens candidatos a nome
chat/ChatMessageHandler           mensagem -> HeadPlan (ou null = sem cabeça)
chat/ComponentUtil                reserva o espaço da cabeça preservando cliques/hover/cores
chat/ChatLineManager              marca qual linha desenhada leva cabeça (mapas fracos, sem vazamento)
player/IdentityScorer + ConfidenceModel   (puros) escolha do remetente e nível de confiança
player/PlayerIdentityResolver     manual > nome real na TAB > apelido (display name da TAB)
player/NicknameMapper             apelidos manuais persistidos
cache/PlayerCache                 índice TAB (+ entidades próximas) com remoção LRU e limpeza ao desconectar
cache/SkinCache                   skins de jogadores offline (só para apelidos manuais), em threads de fundo
render/HeadRenderer               desenho do rosto + hat layer
render/SkinTextureManager         escolhe a textura a cada frame, sem I/O
config/*                          Configuration do Forge, tela de config, GuiFactory
```

### Decisões técnicas

1. **Subclasse de `GuiNewChat` instalada por reflexão, sem ASM.** 1.8.9 não tem Mixin nativo e nenhum evento Forge
   permite desenhar por linha do chat. A subclasse sobrescreve só 3 métodos. Só substituímos um `GuiNewChat` puro:
   se outro mod já trocou o chat, **não brigamos** (log + status `inactive`).
2. **O desenho vanilla não é copiado.** `drawChat` chama `super.drawChat` (tudo igual ao vanilla) e depois desenha só as
   cabeças por cima, com o mesmo `translate/scale`, a mesma conta de fade e a mesma opacidade. Rolagem, barra de
   rolagem, escala, largura, cores, links e hover continuam sendo do vanilla.
3. **O espaço da cabeça é texto (espaços), não deslocamento de desenho.** Assim quebra de linha, largura do fundo e
   hit-test de clique/hover são calculados pelo próprio vanilla com a geometria real. O componente é achatado com o
   iterador do `IChatComponent` (estilos efetivos) e remontado, preservando `ClickEvent`/`HoverEvent`.
4. **Skins de jogadores online vêm do `NetworkPlayerInfo`** (vanilla já baixa, guarda em disco e sobe a textura; devolve
   a skin padrão do UUID até a real chegar, então a cabeça atualiza sozinha). Não há download nosso na thread de render.
   O `SkinCache` só busca (em 2 threads daemon, com dedupe e backoff de 10 min) skins de apelidos manuais offline.
5. **Cabeça segue a skin real mesmo depois que o jogador sai**: guardamos a última textura real vista.
6. **Altura da textura**: `steve.png` do 1.8.9 é 64x32 e skins de rede são 64x64; o V é normalizado pela altura real
   (consultada uma vez por textura) em vez de assumir 64x64.

### Identificação e confiança

Só o **cabeçalho** (texto antes do separador `: » › → >> -> > - |`) é analisado, nunca o corpo. Grupos entre `[] () {}`
são tratados como rank/tag, nunca como nome. Confiança: apelido manual 1.00; nome real exato 0.95; case-insensitive
0.90; apelido na TAB 0.85; penalidades por palavras soltas antes/depois do nome, nome ≤3 letras, dois jogadores no
cabeçalho. Abaixo de `minConfidence` (padrão 0.85) **não há cabeça** — nunca se atribui skin de outro jogador.
`Unknown Players` (padrão OFF) mostra um quadrado cinza neutro, jamais uma skin.

## Diferenças em relação ao mod moderno (1.20)

*Baseado no que é conhecido sobre as duas plataformas, não numa leitura do JAR (que não foi enviado):*
- 1.20 tem mensagens assinadas com UUID do remetente; 1.8.9 só envia texto (`S02PacketChat`) => precisamos de
  parser + TAB + confiança.
- 1.20 usa Mixin/`PlayerInfo`/`PlayerSkin`; 1.8.9 usa `NetworkPlayerInfo`/`ResourceLocation` e GL de função fixa.
- Config moderna (Cloth/Forge Config Spec) => aqui `Configuration` + `GuiScreen` próprio.

## Pontos a validar no primeiro build (suposições feitas sem poder compilar)

1. Nomes SRG em `ChatReflection`: `field_146253_i` (drawnChatLines), `field_146252_h` (chatLines), `field_146248_b`
   (sentMessages), `field_146250_j` (scrollPos), `field_146251_k` (isScrolled), `field_73840_e` (persistantChatGUI).
   Se o nome falhar, o mod procura o campo por tipo/posição (e registra um WARN no log); só se isso também
   falhar ele fica `inactive` (`/chatheads status`). *Confirmado em teste real: `field_146248_b` estava errado
   (o correto é `field_146248_g`); a v1.0.1 já não depende disso.*
2. `GuiUtilRenderComponents.splitText(...)` (usado em `ChatLineManager.rebuild`).
3. Origem do chat no vanilla: `translate(2, 20, 0)` (`HeadsGuiNewChat.ORIGIN_X/Y`). Se as cabeças saírem 1–2 px
   deslocadas, ajuste essas constantes.
4. Assinaturas: `IModGuiFactory`, `CommandBase.processCommand`, `NetHandlerPlayClient.getPlayerInfo(UUID)`,
   `SkinManager.SkinAvailableCallback`, `Minecraft.getSessionService()`.

## Roteiro de testes (executar no jogo)

1. **Singleplayer**: abrir mundo, falar no chat (`<Steve> oi`), `/help`, `/give` => só a fala ganha cabeça; mensagens do sistema não.
2. **Multiplayer vanilla/Spigot/Paper** sem o mod no servidor: vários jogadores; use `/chatheads debug` + `/chatheads why` para ver cada decisão.
3. **Formatos**: `Steve: x`, `[VIP] Steve: x`, `<Steve> x`, `[MVP+] Steve » x`, `Guild Steve: x`, cores `§`, nick sem TAB (use `/chatheads nick`).
4. **Interface**: abrir chat, rolar, aumentar/diminuir largura/escala/opacidade nas opções do jogo, links clicáveis, mensagem longa (multilinha), redimensionar a janela (testa `refreshChat`).
5. **Desempenho**: servidor cheio; FPS com e sem o mod; `SkinCache` nunca deve aparecer na thread "Client thread".
6. **Compatibilidade**: com OptiFine HD U M5, com mod de chat (se o status virar `inactive: chat already replaced by ...` é o comportamento esperado).
7. Entrar/sair de vários servidores (cache é limpo ao desconectar) e verificar `latest.log` sem exceções de "ChatHeads".

## Limitações conhecidas

- Alterar tamanho/posição/ligar-desligar afeta **mensagens novas**; linhas já no chat mantêm o espaçamento antigo.
- O log `[CHAT]` do vanilla registra o texto com os espaços reservados.
- Servidores que escondem jogadores da TAB (e fora do alcance de renderização) não permitem identificar o remetente => sem cabeça; use `/chatheads nick`.
- "You killed Steve: ..." e similares são rejeitados por baixa confiança (de propósito); formatos sem separador (`Steve hello`) não são reconhecidos.
- Se outro mod já substituiu o `GuiNewChat`, o Chat Heads fica inativo em vez de conflitar.
- Tamanho da cabeça limitado a 6–9 px (a linha do chat tem 9 px).
- A busca por nome de apelidos manuais offline usa a API pública da Mojang (`api.mojang.com`), só para nomes que **você** mapeou.

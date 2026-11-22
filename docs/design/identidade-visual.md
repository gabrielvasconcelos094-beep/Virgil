# Identidade visual do Virgil

**Conceito:** uma biblioteca pessoal silenciosa. Papel, tinta, encadernação em tecido verde, uma fita
marcadora cor de terracota. O nome remete a Virgílio, o guia de Dante: alguém que acompanha a
travessia de um texto difícil sem tomar o lugar do leitor.

## Marca e ícone

Um livro aberto visto de frente. As duas páginas, inclinadas para cima a partir da lombada, desenham
um **V** no espaço entre elas; a fita marcadora em terracota pende da página direita, marcando onde a
leitura parou. Fundo verde-floresta com uma vinheta discreta na base (sem gradientes chamativos).
Há versão monocromática para ícones temáticos (Android 13+) e versões da marca para a tela de
abertura (páginas verdes sobre papel no tema claro; páginas claras sobre a noite no escuro).
Arquivos: `app/src/main/res/drawable/ic_launcher_*.xml`, `ic_splash.xml`.

## Paletas

| Token | Paper & Forest (claro) | Midnight Library (escuro) | Uso |
|---|---|---|---|
| background | `#F4F0E7` | `#181E1B` | Fundo das telas e do leitor |
| surface | `#FBF8F1` | `#232B26` | Folhas, cartões discretos |
| surfaceSunken | `#EDE8DC` | `#141917` | Indicador da navegação, campos |
| primary | `#30483C` verde-floresta | `#9BAF91` verde principal | Ações, seleção, dados |
| secondary | `#84917A` sálvia | `#71836D` | Preenchimentos, barras |
| secondaryInk | `#66735D` | `#8A9C85` | Sálvia **quando vira texto** |
| accent | `#B77955` terracota | `#CE956F` | Fita, palavra selecionada, níveis altos |
| accentInk | `#9A5F3E` | `#CE956F` | Terracota **quando vira texto** |
| textPrimary | `#292D28` | `#EAE7DE` | Texto de interface |
| textSecondary | `#6B6A61` | `#AAA99F` | Metadados |
| textReading | `#292D28` | `#DEDACF` | Corpo do livro |
| divider | `#DDD6C9` | `#39423B` | Filetes |

### Refinamentos propostos (e por quê)

Contrastes calculados pela fórmula WCAG 2.x:

| Combinação | Original | Refinado | Motivo |
|---|---|---|---|
| Texto secundário sobre fundo claro | `#77766D` → 4,0:1 | `#6B6A61` → 4,8:1 | Atingir AA (4,5:1) para texto pequeno |
| Sálvia como texto (claro) | `#84917A` → 2,9:1 | `#66735D` → 4,4:1 | Sálvia original fica para preenchimentos |
| Terracota como texto (claro) | `#B77955` → 3,1:1 | `#9A5F3E` → 4,5:1 | Idem |
| Verde secundário como texto (escuro) | `#71836D` → 4,2:1 | `#8A9C85` → 5,8:1 | AA no escuro |
| Corpo de leitura no escuro | `#EAE7DE` → 13,7:1 | `#DEDACF` → 12,1:1 | Menos halo em leitura longa, ainda muito acima de AAA |

O tema escuro não é uma inversão do claro: a superfície fica *mais clara* que o fundo (como uma folha
sob uma luminária), o texto de leitura é levemente atenuado e a terracota ganha luminosidade para
continuar visível sem saturar.

## Tipografia

| Papel | Fonte | Licença |
|---|---|---|
| Leitura (padrão) | **Literata** — desenhada para leitura longa em telas | OFL 1.1 |
| Leitura (alternativas) | Source Serif 4, EB Garamond, Inter | OFL 1.1 |
| Títulos editoriais | Literata SemiBold | OFL 1.1 |
| Interface | **Inter** | OFL 1.1 |

Todas embarcadas (≈5 MB no total), sem download em tempo de execução. Textos das licenças em
`docs/licenses/`. O leitor permite ajustar fonte, tamanho, entrelinha, espaço entre parágrafos,
margens, alinhamento (esquerda ou justificado) e hifenização.

## Princípios de interface

- Hierarquia por peso e espaçamento, não por cor ou tamanho exagerado.
- Cantos de 4–14 dp; filetes de 1 dp em vez de sombras.
- Animações curtas (esmaecimento de 120–180 ms); nada de saltos ou confetes.
- Estados vazios com uma frase calma, sem ilustrações infantis.
- Capas ausentes viram **capas tipográficas** determinísticas (mesmo título, mesma capa), em seis
  combinações derivadas da paleta, com filete duplo evocando a lombada de uma edição em tecido.
- Dificuldade por capítulo como uma faixa de barras finas: sálvia (até B1), verde (B2), terracota (C1+).

## Capturas (Fase 1)

Geradas pelos testes Roborazzi (`./gradlew test`) a partir das telas reais com dados de exemplo:
[biblioteca clara](capturas/biblioteca-claro.png) · [biblioteca escura](capturas/biblioteca-escuro.png) ·
[vocabulário claro](capturas/vocabulario-claro.png) · [vocabulário escuro](capturas/vocabulario-escuro.png) ·
[configurações](capturas/configuracoes-claro.png) · [capas e mapa de dificuldade](capturas/capas-e-mapa-claro.png)

Pendência de polimento: o `Slider` padrão do Material 3 tem trilho espesso demais para a linguagem do
Virgil; será substituído por um trilho fino personalizado.

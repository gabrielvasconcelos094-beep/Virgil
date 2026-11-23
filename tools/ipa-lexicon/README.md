# Léxicos de pronúncia (IPA)

Gera `engine/speech/src/main/assets/ipa/{en,fr,de,it,es,pt}.tsv.gz`, usados pelo cartão de consulta.

**Fonte:** Wiktionary em inglês (en.wiktionary.org), extraído pelo projeto wiktextract (Tatu Ylonen),
no dump mensal publicado em `huggingface.co/datasets/DataDock/wiktextract` (arquivo `2026-10-01.jsonl.gz`).
Licença dos dados: **CC BY-SA 4.0**; os arquivos gerados são adaptações sob a mesma licença
(atribuição na tela Licenças do app).

Fontes avaliadas e descartadas:
- *ipa-dict*: inglês sob GPL-3, acentuação fora do padrão e palavras comuns ausentes ("read");
  espanhol gerado por regra, sem acento.
- *WikiPron*: remove as marcas de acento (ˈ ˌ), essenciais para o aprendiz.
- Transcrição pelo próprio TextToSpeech do Android: a API pública não expõe fonemas.
- Geração por regras ortográficas (eSpeak etc.): proibida pelo requisito "não invente IPA".

## Como regenerar

```bash
python extract.py <saida>          # filtra o dump (3 GB, streaming) para os seis idiomas
python build.py <saida> engine/speech/src/main/assets/ipa      # escolhe variante, grava, mede cobertura
python coverage_books.py engine/speech/src/main/assets/ipa <pasta-de-livros> coverage-books.json
```

`extract.py` lê o dump em streaming e guarda, por verbete, palavra, classe, transcrições
(`ipa`, `tags`, `note`, `raw_tags`) e `form_of`.

## Regras

Ver o cabeçalho de `build.py`. Em resumo: só transcrições escritas pelos editores; variante do Virgil
(RP/UK, Brasil, castelhano, alemão padrão, França, italiano padrão); fonêmica `/…/` sempre que
existir, senão a fonética `[…]` com colchetes; homógrafos com alternativas por classe gramatical.

## Cobertura medida (nov/2026)

| Idioma | Palavras | Tamanho | Top 1000 | Top 5000 | Tokens (wordfreq) | Tokens em 40 livros do Gutenberg |
|---|---|---|---|---|---|---|
| inglês (RP) | 86 287 | 738 KB | 99,4% | 95,1% | 95,9% | 93,9% |
| francês | 104 672 | 632 KB | 97,1% | 87,0% | 93,2% | 85,7% |
| alemão | 75 295 | 698 KB | 88,4% | 70,9% | 85,4% | 75,0% |
| italiano | 85 893 | 592 KB | 92,7% | 81,8% | 87,0% | 78,5% |
| espanhol (Espanha) | 138 505 | 866 KB | 93,7% | 81,9% | 91,1% | 80,6% |
| português (Brasil) | 60 834 | 480 KB | 95,1% | 79,3% | 86,1% | 69,3% |

"Tokens" = fração das palavras de um texto corrido que recebem transcrição. Nos livros, o português
cai porque boa parte do acervo do Gutenberg é português europeu em grafia antiga (sem a marca
"Brazil", a transcrição não é exibida). No alemão faltam sobretudo formas flexionadas
(o Wiktionary em inglês raramente transcreve "Menschen", "diesem").
Arquivos: `coverage-wordfreq.json`, `coverage-books.json`.

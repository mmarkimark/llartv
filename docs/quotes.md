# Quote of the day

Home shows a quote of the day under the date (issue #69): a traditional saying or a quote by a classic author, in
the interface language. This page explains where the quotes come from, how they were chosen, and their licence.

## How it works

- The quotes are bundled with the app, one file per interface language:
  `app/src/main/assets/quotes/ca.tsv`, `es.tsv` and `en.tsv`. **No network is used**: nothing is downloaded,
  and the quote shows the same with or without Internet.
- One quote per line, three or four fields separated by a tab:

  ```text
  text<TAB>author<TAB>source[<TAB>translator]
  ```

  `author` is empty for a traditional saying. `source` is the page the quote was checked against (a Wikiquote,
  Viquidites or Wikisource page): the page carrying the text as shown, or — for an `LlarTV` translation — the
  original-language page it was translated from. `translator` is present only for a translated quote: the
  public-domain translator, or `LlarTV` for a translation made for this project. Blank or malformed lines are
  skipped.
- Days are counted continuously: each day shows the next line, and a file of *n* quotes repeats only after *n* days
  (1,000 quotes per language, close to three years without a repeat). It is the same quote all
  day and changes at midnight.
- The quote **follows the interface language** (`language`, *Configuració → Idioma*). If a language's file were
  missing or empty, the Catalan quotes are shown instead.
- Home shows the text in at most two lines, then the author as "— Author" on a line of its own (a saying has no
  author line). The night palette applies to it like to the rest of the home screen.
- It is on by default. Turn it off with *Frase del dia* / *Frase del día* / *Quote of the day* in the settings
  menu, or `quote=off` in `home.conf`. An empty value, or the settings reset, turns it back on.

## Where the quotes come from

- **Wikiquote**: [Viquidites](https://ca.wikiquote.org/) (Catalan), [Wikiquote en español](https://es.wikiquote.org/)
  and [English Wikiquote](https://en.wikiquote.org/), for quotes by authors and for collections of proverbs.
- **Wikisource** ([Viquitexts](https://ca.wikisource.org/), [es](https://es.wikisource.org/),
  [en](https://en.wikisource.org/)), for checking a quote against the original public-domain text.
- **Traditional sayings** (*dites*, *refranes*, proverbs), which belong to the shared oral tradition of each
  language and have no author.

## Selection rules

- Each file has 1,000 lines, roughly half traditional sayings and half quotes by classic authors; chosen to be
  encouraging or to make you think.
- Every author **died before 1956**, so their work is in the public domain in the European Union (life + 70
  years). No modern or copyrighted quotes are included.
- Sayings are traditional: anonymous, of popular origin, not the invention of a living writer.
- Every line names the page it was checked against in its `source` field.
- Translations: a quote by an author who wrote in another language is used only with a public-domain translation
  (translator died before 1956, named in the `translator` field) or with a translation made for LlarTV from the
  public-domain original (`translator` = `LlarTV`). Only the author's name is shown on screen.

### Catalan spelling

Catalan quotes from historical public-domain editions are shown with their spelling normalised to current IEC norms;
wording is unchanged. Normalisation covers accents and diacritics (es → és, dóna → dona), apostrophes and
contractions ('ls → els, lo → el), conjunctions and prepositions (y → i, ab → amb, pera → per a), hyphens (fer-les,
tornar-hi) and old letter forms (th, ch, h, ss → current spelling). Words, word order and meaning are not changed;
lines that could not be normalised without changing words were left out. Each line's source column links to the
original page, where the historical spelling can be read.

## Licence and attribution

The quotes themselves are public-domain texts or traditional sayings. The selection, wording and translations
compiled from Wikiquote are used under the
[Creative Commons Attribution-ShareAlike 4.0 International licence (CC BY-SA 4.0)](https://creativecommons.org/licenses/by-sa/4.0/):

> Quotes compiled from Wikiquote (Viquidites, Wikiquote en español, English Wikiquote), contributors of the
> respective Wikiquote projects, licensed under CC BY-SA 4.0. The page each quote comes from is given in the
> `source` field of `assets/quotes/*.tsv`. Changes: selected, trimmed to one line, Catalan spelling normalised, and arranged one per day.

Under the ShareAlike terms, the three quote files (`app/src/main/assets/quotes/*.tsv`) are distributed under
CC BY-SA 4.0. This does not change the licence of the rest of LlarTV, which stays under the [MIT License](../LICENSE).

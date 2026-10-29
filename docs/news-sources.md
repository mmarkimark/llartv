# News sources

LlarTV shows the latest headlines from an RSS feed in the morning panel. The source is chosen with the
`news_source` setting: `automatic` (the default) follows the interface language, and the interface-language
selector is issue #11, which lands in v1.2, so until then `automatic` resolves to Catalan (3Cat). The other
values are the presets below, and a custom `news_rss` feed overrides every preset.

## The feeds

| Source | Feed | Language |
| --- | --- | --- |
| 3Cat, preset `3cat` (the default) | `https://api.3cat.cat/noticies?_format=rss&origen=frontal&frontal=n324-portada-noticia&version=2.0` | Catalan |
| BBC News Mundo, preset `bbc-mundo` | `https://feeds.bbci.co.uk/mundo/rss.xml` | Spanish |
| BBC News World, preset `bbc-world` | `https://feeds.bbci.co.uk/news/world/rss.xml` | English |

## Choosing the source

`news_source` in `home.conf` takes four values: `automatic` (the default, which resolves as described above),
`3cat`, `bbc-mundo` and `bbc-world`. On the TV it is changed under *Configuració → Font de notícies*.

The precedence between a preset and a custom feed is a decision of the project owner:

- A custom `news_rss` value always wins over the preset, and a preset never overwrites a custom `news_rss`.
- An empty `news_rss=` is not a custom feed, so the preset applies again.
- While a custom feed is in effect the settings menu shows "Personalitzada" and refuses to change the preset,
  with the hint that the custom feed has priority and that removing `news_rss` from `home.conf` restores a
  preset.

## Attribution on screen

The terms of the feeds ask for credit, so every headline carries its source: the label (`3Cat`, `BBC News` for
both BBC presets, or the host of a custom feed) is shown next to the headline, followed by its relative age.
Under the headlines there is one credit line with the source and its site: `3Cat · 3cat.cat`,
`BBC News Mundo · bbc.co.uk/news` or `BBC News World · bbc.co.uk/news`; a custom feed is credited with its
host.

The BBC terms ask for the text "BBC News" and a hyperlink to bbc.co.uk/news in a prominent place near the
headlines, and for no BBC branding. The home screen is display-only, so the article URL is kept with the
headline in the cache instead of being clickable.

## The news cache (v2)

The local cache file `files/news.cache` changed format, a breaking change for the file written by older
versions:

- The first line is a header: `#llartv-news-v2<TAB><source key>`, where the source key is the preset id
  (`3cat`, `bbc-mundo`, `bbc-world`) or `custom:<url>` for a custom feed.
- Every item line is `published<TAB>title<TAB>article link`; the link was not stored before.
- A cache written by another source is never shown under this source, and a cache written by an older version
  (no header) is not reused at all, so after an upgrade the headlines appear again after the next fetch.

## The conditions for the BBC feeds

Checked on 2026-10-27 against
[Can I use BBC content?](https://www.bbc.co.uk/usingthebbc/terms/can-i-use-bbc-content) and
[News feeds from the BBC](https://www.bbc.co.uk/news/10628494).

**For people.** The BBC allows adding the BBC News RSS feed to your own website or social media account, with
three conditions, quoted:

> You can add the BBC News RSS feed to your website or social media account. Provided:
> - You don't change the RSS feed or remove any of our branding or logos
> - You credit us by saying it's from BBC News, bbc.co.uk/news or bbc.com/news putting the text and hyperlink in a
>   prominent place nearby
> - You don't add our branding, logos and so on, except for any branding that's already embedded in the RSS feed.

The same page adds: *"You're not allowed to pluck metadata from our content or RSS feeds"*, and on linking:
*"Is it OK to link to the BBC? Yes, it's ok for you to link to our website URL so long as you don't use the BBC
logo."* The news feeds page adds: *"we do require that the proper format and attribution is used when BBC News
content appears. The attribution text should read 'BBC News' or 'bbc.co.uk/news' as appropriate. You may not use
any BBC logo or other BBC trademark."*

**For business.** From
[Can I reuse BBC content and services for my business?](https://www.bbc.com/usingthebbc/terms/can-i-use-bbc-content-for-my-business/):
*"You'll need to get our permission first for any business use of our RSS feeds, and you might have to pay a
fee."* The same page counts as business use any use *"for commercial purposes"*, *"for educational, non-profit,
charitable or government uses"* and any use that is *"substantially to do your job"*.

### What LlarTV has to do to comply

1. **Credit the source.** The label next to every headline and the credit line under the headlines name the
   source and its site ("BBC News Mundo · bbc.co.uk/news", "BBC News World · bbc.co.uk/news"), which is the
   "BBC News" text near the headlines that the terms ask for.
2. **Not change the feed and not remove branding.** LlarTV reads the feed as it is and adds no logo of its own.
3. **Keep the link to each article.** The home screen is display-only, so there is no functional link back to
   the article there; the article URL has to be kept with the headline.
4. **Stay personal and non-commercial.** LlarTV is a personal project, which is the "for people" path above. If it
   is ever distributed as a product, or used for any non-personal purpose, the BBC has to be asked first through
   [their contact form](https://www.bbc.co.uk/contact/enquiryform/).
5. **Not claim neutrality.** BBC publishes
   [impartiality guidelines](https://www.bbc.com/editorialguidelines/guidelines/impartiality), but no outlet is
   objectively bias-free: World has a British perspective and Mundo emphasises Latin America, and both share one
   editorial organisation.

### Conditions for the implementation

- The presets ship with the attribution and the article link in place, so the app never shows BBC headlines
  without the credit the terms ask for.
- The cached item keeps the source and the article link, so the cache format changes and the entries written by an
  older version are not reused.
- A custom `news_rss` value is always preserved and is never replaced by a preset or by a language change.

## The conditions for the 3Cat feed

Checked on 2026-10-28 against the
[Condicions d'utilització del portal](https://www.3cat.cat/avis-legal/condicions-utilitzacio-del-portal/)
("Condicions d'ús" of the legal notice).

**Personal and private use.** The use the conditions allow is, quoted:

> "Que es faci només per obtenir informació per a l'ús personal i privat. Que no la utilitzis amb finalitats
> comercials o per a la seva distribució, comunicació pública, transformació o descompilació."

Personal and private use only: commercial use, distribution, public communication, transformation and
decompilation are not allowed.

**No modification.** Quoted:

> "Que no en modifiquis cap dels continguts."

The content must not be modified.

**Insertion elsewhere.** Quoted:

> "Podràs inserir determinits continguts als teus blogs o a d'altres espais web, sempre que en cap moment no es
> causi confusió a altres usuaris i, en tot cas, no es manipuli de cap manera cap element identificatiu de
> l'autoria i de la titularitat dels continguts."

Content may be inserted into blogs or other web spaces, as long as no confusion is caused to other users and
no identifying element of the authorship or the ownership of the content is manipulated.

**Intellectual property.** The intellectual-property clause of the same notice reserves all rights over its
content to the CCMA and opposes its use for text and data mining without prior written authorisation.

### What LlarTV has to do to comply

1. **Stay personal and private.** A personal launcher on your own TV is the personal and private use the
   conditions allow; a public distribution of the app is not covered by it, so a wider distribution has to ask
   the CCMA first. This is the same shape as the BBC caveat above (personal use allowed, business or product
   distribution needs permission), and the decision on whether to distribute publicly is the project owner's.
2. **Not modify the content.** LlarTV reads the feed as it is.
3. **Not claim a licence.** The conditions do not grant any licence over the content; they only state which
   use they allow.
4. **Not claim neutrality.** Impartiality is a selection criterion for the presets, not a guarantee of any
   source.

## Still open

- Whether the personal, non-commercial reading of the terms covers a public release of the app is the owner's
  call: the reading above is that a personal launcher on your own TV is the "for people" path the BBC allows and
  the personal and private use the 3Cat conditions allow, and a wider distribution needs the BBC's permission
  and the CCMA's authorisation first.

# News sources

LlarTV shows the latest headlines from an RSS feed in the morning panel. The default is 3Cat's front page
(Catalan). The roadmap also plans BBC News Mundo (Spanish) and BBC News World (English) as presets that the
news source setting can choose.

## The feeds

| Source | Feed | Language |
| --- | --- | --- |
| 3Cat (default) | `https://api.3cat.cat/noticies?_format=rss&origen=frontal&frontal=n324-portada-noticia&version=2.0` | Catalan |
| BBC News Mundo | `https://feeds.bbci.co.uk/mundo/rss.xml` | Spanish |
| BBC News World | `https://feeds.bbci.co.uk/news/world/rss.xml` | English |

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

1. **Credit the source.** The news card names the source ("BBC News Mundo", "BBC News World") with a link to the
   BBC page of that feed, next to the headlines.
2. **Not change the feed and not remove branding.** LlarTV reads the feed as it is and adds no logo of its own.
3. **Keep the link to each article.** The home screen is display-only, so there is no functional link back to the
   article there; the article URL has to be kept with the headline.
4. **Stay personal and non-commercial.** LlarTV is a personal project, which is the "for people" path above. If it
   is ever distributed as a product, or used for any non-personal purpose, the BBC has to be asked first through
   [their contact form](https://www.bbc.co.uk/contact/enquiryform/).
5. **Not claim neutrality.** BBC publishes
   [impartiality guidelines](https://www.bbc.com/editorialguidelines/guidelines/impartiality), but no outlet is
   objectively bias-free: World has a British perspective and Mundo emphasises Latin America, and both share one
   editorial organisation.

### Conditions for the implementation

- The presets stay disabled until the attribution and the article link are in place, so the app never shows BBC
  headlines without the credit the terms ask for.
- The cached item keeps the source and the article link, so the cache format changes and the entries written by an
  older version are not reused.
- A custom `news_rss` value is always preserved and is never replaced by a preset or by a language change.

## Still open

- The terms of the 3Cat feed are not recorded yet; it is the default source, so they should be checked before the
  v1.1 release as well.
- Whether the personal, non-commercial reading of the terms covers a public release of the app is the owner's call:
  the reading above is that a personal launcher on your own TV is the "for people" path, and a wider distribution
  needs a licence.

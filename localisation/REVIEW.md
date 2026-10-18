# Translation review

Resource coverage includes all 12 requested locales. Android string coverage and placeholder/plural validation are technical gates, separate from publication approval. No locale is approved for release yet.

The initial navigation copy was authored locally. Expanded drafts combine cached Google public-endpoint translation output and explicitly authored multilingual interface copy in `authored/interface.tsv` and `authored/hi-additional.txt`. The provider throttled generation; network requests were stopped, and the remaining resources were completed locally. Only public application copy was processed. User recipes, backups, microphone audio and databases were never inputs. `finish_locale_resources.py` reproduces resource assembly without network access. `compile_authored_locales.py` preserves the source-to-translation mapping.

Run `python tools/localize.py --strict` to check resource completeness. `resource_complete` does not imply `release_approved`. The legacy `complete` field remains false until the broader review is finished. The report explicitly counts remaining inline interpolations; unit labels, backend/domain errors, complete voice dictionaries, store metadata, screenshots and website translations still need additional work.

For each market, a native reviewer must verify terminology, grammar, context, button length, privacy and subscription disclosures, plurals, accessibility announcements, OCR guidance and actual rendered layouts. Portuguese resources need separate Brazilian and European review. Arabic needs RTL, numeral/bidirectional text and large-font checks. The recipe-count plural resources include the required Arabic and Polish categories; grammatical review is still required. Do not translate user-entered recipe or collection names automatically.

Keep DishNest and Amazon product names unchanged. Metric and imperial abbreviations describe actual quantities and must not silently change their units. The pasted-text parser currently expects the literal English headings `Ingredients` and `Method` or `Instructions`; translations of its guidance deliberately preserve those input tokens.

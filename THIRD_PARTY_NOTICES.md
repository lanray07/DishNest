# Third-party components

The original DishNest source, logo, illustrations and demo recipes were created for this project. The four demo food photographs were generated with the built-in imagegen tool on 18 October 2026. They represent illustrative dishes; no third-party recipe photography was copied.

| Component | Licence / source |
| --- | --- |
| Kotlin, AndroidX, Compose Material 3, Material icons, Room, DataStore | Apache 2.0; https://developer.android.com/jetpack/androidx |
| Kotlin Coroutines | Apache 2.0; https://github.com/Kotlin/kotlinx.coroutines |
| Gson | Apache 2.0; https://github.com/google/gson |
| Jsoup | MIT; https://jsoup.org/license |
| Coil | Apache 2.0; https://github.com/coil-kt/coil |
| Tesseract4Android 4.9.0 | Apache 2.0; https://github.com/adaptech-cz/Tesseract4Android |
| English tessdata_fast model | Apache 2.0; https://github.com/tesseract-ocr/tessdata_fast |
| Tesseract 5.5.1, Leptonica 1.85.0, libjpeg 9f, libpng 1.6.48 bundled through OCR library | Original notices collected from the pinned Tesseract4Android 4.9.0 source tag; see `legal/third-party/manifest.json` |
| Amazon Appstore SDK 3.0.9 | Amazon developer SDK terms, not an open-source project; https://developer.amazon.com/apps-and-games/sdks |
| JUnit, Robolectric, Roborazzi | Development/test dependencies; respective upstream licences |

The offline **Third-party notices** viewer in Settings reproduces ten original upstream licence/notice files, including native OCR notices, the IJG acknowledgement, the DataStore Protocol Buffers BSD terms and OkHttp Public Suffix List/MPL notices. `tools/collect_native_notices.py` records source URLs and SHA-256 checksums; `tools/package_notices.py` verifies them before assembly. `tools/dependency-inventory.init.gradle` and `tools/dependency_inventory.py` inventory the exact resolved release artifacts and their POM licence declarations. Missing metadata is explicitly flagged for review; no automated inventory is presented as legal approval.

Marketing PNG templates render with locally installed Segoe UI and Georgia. Those font files are not redistributed. Editable SVGs reference system fonts and may render differently elsewhere. Android app text uses system sans and serif fonts. Before public distribution, the publisher must review distribution obligations, native notices, Amazon's proprietary terms and the final resolved dependency inventory.

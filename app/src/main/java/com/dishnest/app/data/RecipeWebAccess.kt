package com.dishnest.app.data

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

internal data class RecipeWebResponse(
    val status: Int,
    val type: String = "",
    val location: String? = null,
    val body: String = "",
)

internal data class RecipeWebPage(val url: URL, val html: String)

/** Bounded, redirect-by-redirect fetching; no cookies, login, or access-control bypass. */
internal object RecipeWebAccess {
    private const val ROBOTS_LIMIT = 512 * 1024
    private const val PAGE_LIMIT = 2_000_000
    private val redirects = setOf(301, 302, 303, 307, 308)

    fun page(start: URL, get: (URL, Int) -> RecipeWebResponse = ::download): RecipeWebPage {
        var current = start
        val policies = mutableMapOf<String, RobotsPolicy>()
        repeat(6) {
            validateUrl(current)
            val origin = "https://${current.authority}"
            val policy = policies.getOrPut(origin) { robots(URL("$origin/robots.txt"), get) }
            require(policy.allows(current.toURI())) {
                "The site does not allow this recipe import. Paste text you have permission to save."
            }
            val response = get(current, PAGE_LIMIT)
            if (response.status in redirects) {
                current = URL(current, response.location ?: error("Missing redirect target"))
            } else {
                require(response.status == 200) {
                    "The site declined access (${response.status}). Paste a recipe you have permission to save."
                }
                require(response.type.substringBefore(';').trim().equals("text/html", true)) {
                    "This URL does not contain a web recipe."
                }
                return RecipeWebPage(current, response.body)
            }
        }
        error("Too many redirects")
    }

    private fun robots(start: URL, get: (URL, Int) -> RecipeWebResponse): RobotsPolicy {
        var current = start
        repeat(6) {
            validateUrl(current)
            val response = get(current, ROBOTS_LIMIT)
            when {
                response.status in redirects ->
                    current = URL(current, response.location ?: error("Missing redirect target"))
                response.status == 404 || response.status == 410 -> return RobotsPolicy("")
                response.status in 200..299 -> {
                    // A login/challenge page is not a usable robots policy.
                    require(
                        !response.type.contains("html", true) &&
                            !response.body.trimStart().startsWith('<')
                    ) {
                        "The site's import policy could not be checked. Try again later or paste permitted text."
                    }
                    return RobotsPolicy(response.body)
                }
                else ->
                    error(
                        "The site's import policy could not be checked. Try again later or paste permitted text."
                    )
            }
        }
        error("Too many import-policy redirects")
    }

    private fun validateUrl(url: URL) {
        require(
            url.protocol == "https" &&
                url.userInfo == null &&
                url.port in listOf(-1, 443) &&
                url.host.isNotBlank()
        ) {
            "Use a public HTTPS recipe URL."
        }
    }

    private fun download(url: URL, limit: Int): RecipeWebResponse {
        validateUrl(url)
        val addresses = InetAddress.getAllByName(url.host)
        require(
            addresses.isNotEmpty() &&
                addresses.all { address ->
                    val bytes = address.address
                    val shared =
                        bytes.size == 4 &&
                            (bytes[0].toInt() and 255) == 100 &&
                            (bytes[1].toInt() and 255) in 64..127
                    val uniqueLocal = bytes.size == 16 && (bytes[0].toInt() and 254) == 252
                    !address.isAnyLocalAddress &&
                        !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress &&
                        !address.isSiteLocalAddress &&
                        !address.isMulticastAddress &&
                        !shared &&
                        !uniqueLocal
                }
        ) {
            "Private network URLs are not supported."
        }
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty(
            "User-Agent",
            "DishNest/0.1 RecipeMetadata (user-requested import; https://github.com/lanray07/DishNest)",
        )
        try {
            val status = connection.responseCode
            val body =
                if (status in 200..299)
                    connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val size = input.read(buffer)
                            if (size < 0) break
                            total += size
                            require(total <= limit) {
                                "The site's response is too large to import safely."
                            }
                            output.write(buffer, 0, size)
                        }
                        output.toString(Charsets.UTF_8.name())
                    }
                else ""
            return RecipeWebResponse(
                status,
                connection.contentType.orEmpty(),
                connection.getHeaderField("Location"),
                body,
            )
        } finally {
            connection.disconnect()
        }
    }
}

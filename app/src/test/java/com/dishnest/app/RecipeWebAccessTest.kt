package com.dishnest.app

import com.dishnest.app.data.*
import java.net.URI
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class RecipeWebAccessTest {
    @Test
    fun mergesMatchingGroupsAndPrefersSpecificAllow() {
        val policy =
            RobotsPolicy(
                """
            User-agent: *
            Disallow: /
            User-agent: DishNest
            Disallow: /private
            Allow: /private/shared
            User-agent: dishnest
            Disallow: /draft
        """
                    .trimIndent()
            )
        assertTrue(policy.allows(URI("https://recipe.example/soup")))
        assertFalse(policy.allows(URI("https://recipe.example/draft/soup")))
        assertFalse(policy.allows(URI("https://recipe.example/private/soup")))
        assertTrue(policy.allows(URI("https://recipe.example/private/shared/soup")))
    }

    @Test
    fun wildcardEndAnchorCommentsAndEmptyRules() {
        val policy =
            RobotsPolicy(
                """
            User-agent: *
            Disallow:
            Disallow: /*?print=*$ # no print view
            Disallow: /same
            Allow: /same
        """
                    .trimIndent()
            )
        assertFalse(policy.allows(URI("https://recipe.example/soup?print=yes")))
        assertTrue(policy.allows(URI("https://recipe.example/soup?other=yes")))
        assertTrue(policy.allows(URI("https://recipe.example/same")))
    }

    @Test
    fun unicodeAndEncodedOctetsPreserveReservedSeparators() {
        val policy =
            RobotsPolicy(
                "User-agent: *\nDisallow: /caf\u00e9\nDisallow: /foo/bar\nDisallow: /~draft\n"
            )
        assertFalse(policy.allows(URI("https://recipe.example/caf%C3%A9")))
        assertFalse(policy.allows(URI("https://recipe.example/%7edraft")))
        assertTrue(policy.allows(URI("https://recipe.example/foo%2fbar")))
        assertTrue(policy.allows(URI("https://recipe.example/Caf%C3%A9")))
    }

    @Test
    fun denialPreventsPageRequestAndRedirectChecksDestinationPolicy() {
        val requested = mutableListOf<String>()
        val result = runCatching {
            RecipeWebAccess.page(URL("https://first.example/soup")) { url, _ ->
                requested.add(url.toString())
                when (url.toString()) {
                    "https://first.example/robots.txt" -> RecipeWebResponse(404)
                    "https://first.example/soup" ->
                        RecipeWebResponse(302, location = "https://second.example/private")
                    "https://second.example/robots.txt" ->
                        RecipeWebResponse(
                            200,
                            "text/plain",
                            body = "User-agent: *\nDisallow: /private",
                        )
                    else -> error("Forbidden page was requested")
                }
            }
        }
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("does not allow"))
        assertEquals(
            listOf(
                "https://first.example/robots.txt",
                "https://first.example/soup",
                "https://second.example/robots.txt",
            ),
            requested,
        )
    }

    @Test
    fun missingPolicyAllowsButUnavailableOrChallengePolicyStopsImport() {
        for (status in listOf(401, 403, 429, 500, 503)) {
            var calls = 0
            val result = runCatching {
                RecipeWebAccess.page(URL("https://recipe.example/soup")) { _, _ ->
                    calls++
                    RecipeWebResponse(status)
                }
            }
            assertTrue("status=$status", result.isFailure)
            assertEquals(1, calls)
        }
        assertTrue(
            runCatching {
                    RecipeWebAccess.page(URL("https://recipe.example/soup")) { _, _ ->
                        RecipeWebResponse(200, "text/html", body = "<html>Sign in</html>")
                    }
                }
                .isFailure
        )
        val page =
            RecipeWebAccess.page(URL("https://recipe.example/soup")) { url, limit ->
                if (url.path == "/robots.txt") {
                    assertEquals(512 * 1024, limit)
                    RecipeWebResponse(410)
                } else {
                    assertEquals(2_000_000, limit)
                    RecipeWebResponse(200, "text/html; charset=utf-8", body = "recipe")
                }
            }
        assertEquals("recipe", page.html)
    }

    @Test
    fun policyRedirectRulesRemainBoundToOriginalOriginAndAreNotCachedAcrossImports() {
        val requests = mutableListOf<String>()
        val get: (URL, Int) -> RecipeWebResponse = { url, _ ->
            requests.add(url.toString())
            when (url.toString()) {
                "https://recipe.example/robots.txt" ->
                    RecipeWebResponse(302, location = "https://policy.example/rules.txt")
                "https://policy.example/rules.txt" ->
                    RecipeWebResponse(
                        200,
                        "text/plain",
                        body = "User-agent: DishNest\nDisallow: /private",
                    )
                else -> error("Disallowed content fetched")
            }
        }
        repeat(2) {
            assertTrue(
                runCatching { RecipeWebAccess.page(URL("https://recipe.example/private"), get) }
                    .isFailure
            )
        }
        assertEquals(4, requests.size)
    }

    @Test
    fun redirectsAreBoundedAndCannotDowngradeOrSendCredentials() {
        var requests = 0
        assertTrue(
            runCatching {
                    RecipeWebAccess.page(URL("https://recipe.example/soup")) { _, _ ->
                        requests++
                        RecipeWebResponse(302, location = "/loop")
                    }
                }
                .isFailure
        )
        assertEquals(6, requests)
        for (target in
            listOf(
                "http://recipe.example/soup",
                "https://user:pass@recipe.example/soup",
                "https://recipe.example:8443/soup",
            )) {
            requests = 0
            assertTrue(
                runCatching {
                        RecipeWebAccess.page(URL("https://recipe.example/soup")) { url, _ ->
                            requests++
                            if (url.path == "/robots.txt") RecipeWebResponse(404)
                            else RecipeWebResponse(302, location = target)
                        }
                    }
                    .isFailure
            )
            assertEquals(2, requests)
        }
    }
}

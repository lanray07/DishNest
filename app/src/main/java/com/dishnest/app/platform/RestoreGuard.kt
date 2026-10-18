package com.dishnest.app.platform

/** Purchase eligibility is withheld until every restore page and receipt is checked. */
class RestoreGuard {
    var generation: Long = 0
        private set

    var historyComplete = false
        private set

    var historyFailed = false
        private set

    private val expected = mutableSetOf<String>()
    private val verified = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()
    val verificationFailed
        get() = failed.isNotEmpty()

    val ready
        get() =
            historyComplete && !historyFailed && failed.isEmpty() && verified.containsAll(expected)

    fun begin(receipts: Collection<String>): Long {
        generation++
        historyComplete = false
        historyFailed = false
        expected.clear()
        expected.addAll(receipts)
        verified.clear()
        failed.clear()
        return generation
    }

    fun add(receipts: Collection<String>) {
        expected.addAll(receipts)
    }

    fun page(receipts: Collection<String>, hasMore: Boolean) {
        add(receipts)
        historyComplete = !hasMore
        historyFailed = false
    }

    fun historyFailure() {
        historyFailed = true
        historyComplete = false
    }

    fun verification(receipt: String, success: Boolean, session: Long) {
        if (session != generation || receipt !in expected) return
        if (success) {
            verified.add(receipt)
            failed.remove(receipt)
        } else {
            verified.remove(receipt)
            failed.add(receipt)
        }
    }
}

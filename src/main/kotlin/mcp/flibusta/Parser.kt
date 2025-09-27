package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.utils.HttpHelperInterface
import org.springframework.stereotype.Component

@Component
class Parser(httpHelper: HttpHelperInterface) {

    fun parseSearchResults(html: String): List<BookInfo> {
        val regex = Regex("""<a href="/b/(\d+)" title="([^"]+)">""")
        return regex.findAll(html).map { matchResult ->
            val (id, title) = matchResult.destructured
            BookInfo(id.toInt(), title)
        }.toList()
    }

}
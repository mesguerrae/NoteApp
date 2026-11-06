package com.mee.noteapp.mhtml

import android.content.Context

/**
 * Looks at app/src/main/assets/mhtml — the folder you drop your .mhtml files into
 * BEFORE compiling — and lists them sorted by file name, which defines the page order
 * used everywhere in the app (viewer, exporter, etc).
 */
object MhtmlAssetRepository {
    const val ASSET_FOLDER = "mhtml"

    fun listOrderedFiles(context: Context): List<String> {
        val files = context.assets.list(ASSET_FOLDER) ?: emptyArray()
        return files
            .filter { it.endsWith(".mhtml", ignoreCase = true) || it.endsWith(".mht", ignoreCase = true) }
            .sortedWith(naturalOrderComparator())
    }

    fun assetPath(fileName: String) = "file:///android_asset/$ASSET_FOLDER/$fileName"

    /** Sorts "page2.mhtml" before "page10.mhtml" instead of plain lexical order. */
    private fun naturalOrderComparator() = Comparator<String> { a, b ->
        val ax = splitNumeric(a)
        val bx = splitNumeric(b)
        var i = 0
        while (i < ax.size && i < bx.size) {
            val x = ax[i]; val y = bx[i]
            val bothNumeric = x.first != null && y.first != null
            val cmp = if (bothNumeric) x.first!!.compareTo(y.first!!) else x.second.compareTo(y.second)
            if (cmp != 0) return@Comparator cmp
            i++
        }
        ax.size - bx.size
    }

    private fun splitNumeric(s: String): List<Pair<Long?, String>> {
        val regex = Regex("\\d+|\\D+")
        return regex.findAll(s).map {
            val v = it.value
            if (v.isNotEmpty() && v[0].isDigit()) v.toLongOrNull() to v else null to v
        }.toList()
    }
}

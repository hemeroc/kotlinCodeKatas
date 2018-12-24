package io.github.hemeroc.katas.kotlin.wordcount.application

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import java.io.File
import java.lang.Thread.currentThread
import java.nio.file.Files.readAllLines
import java.nio.file.Paths

fun main(args: Array<String>) = mainBody {
    val arguments = ArgParser(args).parseInto(::CommandlineArgs)
    val interactiveMode = arguments.inputText == null
    do {
        val text: String = arguments.inputText ?: {
            print("Enter text: ")
            flush()
            readLine()!!
        }.invoke()
        if (interactiveMode && text.isEmpty()) break
        with(WordCount(text, arguments.stopWords, arguments.dictionary)) {
            println("Number of words: $wordsCount, " +
                "unique: $uniqueWordsCount, " +
                "average word length: ${"%.2f".format(averageWordLength)} characters")
            if (arguments.index) {
                val dictionaryInformation = if (arguments.dictionary != null) {
                    " (unknown: $uniqueWordsUnknownCount)"
                } else ""
                println("Index$dictionaryInformation:")
                uniqueWordsUnknownMarked.forEach { println("\t$it") }
            }
        }
        if (interactiveMode) println()
    } while (interactiveMode)
}

class CommandlineArgs(parser: ArgParser) {
    val index by parser.flagging(
        names = *arrayOf("-i", "--index"),
        help = "show index of unique words"
    ).default(true)
    val dictionary by parser.storing(
        names = *arrayOf("-d", "--dictionary"),
        help = "file with set of words in the dictionary",
        transform = { File(this).readLines().toSet() }
    ).default<Set<String>?>(null)
    val stopWords by parser.storing(
        names = *arrayOf("-s", "--stopWords"),
        help = "file with set of stopWords which are ignored by the word counter",
        transform = { File(this).readLines().toSet() }
    ).default<Set<String>>(DEFAULT_STOP_WORDS)
    val inputText by parser.positional(
        name = "SOURCE",
        help = "source file to operate on",
        transform = { File(this).readText() }
    ).default<String?>(null)
}

class WordCount(
    val text: String,
    val stopWords: Set<String> = emptySet(),
    val dictionary: Set<String>? = null,
    wordRegex: String = "[a-zA-Z-]+"
) {

    val words: List<String> by lazy { text.words(stopWords) }
    val wordsCount by lazy { words.size }

    val uniqueWords by lazy { words.distinct().toSortedSet() }
    val uniqueWordsCount by lazy { uniqueWords.size }
    private val uniqueWordsUnknown by lazy {
        var numberOfUniqueWordsFound = 0
        UniqueWordsUnknown(uniqueWords.map {
            it + if (dictionary?.contains(it) == false) {
                numberOfUniqueWordsFound++
                "*"
            } else ""
        }.toSortedSet(), numberOfUniqueWordsFound)
    }
    val uniqueWordsUnknownMarked by lazy { uniqueWordsUnknown.uniqueWordsUnknownMarked }
    val uniqueWordsUnknownCount by lazy { uniqueWordsUnknown.uniqueWordsUnknownCount }

    val averageWordLength by lazy { words.map { it.length }.average() }

    private data class UniqueWordsUnknown(val uniqueWordsUnknownMarked: Set<String>, val uniqueWordsUnknownCount: Int)

    private val wordRegex: Regex = Regex(wordRegex)

    private fun String?.words(stopWords: Set<String>) =
        this.words.filterNot { stopWords.contains(it) }

    private val String?.words: List<String>
        get() = wordRegex.findAll(this ?: "").map { it.value }.toList()
}

private val DEFAULT_STOP_WORDS =
    readAllLines(Paths.get(currentThread().contextClassLoader.getResource("stopwords.txt").toURI())).toSet()

private fun flush() = System.out.flush()

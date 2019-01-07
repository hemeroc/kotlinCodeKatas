package io.github.hemeroc.katas.kotlin.csvviewer.application

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import io.github.hemeroc.katas.kotlin.csvviewer.application.Align.CENTER
import io.github.hemeroc.katas.kotlin.csvviewer.application.Align.LEFT
import io.github.hemeroc.katas.kotlin.csvviewer.application.Align.RIGHT
import io.github.hemeroc.katas.kotlin.csvviewer.application.MenuOperation.EXIT
import io.github.hemeroc.katas.kotlin.csvviewer.application.MenuOperation.FIRST_PAGE
import io.github.hemeroc.katas.kotlin.csvviewer.application.MenuOperation.JUMP_TO_PAGE
import io.github.hemeroc.katas.kotlin.csvviewer.application.MenuOperation.LAST_PAGE
import io.github.hemeroc.katas.kotlin.csvviewer.application.MenuOperation.NEXT_PAGE
import io.github.hemeroc.katas.kotlin.csvviewer.application.MenuOperation.PREVIOUS_PAGE
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.util.Scanner
import kotlin.math.ceil
import kotlin.math.max

fun main(args: Array<String>) = mainBody {
    val arguments = ArgParser(args).parseInto(::CommandlineArgs)
    val csvFile = CsvFile(arguments.csvFile, delimiter = ';', addRecordNumber = arguments.addRecordNumber)
    AsciiCsvViewer(csvFile, arguments.linesPerPage).render()
}

private class CommandlineArgs(parser: ArgParser) {
    val addRecordNumber by parser.flagging(
        names = *arrayOf("-n", "--addRecordNumber"),
        help = "add record number to each line"
    ).default(false)
    val csvFile by parser.positional(
        name = "SOURCE",
        help = "source file to operate on",
        transform = { File(this) }
    )
    val linesPerPage by parser.positional(
        help = "lines to be displayed per page",
        transform = { toInt() }
    ).default(10)
}

enum class Align {
    LEFT, RIGHT, CENTER
}

enum class MenuOperation(val text: String, operation: String) {

    NEXT_PAGE("(${BOLD_S}n$BOLD_E)ext page", "^n$"),
    PREVIOUS_PAGE("(${BOLD_S}p$BOLD_E)revious page", "^p$"),
    FIRST_PAGE("(${BOLD_S}f$BOLD_E)irst page", "^f$"),
    LAST_PAGE("(${BOLD_S}l$BOLD_E)ast page", "^l$"),
    JUMP_TO_PAGE("(${BOLD_S}j\$$BOLD_E)ump to page", "^j(\\d+)$"),
    EXIT("e(${BOLD_S}x$BOLD_E)it", "^x$"),
    ;

    val operation = Regex(operation)
}

class AsciiCsvViewer(
    private val csvFile: CsvFile,
    private val linesPerPage: Int = 10,
    private val printStream: PrintStream = System.out,
    inputStream: InputStream = System.`in`,
    borderWidth: Int = 1
) {

    private val inputScanner = Scanner(inputStream)
    private val columnRange = 0 until csvFile.columnCount
    private val border = " ".repeat(borderWidth)
    private val doubleBorderWidth = borderWidth * 2

    private fun cellToString(cellString: String, columnWidth: Int, align: Align = LEFT): String {
        val cellStringWithBorder = border + cellString + border
        return when (align) {
            RIGHT -> cellStringWithBorder.padStart(columnWidth + doubleBorderWidth)
            LEFT -> cellStringWithBorder.padEnd(columnWidth + doubleBorderWidth)
            CENTER -> {
                val padding = columnWidth + doubleBorderWidth - cellStringWithBorder.length
                val leftPadding = " ".repeat(padding / 2)
                val rightPadding = " ".repeat(ceil(padding.toDouble() / 2).toInt())
                leftPadding + cellStringWithBorder + rightPadding
            }
        }
    }

    private fun separatorLine(csvPage: CsvPage) = columnRange.joinToString(separator = "+") {
        "-".repeat(csvPage.columnWidth(it) + doubleBorderWidth)
    }

    private fun titleLine(csvPage: CsvPage) =
        "Page $BOLD_S${csvPage.pageNumber}$BOLD_E of ${csvFile.pageCount(linesPerPage)} in ${csvFile.name}"

    private fun headerLine(csvPage: CsvPage) = columnRange.joinToString(separator = "|") {
        BOLD_S + cellToString(csvPage.header[it], csvPage.columnWidth(it), CENTER) + BOLD_E
    }

    private fun page(csvPage: CsvPage) =
        (0 until csvPage.lineCount)
            .joinToString(separator = "\n") { line ->
                columnRange.joinToString(separator = "|") { column ->
                    cellToString(csvPage[line][column], csvPage.columnWidth(column))
                }
            }

    private fun availableOperations(pageNumber: Int): Set<MenuOperation> =
        mutableSetOf<MenuOperation>().apply {
            if (pageNumber < csvFile.pageCount(linesPerPage)) {
                add(NEXT_PAGE)
                add(MenuOperation.LAST_PAGE)
            }
            if (pageNumber > 1) {
                add(PREVIOUS_PAGE)
                add(MenuOperation.FIRST_PAGE)
            }
            add(JUMP_TO_PAGE)
            add(EXIT)
        }.toSortedSet()

    fun render(initialPage: Int = 1) {
        var currentPage = initialPage
        do {
            val page = csvFile.page(currentPage, linesPerPage)
            printStream.println("""
            ${titleLine(page)}

            ${headerLine(page)}
            ${separatorLine(page)}
        """.trimIndent())
            printStream.println(page(page))
            printStream.println()
            val availableOperations = availableOperations(currentPage)
            printStream.println(availableOperations.joinToString(separator = ", ") { it.text })
            var menuOperation: MenuOperation?
            do {
                val input = inputScanner.nextLine()
                menuOperation = availableOperations.find { it.operation.matches(input) }
                currentPage = when (menuOperation) {
                    FIRST_PAGE -> 1
                    LAST_PAGE -> csvFile.pageCount(linesPerPage)
                    NEXT_PAGE -> currentPage + 1
                    PREVIOUS_PAGE -> currentPage - 1
                    JUMP_TO_PAGE -> JUMP_TO_PAGE.operation.find(input)!!.groupValues[1].toInt()
                        .let { if (it > csvFile.pageCount(linesPerPage)) currentPage else it }
                    EXIT -> 0
                    else -> currentPage
                }
            } while (menuOperation == null)
            printStream.println()
        } while (currentPage != 0)
    }

}

typealias CsvLine = List<String>

class CsvPage(
    val pageNumber: Int,
    val header: List<String>,
    private val lines: List<CsvLine>
) {
    private val headerColumnWidths = header.map { it.length }
    private val lineColumnWidths = (0 until header.count()).map {
        lines.map { line -> line[it] }.maxBy { cell -> cell.length }?.length ?: 0
    }

    val lineCount = lines.count()

    operator fun get(line: Int) = lines[line]

    fun columnWidth(column: Int, includeHeader: Boolean = true): Int {
        return max(if (includeHeader) headerColumnWidths[column] else 0, lineColumnWidths[column])
    }

}

class CsvFile(
    private val sourceFile: File,
    val delimiter: Char = ',',
    val addRecordNumber: Boolean = false,
    val defaultLinesPerPage: Int = 10
) {
    val name = sourceFile.name
    val lineCount: Int = sourceFile.lines(1).count()
    val header: CsvLine = (if (addRecordNumber) listOf("No.") else emptyList()) +
        sourceFile.lines(0, 1).first().split(delimiter)

    val columnCount = header.size

    fun pageCount(linesPerPage: Int = defaultLinesPerPage) = ceil(lineCount.toDouble() / linesPerPage).toInt()

    fun page(pageNumber: Int, linesPerPage: Int = defaultLinesPerPage): CsvPage {
        val offset = (pageNumber - 1) * linesPerPage + 1
        val lines = sourceFile
            .lines(offset, linesPerPage)
            .mapIndexed { index, line ->
                (if (addRecordNumber) listOf("${offset + index}.") else emptyList()) + line.split(delimiter)
            }
            .toList()
        return CsvPage(pageNumber, header, lines)
    }

}

private fun File.lines(offset: Int, lines: Int = Int.MAX_VALUE) =
    bufferedReader().lineSequence().drop(offset).take(lines)

const val BOLD_S = "\u001B[1m"
const val BOLD_E = "\u001B[0m"

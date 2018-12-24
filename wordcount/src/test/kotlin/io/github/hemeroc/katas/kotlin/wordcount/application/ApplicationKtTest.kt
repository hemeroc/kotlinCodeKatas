package io.github.hemeroc.katas.kotlin.wordcount.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ApplicationKtTest {

    @ParameterizedTest
    @MethodSource("wordsExtractCorrectNumberOfWordsArgument")
    fun wordsExtractCorrectNumberOfWords(
        line: String,
        numberOfWords: Long
    ) {
        assertThat(WordCount(line).wordsCount).isEqualTo(numberOfWords)
    }

    @ParameterizedTest
    @MethodSource("wordsExtractCorrectNumberOfWordsWithStopWordsArgument")
    fun wordsExtractCorrectNumberOfWordsWithStopWordsArgument(
        line: String,
        stopWords: Set<String>,
        numberOfWords: Long
    ) {
        assertThat(WordCount(line, stopWords).wordsCount).isEqualTo(numberOfWords)
    }

    companion object {

        @JvmStatic
        fun wordsExtractCorrectNumberOfWordsArgument() = listOf(
            Arguments.of("", 0),
            Arguments.of(" ", 0),
            Arguments.of("HelloWorld", 1),
            Arguments.of("Hello World", 2),
            Arguments.of("Hello-World", 1),
            Arguments.of("Mary had a little lamb", 5)
        )

        @JvmStatic
        fun wordsExtractCorrectNumberOfWordsWithStopWordsArgument() = listOf(
            Arguments.of("", emptySet<String>(), 0),
            Arguments.of("", setOf("had", "a"), 0),
            Arguments.of("Mary had a little lamb", setOf("had", "a"), 3),
            Arguments.of("Mary had a little lamb", emptySet<String>(), 5)
        )
    }
}

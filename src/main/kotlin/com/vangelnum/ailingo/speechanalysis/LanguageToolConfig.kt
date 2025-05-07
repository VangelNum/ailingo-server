package com.vangelnum.ailingo.speechanalysis

import org.languagetool.JLanguageTool
import org.languagetool.Languages
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LanguageToolConfig {

    @Bean
    fun languageTool(): JLanguageTool {
        val americanEnglish = Languages.getLanguageForShortCode("en-US")
            ?: throw IllegalStateException("AmericanEnglish (en-US) could not be loaded by LanguageTool.")

        val tool = JLanguageTool(americanEnglish)

        tool.disableRule("UPPERCASE_SENTENCE_START")
        tool.disableRule("I_LOWERCASE")

        return tool
    }
}
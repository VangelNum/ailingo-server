package com.vangelnum.ailingo.speechanalysis

import org.languagetool.JLanguageTool
import org.languagetool.Languages // Import the Languages class
// import org.languagetool.language.AmericanEnglish // No longer strictly needed for this bean if not casting
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LanguageToolConfig {

    // Remove this bean. LanguageTool will manage its own language instances.
    // @Bean
    // fun americanEnglish(): AmericanEnglish {
    //     return AmericanEnglish()
    // }

    @Bean
    fun languageTool(): JLanguageTool { // Removed 'americanEnglish: AmericanEnglish' parameter
        // Get the AmericanEnglish language instance from LanguageTool's managed Languages class.
        // This will correctly initialize the Languages class and its managed language instances if not already done.
        val americanEnglish = Languages.getLanguageForShortCode("en-US")
            ?: throw IllegalStateException("AmericanEnglish (en-US) could not be loaded by LanguageTool.")

        // JLanguageTool constructor accepts org.languagetool.Language,
        // so the 'americanEnglish' variable (which is of type Language) is suitable.
        val tool = JLanguageTool(americanEnglish)

        // Ensure these rule IDs are correct and exist for the loaded language.
        // You can list available rule IDs using tool.allRules.map { it.id } if unsure.
        tool.disableRule("UPPERCASE_SENTENCE_START")
        tool.disableRule("I_LOWERCASE")

        return tool
    }
}
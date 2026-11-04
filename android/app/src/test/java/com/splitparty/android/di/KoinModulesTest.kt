package com.splitparty.android.di

import android.content.Context
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class KoinModulesTest {
    /**
     * Every constructor dependency in the graph has a definition. Context comes
     * from androidContext() at startup; String is the userId passed with parametersOf.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `app module graph is complete`() {
        module { includes(appModules) }.verify(extraTypes = listOf(Context::class, String::class))
    }
}

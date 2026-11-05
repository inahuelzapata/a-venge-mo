package com.splitparty.android.di

import android.content.Context
import com.splitparty.android.data.AvatarFetcher
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class KoinModulesTest {
    /**
     * Every constructor dependency in the graph has a definition. Context comes
     * from androidContext() at startup; String is the userId passed with parametersOf.
     * AvatarImageStore is built by hand (its directory under filesDir, default fetcher
     * and dispatcher), so its constructor types need no definitions.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `app module graph is complete`() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(Context::class, String::class, File::class, AvatarFetcher::class, CoroutineDispatcher::class)
        )
    }
}

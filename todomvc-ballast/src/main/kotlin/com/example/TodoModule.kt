package com.example

import io.kvision.routing.KVRouter
import io.kvision.routing.Routing
import kotlinx.browser.localStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val todoModule = module {
    single {
        Json {
            prettyPrint = true
        }
    }
    single {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
    single {
        localStorage
    }
    single<KVRouter> {
        Routing()
    }
    singleOf(::TodoSavedStateAdapter)
    singleOf(::TodoViewModel)
}

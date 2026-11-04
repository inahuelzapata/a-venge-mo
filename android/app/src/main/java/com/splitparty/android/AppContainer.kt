package com.splitparty.android

import android.app.Application
import android.content.Context
import com.splitparty.android.data.FirebaseConnection
import com.splitparty.android.data.FirebaseSetup
import com.splitparty.android.data.NetworkMonitor
import com.splitparty.android.data.SessionStore
import com.splitparty.android.data.SplitPartyRepository

/** The app's few long-lived objects, created once per process. */
class AppContainer(context: Context) {
    val connection: FirebaseConnection = FirebaseSetup.connect(context)
    val repository: SplitPartyRepository? = (connection as? FirebaseConnection.Ready)?.let { SplitPartyRepository(it.db) }
    val session = SessionStore(context)
    val network = NetworkMonitor(context)
}

class SplitPartyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

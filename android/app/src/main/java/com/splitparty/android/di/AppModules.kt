package com.splitparty.android.di

import com.google.firebase.firestore.FirebaseFirestore
import com.splitparty.android.data.AndroidConnectivityObserver
import com.splitparty.android.data.DataStoreSessionRepository
import com.splitparty.android.data.FirestoreSplitPartyRepository
import com.splitparty.android.data.firebase.FirebaseConnection
import com.splitparty.android.data.firebase.FirebaseSetup
import com.splitparty.android.data.firebase.FirestoreTarget
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.SessionRepository
import com.splitparty.android.domain.repository.SplitPartyRepository
import com.splitparty.android.domain.usecase.ObserveFeedUseCase
import com.splitparty.android.domain.usecase.ObserveSessionUseCase
import com.splitparty.android.domain.usecase.ObserveUserTransactionsUseCase
import com.splitparty.android.domain.usecase.ObserveUserUseCase
import com.splitparty.android.domain.usecase.ObserveUsersUseCase
import com.splitparty.android.domain.usecase.SignInUseCase
import com.splitparty.android.domain.usecase.SignOutUseCase
import com.splitparty.android.ui.home.HomeViewModel
import com.splitparty.android.ui.login.LoginViewModel
import com.splitparty.android.ui.me.MeViewModel
import com.splitparty.android.ui.root.SessionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Firestore connection for the configured target (android/firestore.properties).
 * [FirebaseFirestore] only exists for a Ready connection; the app root shows the
 * Setup screen instead of anything that would resolve it.
 */
val firebaseModule = module {
    single { FirebaseSetup.connect(androidContext()) }
    single<FirestoreTarget> { get<FirebaseConnection>().target }
    single<FirebaseFirestore> {
        when (val connection = get<FirebaseConnection>()) {
            is FirebaseConnection.Ready -> connection.db
            is FirebaseConnection.SetupRequired -> error("Firestore requested while setup is required: ${connection.problems}")
        }
    }
}

val dataModule = module {
    singleOf(::FirestoreSplitPartyRepository) { bind<SplitPartyRepository>() }
    singleOf(::DataStoreSessionRepository) { bind<SessionRepository>() }
    singleOf(::AndroidConnectivityObserver) { bind<ConnectivityObserver>() }
}

val domainModule = module {
    factoryOf(::SignInUseCase)
    factoryOf(::SignOutUseCase)
    factoryOf(::ObserveSessionUseCase)
    factoryOf(::ObserveFeedUseCase)
    factoryOf(::ObserveUserUseCase)
    factoryOf(::ObserveUserTransactionsUseCase)
    factoryOf(::ObserveUsersUseCase)
}

/** ViewModels that need the signed-in user get it as a parameter: `koinViewModel { parametersOf(userId) }`. */
val viewModelModule = module {
    viewModelOf(::SessionViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::MeViewModel)
}

val appModules = listOf(firebaseModule, dataModule, domainModule, viewModelModule)

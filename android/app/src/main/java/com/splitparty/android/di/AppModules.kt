package com.splitparty.android.di

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.splitparty.android.BuildConfig
import com.splitparty.android.data.AndroidConnectivityObserver
import com.splitparty.android.data.AvatarImageStore
import com.splitparty.android.data.DataStoreDemoSettingsRepository
import com.splitparty.android.data.DataStoreLastPartyRepository
import com.splitparty.android.data.DataStoreSessionRepository
import com.splitparty.android.data.FirebaseDemoResetRepository
import com.splitparty.android.data.FirestorePartyRepository
import com.splitparty.android.data.FirestoreSplitPartyRepository
import com.splitparty.android.data.LogcatAnalytics
import com.splitparty.android.data.MlKitReceiptRecognizer
import com.splitparty.android.data.firebase.FirebaseConnection
import com.splitparty.android.data.firebase.FirebaseSetup
import com.splitparty.android.data.firebase.FirestoreTarget
import com.splitparty.android.domain.Clock
import com.splitparty.android.domain.analytics.Analytics
import com.splitparty.android.domain.repository.ConnectivityObserver
import com.splitparty.android.domain.repository.DemoResetRepository
import com.splitparty.android.domain.repository.DemoSettingsRepository
import com.splitparty.android.domain.repository.LastPartyRepository
import com.splitparty.android.domain.repository.PartyRepository
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
import com.splitparty.android.ui.home.MerchantCardEntrance
import com.splitparty.android.ui.join.JoinViewModel
import com.splitparty.android.ui.login.LoginViewModel
import com.splitparty.android.ui.me.MeViewModel
import com.splitparty.android.ui.party.PartyViewModel
import com.splitparty.android.ui.root.SessionViewModel
import com.splitparty.android.ui.scan.ReceiptRecognizer
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
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
    single<FirebaseFunctions> { FirebaseSetup.functions(get<FirebaseFirestore>(), get<FirestoreTarget>()) }
}

val dataModule = module {
    singleOf(::FirestoreSplitPartyRepository) { bind<SplitPartyRepository>() }
    single<PartyRepository> { FirestorePartyRepository(get()) }
    single<DemoResetRepository> { FirebaseDemoResetRepository(get(), endpointUrl = BuildConfig.RESET_DEMO_URL) }
    singleOf(::DataStoreSessionRepository) { bind<SessionRepository>() }
    singleOf(::DataStoreDemoSettingsRepository) { bind<DemoSettingsRepository>() }
    singleOf(::DataStoreLastPartyRepository) { bind<LastPartyRepository>() }
    single<Analytics> { LogcatAnalytics() }
    singleOf(::AndroidConnectivityObserver) { bind<ConnectivityObserver>() }
    single<Clock> { Clock.System }
    single<ReceiptRecognizer> { MlKitReceiptRecognizer() }
    single { AvatarImageStore(File(androidContext().filesDir, "avatars")) }
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
    // App-wide UI state: the merchant card's arrival plays once per session (Home and Me share it).
    single { MerchantCardEntrance() }
    viewModelOf(::SessionViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::MeViewModel)
    // Two String parameters (partyId, userId): resolved by position, not by type.
    viewModel { (partyId: String, userId: String) ->
        PartyViewModel(partyId, userId, get(), get(), get(), get(), get(), get(), get<DemoSettingsRepository>().customShares)
    }
    viewModel { (partyId: String, userId: String) -> JoinViewModel(partyId, userId, get(), get(), get()) }
}

val appModules = listOf(firebaseModule, dataModule, domainModule, viewModelModule)

package com.nexmusic.app.di

import com.nexmusic.app.player.PlayerViewModel
import com.nexmusic.app.ui.screens.DetailViewModel
import com.nexmusic.app.ui.screens.HomeViewModel
import com.nexmusic.app.ui.screens.LibraryViewModel
import com.nexmusic.app.ui.screens.SearchViewModel
import com.nexmusic.app.sync.AccountSync
import com.nexmusic.app.sync.LocalLibrary
import com.nexmusic.app.taste.OnboardingViewModel
import com.nexmusic.app.update.AppUpdater
import com.nexmusic.app.taste.TasteStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.core.qualifier.named
import com.maxrave.common.Config
import com.nexmusic.app.library.LocalImport

val nexModule =
    module {
        viewModelOf(::PlayerViewModel)
        viewModelOf(::HomeViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::LibraryViewModel)
        viewModelOf(::DetailViewModel)
        viewModelOf(::OnboardingViewModel)
        single { TasteStore(androidContext()) }
        single { LocalLibrary(androidContext(), get(), get(), get(), get(), get(), get(), get(), get()) }
        single { AccountSync(androidContext(), get(), get(), get()) }
        single { AppUpdater(androidContext()) }
        single { LocalImport(androidContext(), get(), get(named(Config.DOWNLOAD_CACHE))) }
    }

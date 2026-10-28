package com.nexmusic.app.di

import com.nexmusic.app.player.PlayerViewModel
import com.nexmusic.app.ui.screens.DetailViewModel
import com.nexmusic.app.ui.screens.HomeViewModel
import com.nexmusic.app.ui.screens.LibraryViewModel
import com.nexmusic.app.ui.screens.SearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val nexModule =
    module {
        viewModelOf(::PlayerViewModel)
        viewModelOf(::HomeViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::LibraryViewModel)
        viewModelOf(::DetailViewModel)
    }

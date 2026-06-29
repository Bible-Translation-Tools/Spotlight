package org.bibletranslationtools.glossary.di

import org.bibletranslationtools.glossary.GlossaryDatabase
import org.bibletranslationtools.glossary.domain.CatalogApi
import org.bibletranslationtools.glossary.domain.CatalogApiImpl
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.FileSystemProviderImpl
import org.bibletranslationtools.glossary.domain.GlossaryApi
import org.bibletranslationtools.glossary.domain.GlossaryApiImpl
import org.bibletranslationtools.glossary.domain.InitApp
import org.bibletranslationtools.glossary.domain.usecases.UpdateLanguages
import org.bibletranslationtools.glossary.domain.createHttpClient
import org.bibletranslationtools.glossary.domain.persistence.GlossaryDataSource
import org.bibletranslationtools.glossary.domain.persistence.GlossaryDataSourceImpl
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepositoryImpl
import org.bibletranslationtools.glossary.domain.persistence.LanguageDataSource
import org.bibletranslationtools.glossary.domain.persistence.LanguageDataSourceImpl
import org.bibletranslationtools.glossary.domain.persistence.PhraseDataSource
import org.bibletranslationtools.glossary.domain.persistence.PhraseDataSourceImpl
import org.bibletranslationtools.glossary.domain.persistence.ResourceDataSource
import org.bibletranslationtools.glossary.domain.persistence.ResourceDataSourceImpl
import org.bibletranslationtools.glossary.domain.persistence.SettingsDataSource
import org.bibletranslationtools.glossary.domain.persistence.SettingsDataSourceImpl
import org.bibletranslationtools.glossary.domain.usecases.ExportGlossary
import org.bibletranslationtools.glossary.domain.usecases.ImportGlossary
import org.bibletranslationtools.glossary.domain.usecases.MergePendingPhrases
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.platform.createSqlDriver
import org.bibletranslationtools.glossary.platform.httpClientEngine
import org.bibletranslationtools.glossary.ui.state.AppStateStore
import org.bibletranslationtools.glossary.ui.state.AppStateStoreImpl
import org.bibletranslationtools.glossary.ui.state.GlossaryStateHolder
import org.bibletranslationtools.glossary.ui.state.GlossaryStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.ResourceStateHolder
import org.bibletranslationtools.glossary.ui.state.ResourceStateHolderImpl
import org.bibletranslationtools.glossary.ui.state.UserStateHolder
import org.bibletranslationtools.glossary.ui.state.UserStateHolderImpl
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val sharedModule = module {
    single { GlossaryDatabase(createSqlDriver()) }
    singleOf(::ResourceContainerAccessor)
    single { CatalogApiImpl(createHttpClient(httpClientEngine)) }.bind<CatalogApi>()
    single {
        GlossaryApiImpl(
            httpClient = createHttpClient(httpClientEngine),
            userStateHolder = get()
        )
    }.bind<GlossaryApi>()

    singleOf(::GlossaryDataSourceImpl).bind<GlossaryDataSource>()
    singleOf(::PhraseDataSourceImpl).bind<PhraseDataSource>()
    singleOf(::SettingsDataSourceImpl).bind<SettingsDataSource>()
    singleOf(::LanguageDataSourceImpl).bind<LanguageDataSource>()
    singleOf(::ResourceDataSourceImpl).bind<ResourceDataSource>()
    singleOf(::FileSystemProviderImpl).bind<FileSystemProvider>()
    singleOf(::GlossaryRepositoryImpl).bind<GlossaryRepository>()
    singleOf(::ExportGlossary)
    singleOf(::ImportGlossary)
    singleOf(::MergePendingPhrases)
    single { UpdateLanguages(get(), createHttpClient(httpClientEngine)) }

    factoryOf(::InitApp)

    singleOf(::ResourceStateHolderImpl).bind<ResourceStateHolder>()
    singleOf(::GlossaryStateHolderImpl).bind<GlossaryStateHolder>()
    singleOf(::UserStateHolderImpl).bind<UserStateHolder>()
    singleOf(::AppStateStoreImpl).bind<AppStateStore>()
}

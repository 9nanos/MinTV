package tv.own.owntv.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.core.metadata.MetadataProvider
import tv.own.owntv.core.metadata.TmdbProvider
import tv.own.owntv.features.metadata.ConservativeMetadataProvider
import tv.own.owntv.features.metadata.BoundedTrendingProvider
import tv.own.owntv.features.metadata.MetadataResponseLimit
import tv.own.owntv.core.network.HttpClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Last module: decorate the existing provider, retaining its config/auth/budget implementation. */
val minTvMetadataModule = module {
    single<MetadataProvider> { BoundedTrendingProvider(ConservativeMetadataProvider(TmdbProvider(
        HttpClient(get<OkHttpClient>().newBuilder().callTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(MetadataResponseLimit()).build()), get(), get(), get()))) }
    // 0.20 one Sport: the upstream Sports section reads SHL and HockeyAllsvenskan from Min TV's Swedish
    // hockey reader; ESPN keeps every other league. Overrides Core's ESPN-only binding (loaded first).
    single<tv.own.owntv.core.sports.SportsSource> {
        tv.own.owntv.features.sports.SwedishHockeySource(tv.own.owntv.core.sports.EspnSportsSource(get()),
            get<tv.own.owntv.features.home.SportsRepository>(),
            androidContext().getString(tv.own.owntv.R.string.mintv_hockey_final),
            { home, away -> androidContext().getString(tv.own.owntv.R.string.hub_pair, home, away) })
    }
}

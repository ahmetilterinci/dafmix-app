package xyz.dafmix.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import xyz.dafmix.app.download.Dl

class DafmixApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Svc.init(this)
        Dl.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(Svc.client)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true)
            .build()
}

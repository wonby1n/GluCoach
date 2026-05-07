package com.ssafy.s309.ui.component

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest

@Composable
fun KikiImage(
    @DrawableRes drawableRes: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val imageLoader =
        remember(context) {
            ImageLoader.Builder(context)
                .components {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(AnimatedImageDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .build()
        }

    AsyncImage(
        model =
            ImageRequest.Builder(context)
                .data(drawableRes)
                .build(),
        imageLoader = imageLoader,
        contentDescription = "키키 캐릭터",
        modifier = modifier,
        contentScale = contentScale,
    )
}

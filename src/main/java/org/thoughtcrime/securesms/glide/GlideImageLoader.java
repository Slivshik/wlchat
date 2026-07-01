package org.thoughtcrime.securesms.glide;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import org.thoughtcrime.securesms.R;

/**
 * Utility for loading images with cache-first strategy and smooth fade-in transitions.
 * Mimics Telegram's approach of loading from cache instantly, then fading in from network.
 */
public class GlideImageLoader {

  private static final int FADE_DURATION = 300;

  /**
   * Load an image with disk cache and smooth fade-in transition.
   * If cached, loads instantly. If from network, fades in smoothly.
   */
  public static <T> void loadImage(@NonNull RequestBuilder<T> builder, @NonNull ImageView imageView) {
    builder
        .apply(new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false))
        .listener(new RequestListener<T>() {
          @Override
          public boolean onLoadFailed(@Nullable GlideException e, Object model,
              Target<T> target, boolean isFirstResource) {
            return false;
          }

          @Override
          public boolean onResourceReady(T resource, Object model, Target<T> target,
              DataSource dataSource, boolean isFirstResource) {
            // Fade in only for network loads (not cache)
            if (dataSource != DataSource.MEMORY_CACHE) {
              imageView.setAlpha(0f);
              imageView.animate()
                  .alpha(1f)
                  .setDuration(FADE_DURATION)
                  .start();
            }
            return false;
          }
        })
        .into(imageView);
  }

  /**
   * Load a contact photo with circle crop, disk cache, and fade-in.
   */
  public static void loadContactPhoto(@NonNull Context context, @NonNull String uri,
      @NonNull ImageView imageView) {
    Glide.with(context)
        .load(uri)
        .apply(new RequestOptions()
            .transform(new CircleCrop())
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false))
        .placeholder(R.drawable.ic_contact_picture)
        .listener(new RequestListener<Drawable>() {
          @Override
          public boolean onLoadFailed(@Nullable GlideException e, Object model,
              Target<Drawable> target, boolean isFirstResource) {
            return false;
          }

          @Override
          public boolean onResourceReady(Drawable resource, Object model,
              Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            if (dataSource != DataSource.MEMORY_CACHE) {
              imageView.setAlpha(0f);
              imageView.animate()
                  .alpha(1f)
                  .setDuration(FADE_DURATION)
                  .start();
            }
            return false;
          }
        })
        .into(imageView);
  }

  /**
   * Load a rounded corner image with disk cache and fade-in.
   */
  public static void loadRoundedImage(@NonNull Context context, @NonNull Object model,
      @NonNull ImageView imageView, int cornerRadiusDp) {
    int cornerRadius = (int) (cornerRadiusDp * context.getResources().getDisplayMetrics().density);
    Glide.with(context)
        .load(model)
        .apply(new RequestOptions()
            .transform(new RoundedCorners(cornerRadius))
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false))
        .listener(new RequestListener<Drawable>() {
          @Override
          public boolean onLoadFailed(@Nullable GlideException e, Object m,
              Target<Drawable> target, boolean isFirstResource) {
            return false;
          }

          @Override
          public boolean onResourceReady(Drawable resource, Object m,
              Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            if (dataSource != DataSource.MEMORY_CACHE) {
              imageView.setAlpha(0f);
              imageView.animate()
                  .alpha(1f)
                  .setDuration(FADE_DURATION)
                  .start();
            }
            return false;
          }
        })
        .into(imageView);
  }

  /**
   * Preload images into disk cache for smooth scrolling.
   * Call this for upcoming items in a list.
   */
  public static void preload(@NonNull Context context, @NonNull Object model) {
    Glide.with(context)
        .load(model)
        .apply(new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL))
        .preload();
  }
}

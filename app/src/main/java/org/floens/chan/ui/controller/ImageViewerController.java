/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.controller;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getDimen;
import static org.floens.chan.utils.AndroidUtils.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.davemorrissey.labs.subscaleview.ImageViewState;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.presenter.ImageViewerPresenter;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.site.ImageSearch;
import org.floens.chan.ui.adapter.ImageViewerAdapter;
import org.floens.chan.ui.toolbar.NavigationItem;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.toolbar.ToolbarMenuSubItem;
import org.floens.chan.ui.view.CustomScaleImageView;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.LoadingBar;
import org.floens.chan.ui.view.MultiImageView;
import org.floens.chan.ui.view.OptionalSwipeViewPager;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.ui.view.TransitionImageView;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import okhttp3.HttpUrl;

public class ImageViewerController extends Controller implements ImageViewerPresenter.Callback {
    private static final String TAG = "ImageViewerController";
    private static final int TRANSITION_DURATION = 300;
    private static final float TRANSITION_FINAL_ALPHA = 0.85f;

    private static final int VOLUME_ID = 1;

    @Inject
    ImageLoader imageLoader;

    private int statusBarColorPrevious;
    private AnimatorSet startAnimation;
    private AnimatorSet endAnimation;

    private ImageViewerCallback imageViewerCallback;
    private GoPostCallback goPostCallback;
    private ImageViewerPresenter presenter;

    private final Toolbar toolbar;
    private TransitionImageView previewImage;
    private OptionalSwipeViewPager pager;
    private LoadingBar loadingBar;

    private static final int SUBITEM_TRANSPARENCY_TOGGLE_ID = 220;
    private static final int SUBITEM_ROTATE_IMAGE_ID = 221;
    private ToolbarMenu toolbarMenu;

    private boolean isInImmersiveMode = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public ImageViewerController(Context context, Toolbar toolbar) {
        super(context);
        inject(this);

        this.toolbar = toolbar;

        presenter = new ImageViewerPresenter(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Navigation
        navigation.subtitle = "0";

        NavigationItem.MenuBuilder menuBuilder = navigation.buildMenu();
        if (goPostCallback != null) {
            menuBuilder.withItem(R.drawable.ic_subdirectory_arrow_left_white_24dp, this::goPostClicked);
        }

        menuBuilder.withItem(VOLUME_ID, R.drawable.ic_volume_off_white_24dp, this::volumeClicked);
        menuBuilder.withItem(R.drawable.ic_file_download_white_24dp, this::saveClicked);

        NavigationItem.MenuOverflowBuilder overflowBuilder = menuBuilder.withOverflow();
        overflowBuilder.withSubItem(R.string.action_open_browser, this::openBrowserClicked);
        overflowBuilder.withSubItem(R.string.action_copy_url, this::clipboardURL);
        overflowBuilder.withSubItem(R.string.action_share, this::shareClicked);
        overflowBuilder.withSubItem(R.string.action_search_image, this::searchClicked);
        overflowBuilder.withSubItem(R.string.action_download_album, this::downloadAlbumClicked);
        overflowBuilder.withSubItem(SUBITEM_TRANSPARENCY_TOGGLE_ID, R.string.action_transparency_toggle, this::toggleTransparency);
        overflowBuilder.withSubItem(SUBITEM_ROTATE_IMAGE_ID, R.string.action_rotate_image, this::setOrientation);

        toolbarMenu = overflowBuilder.build().build();

        hideSystemUI();

        // View setup
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        view = inflateRes(R.layout.controller_image_viewer);
        previewImage = view.findViewById(R.id.preview_image);
        pager = view.findViewById(R.id.pager);
        pager.addOnPageChangeListener(presenter);
        loadingBar = view.findViewById(R.id.loading_bar);

        showVolumeMenuItem(false, true);

        // Sanity check
        if (parentController.view.getWindowToken() == null) {
            throw new IllegalArgumentException("parentController.view not attached");
        }

        AndroidUtils.waitForLayout(parentController.view.getViewTreeObserver(), view, view -> {
            presenter.onViewMeasured();
            return true;
        });
    }

    private void goPostClicked(ToolbarMenuItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        ImageViewerCallback imageViewerCallback = goPostCallback.goToPost(postImage);
        if (imageViewerCallback != null) {
            // hax: we need to wait for the recyclerview to do a layout before we know
            // where the new thumbnails are to get the bounds from to animate to
            this.imageViewerCallback = imageViewerCallback;
            AndroidUtils.waitForLayout(view, view -> {
                showSystemUI();
                handler.removeCallbacksAndMessages(null);
                presenter.onExit();
                return false;
            });
        } else {
            showSystemUI();
            handler.removeCallbacksAndMessages(null);
            presenter.onExit();
        }
    }

    private void volumeClicked(ToolbarMenuItem item) {
        presenter.onVolumeClicked();
    }

    private void saveClicked(ToolbarMenuItem item) {
        presenter.saveClicked(presenter.getCurrentPostImage());
    }

    private void openBrowserClicked(ToolbarMenuSubItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        if (ChanSettings.openLinkBrowser.get()) {
            AndroidUtils.openLink(postImage.imageUrl.toString());
        } else {
            AndroidUtils.openLinkInBrowser((Activity) context, postImage.imageUrl.toString());
        }
    }

    private void clipboardURL(ToolbarMenuSubItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        ClipboardManager clipboard = (ClipboardManager) AndroidUtils.getAppContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("File URL", postImage.imageUrl.toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, R.string.url_text_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareClicked(ToolbarMenuSubItem item) {
        presenter.shareClicked(presenter.getCurrentPostImage());
    }

    private void searchClicked(ToolbarMenuSubItem item) {
        showImageSearchOptions(presenter.getLoadable().board.code);
    }

    private void downloadAlbumClicked(ToolbarMenuSubItem item) {
        List<PostImage> all = presenter.getAllPostImages();
        AlbumDownloadController albumDownloadController = new AlbumDownloadController(context);
        albumDownloadController.setPostImages(presenter.getLoadable(), all);
        navigationController.pushController(albumDownloadController);
    }

    private void toggleTransparency(ToolbarMenuSubItem item) {
        ((ImageViewerAdapter) pager.getAdapter()).toggleTransparency(presenter.getCurrentPostImage());
    }

    private void setOrientation(ToolbarMenuSubItem item) {
        List<FloatingMenuItem> orientations = Arrays.asList(
                new FloatingMenuItem(0, "Reset view"),
                new FloatingMenuItem(90, "Rotate 90 deg."),
                new FloatingMenuItem(180, "Rotate 180 deg."),
                new FloatingMenuItem(270, "Rotate 270 deg."),
                new FloatingMenuItem(-1, "Flip horizontally"),
                new FloatingMenuItem(-2, "Flip vertically")
        );
        FloatingMenu menu = new FloatingMenu(context, view, orientations);
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                ((ImageViewerAdapter) pager.getAdapter()).setOrientation(presenter.getCurrentPostImage(), (int) item.getId());
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) { }
        });
        menu.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        showSystemUI();
        handler.removeCallbacksAndMessages(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public boolean onBack() {
        if (presenter.isTransitioning()) return false;
        showSystemUI();
        handler.removeCallbacksAndMessages(null);
        presenter.onExit();
        return true;
    }

    public void setImageViewerCallback(ImageViewerCallback imageViewerCallback) {
        this.imageViewerCallback = imageViewerCallback;
    }

    public void setGoPostCallback(GoPostCallback goPostCallback) {
        this.goPostCallback = goPostCallback;
    }

    public ImageViewerPresenter getPresenter() {
        return presenter;
    }

    public void setPreviewVisibility(boolean visible) {
        previewImage.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    public void setPagerVisiblity(boolean visible) {
        pager.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        pager.setSwipingEnabled(visible);
    }

    public void setPagerItems(List<PostImage> images, int initialIndex) {
        ImageViewerAdapter adapter = new ImageViewerAdapter(context, images, presenter);
        pager.setAdapter(adapter);
        pager.setCurrentItem(initialIndex);
    }

    public void setImageMode(PostImage postImage, MultiImageView.Mode mode, boolean center) {
        ((ImageViewerAdapter) pager.getAdapter()).setMode(postImage, mode, center);
    }

    @Override
    public void setVolume(PostImage postImage, boolean muted) {
        ((ImageViewerAdapter) pager.getAdapter()).setVolume(postImage, muted);
    }

    public MultiImageView.Mode getImageMode(PostImage postImage) {
        return ((ImageViewerAdapter) pager.getAdapter()).getMode(postImage);
    }

    public void setTitle(PostImage postImage, int index, int count, boolean spoiler) {
        if (spoiler) {
            navigation.title = getString(R.string.image_spoiler_filename);
        } else {
            navigation.title = postImage.filename + "." + postImage.extension;
        }
        navigation.subtitle = (index + 1) + "/" + count;
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);

        // TODO this probably should be a separate function
        if (toolbarMenu == null) return;
        MultiImageView.Mode imageMode = getImageMode(postImage);
        boolean enabled = !spoiler && (imageMode == MultiImageView.Mode.BIGIMAGE || imageMode == MultiImageView.Mode.GIF);
        toolbarMenu.findSubItem(SUBITEM_TRANSPARENCY_TOGGLE_ID).enabled = enabled;
        toolbarMenu.findSubItem(SUBITEM_ROTATE_IMAGE_ID).enabled = enabled;
    }

    public void scrollToImage(PostImage postImage) {
        imageViewerCallback.scrollToImage(postImage);
    }

    public void showProgress(boolean show) {
        loadingBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public void onLoadProgress(float progress) {
        loadingBar.setProgress(progress);
    }

    public void onVideoError(MultiImageView multiImageView) {
        if (ChanSettings.videoErrorIgnore.get()) {
            Toast.makeText(context, R.string.image_open_failed, Toast.LENGTH_SHORT).show();
        } else {
            @SuppressLint("InflateParams")
            View notice = LayoutInflater.from(context).inflate(R.layout.dialog_video_error, null);
            final CheckBox dontShowAgain = notice.findViewById(R.id.checkbox);

            new AlertDialog.Builder(context)
                    .setTitle(R.string.video_playback_warning_title)
                    .setView(notice)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (dontShowAgain.isChecked()) {
                                ChanSettings.videoErrorIgnore.set(true);
                            }
                        }
                    })
                    .setCancelable(false)
                    .show();
        }
    }

    @Override
    public void showVolumeMenuItem(boolean show, boolean muted) {
        ToolbarMenuItem volumeMenuItem = navigation.findItem(VOLUME_ID);
        volumeMenuItem.setVisible(show);
        volumeMenuItem.setImage(
                muted ? R.drawable.ic_volume_off_white_24dp : R.drawable.ic_volume_up_white_24dp);
    }

    private void showImageSearchOptions(String boardCode) {
        // TODO: move to presenter
        List<FloatingMenuItem> items = new ArrayList<>();
        for (ImageSearch imageSearch : ImageSearch.engines) {
            if (imageSearch.showFor(boardCode)) {
                items.add(new FloatingMenuItem(imageSearch.getId(), imageSearch.getName()));
            }
        }
        ToolbarMenuItem overflowMenuItem = navigation.findItem(ToolbarMenu.OVERFLOW_ID);
        FloatingMenu menu = new FloatingMenu(context, overflowMenuItem.getView(), items);
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                for (ImageSearch imageSearch : ImageSearch.engines) {
                    if (((Integer) item.getId()) == imageSearch.getId()) {
                        final HttpUrl searchImageUrl = getSearchImageUrl(presenter.getCurrentPostImage());
                        AndroidUtils.openLinkInBrowser((Activity) context, imageSearch.getUrl(searchImageUrl.toString()));
                        break;
                    }
                }
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {
            }
        });
        menu.show();
    }

    @Override
    public boolean isImmersive() {
        return ChanSettings.useImmersiveModeForGallery.get() && isInImmersiveMode;
    }

    public void startPreviewInTransition(PostImage postImage) {
        ThumbnailView startImageView = getTransitionImageView(postImage);

        if (!setTransitionViewData(startImageView)) {
            Logger.test("Oops");
            return; // TODO
        }

        if (Build.VERSION.SDK_INT >= 21) {
            statusBarColorPrevious = getWindow().getStatusBarColor();
        }

        setBackgroundAlpha(0f);

        startAnimation = new AnimatorSet();

        ValueAnimator progress = ValueAnimator.ofFloat(0f, 1f);
        progress.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setBackgroundAlpha(Math.min(1f, (float) animation.getAnimatedValue()));
                previewImage.setProgress((float) animation.getAnimatedValue());
            }
        });

        startAnimation.play(progress);
        startAnimation.setDuration(TRANSITION_DURATION);
        startAnimation.setInterpolator(new DecelerateInterpolator(3f));
        startAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                imageViewerCallback.onPreviewCreate(
                        ImageViewerController.this, postImage);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                startAnimation = null;
                presenter.onInTransitionEnd();
            }
        });

        imageLoader.get(postImage.getThumbnailUrl().toString(), new ImageLoader.ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "onErrorResponse for preview in transition in ImageViewerController, cannot show correct transition bitmap");
                startAnimation.start();
            }

            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                if (response.getBitmap() != null) {
                    previewImage.setBitmap(response.getBitmap());
                    startAnimation.start();
                }
            }
        }, previewImage.getWidth(), previewImage.getHeight());
    }

    public void startPreviewOutTransition(final PostImage postImage) {
        if (startAnimation != null || endAnimation != null) {
            return;
        }

        imageLoader.get(postImage.getThumbnailUrl().toString(), new ImageLoader.ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "onErrorResponse for preview out transition in ImageViewerController, cannot show correct transition bitmap");
                doPreviewOutAnimation(postImage, null);
            }

            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                if (response.getBitmap() != null) {
                    doPreviewOutAnimation(postImage, response.getBitmap());
                }
            }
        }, previewImage.getWidth(), previewImage.getHeight());
    }

    private void doPreviewOutAnimation(PostImage postImage, Bitmap bitmap) {
        // Find translation and scale if the current displayed image was a bigimage
        MultiImageView multiImageView = ((ImageViewerAdapter) pager.getAdapter()).find(postImage);
        CustomScaleImageView customScaleImageView = multiImageView.findScaleImageView();
        if (customScaleImageView != null) {
            ImageViewState state = customScaleImageView.getState();
            if (state != null) {
                PointF p = customScaleImageView.viewToSourceCoord(0f, 0f);
                PointF bitmapSize = new PointF(customScaleImageView.getSWidth(), customScaleImageView.getSHeight());
                previewImage.setState(state.getScale(), p, bitmapSize);
            }
        }

        ThumbnailView startImage = getTransitionImageView(postImage);

        endAnimation = new AnimatorSet();
        if (!setTransitionViewData(startImage) || bitmap == null) {
            if (bitmap != null) {
                previewImage.setBitmap(bitmap);
            }
            ValueAnimator backgroundAlpha = ValueAnimator.ofFloat(1f, 0f);
            backgroundAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setBackgroundAlpha((float) animation.getAnimatedValue());
                }
            });

            endAnimation
                    .play(ObjectAnimator.ofFloat(previewImage, View.Y, previewImage.getTop(), previewImage.getTop() + dp(20)))
                    .with(ObjectAnimator.ofFloat(previewImage, View.ALPHA, 1f, 0f))
                    .with(backgroundAlpha);

        } else {
            ValueAnimator progress = ValueAnimator.ofFloat(1f, 0f);
            progress.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setBackgroundAlpha((float) animation.getAnimatedValue());
                    previewImage.setProgress((float) animation.getAnimatedValue());
                }
            });

            endAnimation.play(progress);
        }
        endAnimation.setDuration(TRANSITION_DURATION);
        endAnimation.setInterpolator(new DecelerateInterpolator(3f));
        endAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                previewOutAnimationEnded(postImage);
            }
        });
        endAnimation.start();
        imageViewerCallback.onBeforePreviewDestroy(this, postImage);
    }

    private void previewOutAnimationEnded(PostImage postImage) {
        setBackgroundAlpha(0f);

        imageViewerCallback.onPreviewDestroy(this, postImage);
        navigationController.stopPresenting(false);
    }

    private boolean setTransitionViewData(ThumbnailView startView) {
        if (startView == null || startView.getWindowToken() == null) {
            return false;
        }

        Bitmap bitmap = startView.getBitmap();
        if (bitmap == null) {
            return false;
        }

        int[] loc = new int[2];
        startView.getLocationInWindow(loc);
        Point windowLocation = new Point(loc[0], loc[1]);
        Point size = new Point(startView.getWidth(), startView.getHeight());
        previewImage.setSourceImageView(windowLocation, size, bitmap, startView.getRounding());
        return true;
    }

    private void setBackgroundAlpha(float alpha) {
        navigationController.view.setBackgroundColor(Color.argb((int) (alpha * TRANSITION_FINAL_ALPHA * 255f), 0, 0, 0));

        if (Build.VERSION.SDK_INT >= 21) {
            if (alpha == 0f) {
                setStatusBarColor(statusBarColorPrevious);
            } else {
                int r = (int) ((1f - alpha) * Color.red(statusBarColorPrevious));
                int g = (int) ((1f - alpha) * Color.green(statusBarColorPrevious));
                int b = (int) ((1f - alpha) * Color.blue(statusBarColorPrevious));
                setStatusBarColor(Color.argb(255, r, g, b));
            }
        }

        setToolbarBackgroundAlpha(alpha);
    }

    private void setStatusBarColor(int color) {
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(color);
        }
    }

    private void setToolbarBackgroundAlpha(float alpha) {
        toolbar.setAlpha(alpha);
        loadingBar.setAlpha(alpha);
    }

    private ThumbnailView getTransitionImageView(PostImage postImage) {
        return imageViewerCallback.getPreviewImageTransitionView(this, postImage);
    }

    private void hideSystemUI() {
        if (!ChanSettings.useImmersiveModeForGallery.get() || isInImmersiveMode) {
            return;
        }

        isInImmersiveMode = true;

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);

        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 && isInImmersiveMode) {
                showSystemUI();
                handler.postDelayed(this::hideSystemUI, 2500);
            }
        });

        //setting this to 0 because GONE doesn't seem to work?
        ViewGroup.LayoutParams params = navigationController.getToolbar().getLayoutParams();
        params.height = 0;
        navigationController.getToolbar().setLayoutParams(params);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            navigationController.getToolbar().bringToFront();
    }

    @Override
    public void showSystemUI(boolean show) {
        if (show) {
            showSystemUI();
            handler.postDelayed(this::hideSystemUI, 2500);
        } else {
            hideSystemUI();
        }
    }

    private void showSystemUI() {
        if (!ChanSettings.useImmersiveModeForGallery.get() || !isInImmersiveMode) {
            return;
        }

        isInImmersiveMode = false;

        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(null);
        decorView.setSystemUiVisibility(0);

        //setting this to the toolbar height because VISIBLE doesn't seem to work?
        ViewGroup.LayoutParams params = navigationController.getToolbar().getLayoutParams();
        params.height = getDimen(context, R.dimen.toolbar_height);
        navigationController.getToolbar().setLayoutParams(params);
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

    public interface ImageViewerCallback {
        ThumbnailView getPreviewImageTransitionView(ImageViewerController imageViewerController, PostImage postImage);

        void onPreviewCreate(ImageViewerController imageViewerController, PostImage postImage);

        void onBeforePreviewDestroy(ImageViewerController imageViewerController, PostImage postImage);

        void onPreviewDestroy(ImageViewerController imageViewerController, PostImage postImage);

        void scrollToImage(PostImage postImage);
    }

    public interface GoPostCallback {
        ImageViewerCallback goToPost(PostImage postImage);
    }

    /**
     * Send thumbnail image of movie posts because none of the image search providers support movies (such as webm) directly
     *
     * @param postImage the post image
     * @return url of an image to be searched
     */
    private HttpUrl getSearchImageUrl(final PostImage postImage) {
        return postImage.type == PostImage.Type.MOVIE ? postImage.thumbnailUrl : postImage.imageUrl;
    }
}

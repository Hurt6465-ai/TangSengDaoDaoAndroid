package com.chat.base.glide;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.chat.base.R;
import com.chat.base.WKBaseApplication;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.EditImgMenu;
import com.chat.base.utils.WKLogUtils;
import com.luck.picture.lib.animators.AnimationType;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.config.PictureMimeType;
import com.luck.picture.lib.config.SelectMimeType;
import com.luck.picture.lib.engine.CompressFileEngine;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.interfaces.OnKeyValueResultCallbackListener;
import com.luck.picture.lib.interfaces.OnResultCallbackListener;
import com.luck.picture.lib.style.BottomNavBarStyle;
import com.luck.picture.lib.style.PictureSelectorStyle;
import com.luck.picture.lib.style.PictureWindowAnimationStyle;
import com.luck.picture.lib.style.SelectMainStyle;
import com.luck.picture.lib.style.TitleBarStyle;
import com.luck.picture.lib.utils.DateUtils;
import com.luck.picture.lib.utils.DensityUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import top.zibin.luban.Luban;
import top.zibin.luban.OnCompressListener;
import top.zibin.luban.OnNewCompressListener;
import top.zibin.luban.OnRenameListener;

/**
 * 2019-12-02 13:52
 * glide管理
 */
public class GlideUtils {
    private GlideUtils() {

    }

    private static class GlideUtilsBinder {
        private final static GlideUtils glideUtils = new GlideUtils();
    }

    public static GlideUtils getInstance() {
        return GlideUtilsBinder.glideUtils;
    }

    public void showImg(Context mContext, String url, ImageView imageView) {
        if (mContext != null) {
            WeakReference<Context> weakReference = new WeakReference<>(mContext);
            Context context = weakReference.get();
            if (context instanceof Activity activity) {
                if (!activity.isDestroyed()) {
                    Glide.with(context).load(url)
                            .apply(GlideRequestOptions.getInstance().normalRequestOption())
                            .into(imageView);
                }
            }
        }
    }

    public void showGif(Context mContext, String url, ImageView imageView, final ILoadGIFRequestListener iLoadGIFRequestListener) {
        if (mContext != null) {
            WeakReference<Context> weakReference = new WeakReference<>(mContext);
            Context context = weakReference.get();
            if (context instanceof Activity activity) {
                if (!activity.isDestroyed()) {
                    Glide.with(context).asGif().load(url).listener(new RequestListener<>() {
                                @Override
                                public boolean onLoadFailed(@Nullable @org.jetbrains.annotations.Nullable GlideException e, Object model, Target<GifDrawable> target, boolean isFirstResource) {
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(GifDrawable resource, Object model, Target<GifDrawable> target, DataSource dataSource, boolean isFirstResource) {
                                    if (iLoadGIFRequestListener != null) {
                                        iLoadGIFRequestListener.onSuccess();
                                    }
                                    return false;
                                }
                            })
                            .apply(GlideRequestOptions.getInstance().normalRequestOption())
                            .into(imageView);
                }
            }
        }
    }

    public interface ILoadGIFRequestListener {
        void onSuccess();
    }

    public void showImg(Context mContext, String url, int width, int height, ImageView imageView) {
        if (mContext != null) {
            WeakReference<Context> weakReference = new WeakReference<>(mContext);
            Context context = weakReference.get();
            if (context instanceof Activity activity) {
                if (!activity.isDestroyed()) {
                    Glide.with(context).load(url)
                            .apply(GlideRequestOptions.getInstance().normalRequestOption(width, height))
                            .into(imageView);
                }
            }
        }
    }

    public void showAvatarImg(Context mContext, String channelID, byte channelType, String key, ImageView imageView) {
        String url = WKApiConfig.getShowAvatar(channelID, channelType);
        showAvatarImg(mContext, url, key, imageView);
    }

    public void showRoundedAvatar(Context mContext, String url, String key, ImageView imageView, int radius) {
        RequestOptions options = new RequestOptions().transform(new RoundedCorners(radius));
        if (mContext != null) {
            WeakReference<Context> weakReference = new WeakReference<>(mContext);
            Context context = weakReference.get();
            if (context instanceof Activity activity) {
                if (!activity.isDestroyed()) {
                    if (TextUtils.isEmpty(key)) {
                        Glide.with(context)
                                .load(url)
                                .apply(options)
                                .into(imageView);
                    } else {
                        Glide.with(context)
                                .load(new MyGlideUrlWithId(url, key)).dontAnimate()
                                .apply(options)
                                .into(imageView);
                    }

                }
            }
        }
// 在ImageView上显示圆角头像

    }

    public void showAvatarImg(Context mContext, String url, String key, ImageView imageView) {
        if (mContext != null) {
            WeakReference<Context> weakReference = new WeakReference<>(mContext);
            Context context = weakReference.get();
            if (context instanceof Activity activity) {
                if (!activity.isDestroyed()) {
                    if (TextUtils.isEmpty(key)) {
                        Glide.with(context).load(url).dontAnimate()
                                .apply(GlideRequestOptions.getInstance().normalRequestOption())
                                .into(imageView);
                    } else {
                        Glide.with(context).load(new MyGlideUrlWithId(url, key)).dontAnimate()
                                .apply(GlideRequestOptions.getInstance().normalRequestOption())
                                .into(imageView);
                    }

                }
            }
        }
    }

    public interface ISelectBack {
        void onBack(List<ChooseResult> paths);

        void onCancel();
    }

    public void chooseIMG(Activity activity, int maxSelectNum, final ISelectBack iSelectBack) {
        chooseIMG(activity, maxSelectNum, false, ChooseMimeType.all, true, iSelectBack);
    }

    private SelectMainStyle getMainStyle(Context context) {
        SelectMainStyle numberSelectMainStyle = new SelectMainStyle();
        numberSelectMainStyle.setSelectNumberStyle(true);
        numberSelectMainStyle.setPreviewSelectNumberStyle(false);
        numberSelectMainStyle.setPreviewDisplaySelectGallery(true);
        numberSelectMainStyle.setSelectBackground(R.drawable.ps_default_num_selector);
        numberSelectMainStyle.setPreviewSelectBackground(R.drawable.ps_preview_checkbox_selector);
        numberSelectMainStyle.setSelectNormalBackgroundResources(R.drawable.ps_select_complete_normal_bg);
        numberSelectMainStyle.setSelectNormalTextColor(ContextCompat.getColor(context, R.color.ps_color_53575e));
        numberSelectMainStyle.setSelectNormalText(context.getString(R.string.ps_send));
        numberSelectMainStyle.setAdapterPreviewGalleryBackgroundResource(R.drawable.ps_preview_gallery_bg);
        numberSelectMainStyle.setAdapterPreviewGalleryItemSize(DensityUtil.dip2px(context, 52));
        numberSelectMainStyle.setPreviewSelectText(context.getString(R.string.ps_select));
        numberSelectMainStyle.setPreviewSelectTextSize(14);
        numberSelectMainStyle.setPreviewSelectTextColor(ContextCompat.getColor(context, R.color.ps_color_white));
        numberSelectMainStyle.setPreviewSelectMarginRight(DensityUtil.dip2px(context, 6));
        numberSelectMainStyle.setSelectBackgroundResources(R.drawable.ps_select_complete_bg);
        numberSelectMainStyle.setSelectText(context.getString(R.string.ps_send_num));
        numberSelectMainStyle.setSelectTextColor(ContextCompat.getColor(context, R.color.ps_color_white));
        numberSelectMainStyle.setMainListBackgroundColor(ContextCompat.getColor(context, R.color.ps_color_black));
        numberSelectMainStyle.setCompleteSelectRelativeTop(true);
        numberSelectMainStyle.setPreviewSelectRelativeBottom(true);
        numberSelectMainStyle.setAdapterItemIncludeEdge(false);
        return numberSelectMainStyle;
    }

    private TitleBarStyle getTitleBarStyle() {
        // 头部TitleBar 风格
        TitleBarStyle numberTitleBarStyle = new TitleBarStyle();
        numberTitleBarStyle.setHideCancelButton(true);
        numberTitleBarStyle.setAlbumTitleRelativeLeft(true);
        numberTitleBarStyle.setTitleAlbumBackgroundResource(R.drawable.ps_album_bg);
        numberTitleBarStyle.setTitleDrawableRightResource(R.drawable.ps_ic_grey_arrow);
        numberTitleBarStyle.setPreviewTitleLeftBackResource(R.drawable.ps_ic_normal_back);
        return numberTitleBarStyle;
    }

    private BottomNavBarStyle getBottomNavBarStyle(Context context) {
        // 底部NavBar 风格
        BottomNavBarStyle numberBottomNavBarStyle = new BottomNavBarStyle();
        numberBottomNavBarStyle.setBottomPreviewNarBarBackgroundColor(ContextCompat.getColor(context, R.color.ps_color_half_grey));
        numberBottomNavBarStyle.setBottomPreviewNormalText(context.getString(R.string.ps_preview));
        numberBottomNavBarStyle.setBottomPreviewNormalTextColor(ContextCompat.getColor(context, R.color.ps_color_9b));
        numberBottomNavBarStyle.setBottomPreviewNormalTextSize(16);
        numberBottomNavBarStyle.setCompleteCountTips(false);
        numberBottomNavBarStyle.setBottomPreviewSelectText(context.getString(R.string.ps_preview_num));
        numberBottomNavBarStyle.setBottomPreviewSelectTextColor(ContextCompat.getColor(context, R.color.ps_color_white));
        return numberBottomNavBarStyle;
    }

    public void chooseIMG(Activity activity, int maxSelectNum, boolean isCamera, ChooseMimeType mimeType, boolean isWithSelectVideoImage, final ISelectBack iSelectBack) {
        this.chooseIMG(activity, maxSelectNum, isCamera, mimeType, isWithSelectVideoImage, true, iSelectBack);
    }

    public void chooseIMG(Activity activity, int maxSelectNum, boolean isCamera, ChooseMimeType mimeType, boolean isWithSelectVideoImage, boolean isOriginalControl, final ISelectBack iSelectBack) {
        if (isCamera) WKBaseApplication.getInstance().disconnect = false;
        PictureSelectorStyle selectorStyle = new PictureSelectorStyle();
        selectorStyle.setTitleBarStyle(getTitleBarStyle());
        selectorStyle.setBottomBarStyle(getBottomNavBarStyle(activity));
        selectorStyle.setSelectMainStyle(getMainStyle(activity));
        PictureWindowAnimationStyle animationStyle = new PictureWindowAnimationStyle();
        animationStyle.setActivityEnterAnimation(R.anim.ps_anim_up_in);
        animationStyle.setActivityExitAnimation(R.anim.ps_anim_down_out);
        selectorStyle.setWindowAnimationStyle(animationStyle);
        PictureSelector.create(activity).openGallery(mimeType == ChooseMimeType.all ? SelectMimeType.ofAll() : SelectMimeType.ofImage()).setImageEngine(GlideEngine.createGlideEngine())
                .isPageStrategy(true, 20)
                .setSelectorUIStyle(selectorStyle)
                .setOfAllCameraType(mimeType == ChooseMimeType.all ? SelectMimeType.ofAll() : SelectMimeType.ofImage())
                .setRecyclerAnimationMode(AnimationType.SLIDE_IN_BOTTOM_ANIMATION)
//                .isWithVideoImage(false)
                .isMaxSelectEnabledMask(true)
                .isPreviewVideo(true)
                .setMaxSelectNum(maxSelectNum)
                .setMaxVideoSelectNum(maxSelectNum)
                .setImageSpanCount(3)
                .isWithSelectVideoImage(isWithSelectVideoImage)
//                .setReturnEmpty(true)
//                .DisplayOriginalSize(true)
//                .setEditorImage(false)
                .isDisplayCamera(isCamera)
                .isPreviewImage(true).setCompressEngine(new ImageFileCompressEngine())
//                .isZoomAnim(true)
//                .isEnableCrop(false)
//                .isCompress(true)
                .isOriginalControl(isOriginalControl).setEditMediaInterceptListener((fragment, currentLocalMedia, requestCode) -> {
                    WKBaseApplication.getInstance().disconnect = true;
                    EndpointManager.getInstance().invoke("edit_img", new EditImgMenu(null, false, currentLocalMedia.getRealPath(), fragment, requestCode, (bitmap, path) -> {

                    }));
                })
                .isGif(true).forResult(new OnResultCallbackListener<>() {
                    @Override
                    public void onResult(ArrayList<LocalMedia> result) {
                        WKBaseApplication.getInstance().disconnect = true;
                        List<ChooseResult> list = new ArrayList<>();
                        for (LocalMedia media : result) {
                            String path;
                            ChooseResult chooseResult = new ChooseResult();
                            if (media.isCut() && !media.isCompressed()) {
                                // 裁剪过
                                path = media.getCutPath();
                            } else if (media.isCut() || media.isCompressed()) {
                                // 压缩过,或者裁剪同时压缩过,以最终压缩过图片为准
                                path = media.getCompressPath();
                            } else {
                                if (media.isToSandboxPath()) {
                                    path = media.getSandboxPath();
                                } else
                                    // 原图
                                    path = media.getRealPath();
                            }
                            // int mediaType = PictureMimeType.getMimeType(media.getMimeType());
                            if (PictureMimeType.isHasVideo(media.getMimeType())) {
                                chooseResult.model = ChooseResultModel.video;
                                chooseResult.path = media.getRealPath();
                                //  chooseResult.path = TextUtils.isEmpty(media.) ? media.getPath() : media.getAndroidQToPath();
                                if (PictureMimeType.isContent(chooseResult.path)) {
                                    chooseResult.path = getRealPathFromUri(activity, Uri.parse(chooseResult.path));
                                }
                            } else {
                                chooseResult.model = ChooseResultModel.image;
                                chooseResult.path = path;
                            }
                            WKLogUtils.e(path);
                            list.add(chooseResult);
                        }
                        iSelectBack.onBack(list);
                    }

                    @Override
                    public void onCancel() {
                        WKBaseApplication.getInstance().disconnect = true;
                        iSelectBack.onCancel();
                    }
                });
    }

    private String getRealPathFromUri(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void compressImg(Context context, String path, final ICompressListener icompressListener) {
        List<String> list = new ArrayList<>();
        list.add(path);
        compressImg(context, list, icompressListener);
    }

    private static final long SMALL_WEBP_LIMIT_BYTES = 100L * 1024L;
    private static final int MAX_COMPRESS_SIDE = 1440;
    private static final int WEBP_QUALITY = 76;

    /**
     * 压缩图片
     *
     * 规则：
     * 1. WebP < 100KB：不压缩，保留原文件。
     * 2. WebP >= 100KB：压缩，仍输出 WebP。
     * 3. 非 WebP < 100KB：不缩尺寸，只转换成 WebP。
     * 4. 非 WebP >= 100KB：缩放压缩并输出 WebP。
     * 5. GIF：保留动画，不转静态 WebP。
     */
    public void compressImg(Context context, List<String> paths, final ICompressListener iCompressListener) {
        new Thread(() -> {
            List<File> result = new ArrayList<>();
            if (paths != null) {
                for (String path : paths) {
                    String outPath = compressLocalImageToWebpIfNeeded(context, path);
                    if (!TextUtils.isEmpty(outPath)) {
                        File file = new File(normalizeFilePath(outPath));
                        if (file.exists()) {
                            result.add(file);
                        }
                    }
                }
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                if (iCompressListener != null) {
                    iCompressListener.onResult(result);
                }
            });
        }).start();
    }

    private static String compressLocalImageToWebpIfNeeded(Context context, String path) {
        if (TextUtils.isEmpty(path)) return path;
        String lower = path.toLowerCase(Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return path;
        if (lower.endsWith(".gif")) return path;
        if (lower.startsWith("content://")) {
            return compressUriImageToWebpIfNeeded(context, Uri.parse(path), "content");
        }

        String localPath = normalizeFilePath(path);
        File source = new File(localPath);
        if (!source.exists() || !source.isFile()) return path;
        if (isWebpPath(localPath) && source.length() < SMALL_WEBP_LIMIT_BYTES) return localPath;
        return decodeAndWriteWebp(localPath, source.getName());
    }

    private static String compressUriImageToWebpIfNeeded(Context context, Uri uri, String sourceName) {
        if (context == null || uri == null) return null;
        String mime = "";
        try {
            mime = context.getContentResolver().getType(uri);
        } catch (Exception ignored) {
        }
        String uriString = uri.toString();
        boolean isGif = (!TextUtils.isEmpty(mime) && mime.toLowerCase(Locale.US).contains("gif"))
                || uriString.toLowerCase(Locale.US).endsWith(".gif");
        if (isGif) return uriString;

        boolean isWebp = (!TextUtils.isEmpty(mime) && mime.toLowerCase(Locale.US).contains("webp"))
                || uriString.toLowerCase(Locale.US).endsWith(".webp");
        long size = getUriSize(context, uri);
        if (isWebp && size > 0 && size < SMALL_WEBP_LIMIT_BYTES) {
            // 返回 null 让 PictureSelector 使用原文件；content uri 原样传给 File 不可靠。
            return null;
        }

        Bitmap bitmap = null;
        FileOutputStream outputStream = null;
        File out = null;
        InputStream boundsStream = null;
        InputStream inputStream = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            boundsStream = context.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / sample > MAX_COMPRESS_SIDE || bounds.outHeight / sample > MAX_COMPRESS_SIDE) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            inputStream = context.getContentResolver().openInputStream(uri);
            bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            if (bitmap == null) return null;

            out = createTargetWebpFile(sourceName);
            outputStream = new FileOutputStream(out);
            boolean ok = bitmap.compress(getWebpCompressFormatCompat(), WEBP_QUALITY, outputStream);
            outputStream.flush();
            return ok && out.exists() && out.length() > 0 ? out.getAbsolutePath() : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                if (boundsStream != null) boundsStream.close();
            } catch (Exception ignored) {
            }
            try {
                if (inputStream != null) inputStream.close();
            } catch (Exception ignored) {
            }
            try {
                if (outputStream != null) outputStream.close();
            } catch (Exception ignored) {
            }
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (out != null && out.exists() && out.length() <= 0) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }
    }

    private static String decodeAndWriteWebp(String localPath, String sourceName) {
        Bitmap bitmap = null;
        FileOutputStream outputStream = null;
        File out = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(localPath, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return localPath;

            int sample = 1;
            while (bounds.outWidth / sample > MAX_COMPRESS_SIDE || bounds.outHeight / sample > MAX_COMPRESS_SIDE) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            bitmap = BitmapFactory.decodeFile(localPath, options);
            if (bitmap == null) return localPath;

            out = createTargetWebpFile(sourceName);
            outputStream = new FileOutputStream(out);
            boolean ok = bitmap.compress(getWebpCompressFormatCompat(), WEBP_QUALITY, outputStream);
            outputStream.flush();
            return ok && out.exists() && out.length() > 0 ? out.getAbsolutePath() : localPath;
        } catch (Exception ignored) {
            return localPath;
        } finally {
            try {
                if (outputStream != null) outputStream.close();
            } catch (Exception ignored) {
            }
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (out != null && out.exists() && out.length() <= 0) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }
    }

    private static File createTargetWebpFile(String sourceName) {
        File dir = new File(WKConstants.imageDir);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        String safe = TextUtils.isEmpty(sourceName) ? "img" : sourceName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return new File(dir, DateUtils.getCreateFileName("CMP_") + "_" + Math.abs(safe.hashCode()) + ".webp");
    }

    private static String normalizeFilePath(String path) {
        if (TextUtils.isEmpty(path)) return path;
        if (path.startsWith("file://")) {
            try {
                String real = Uri.parse(path).getPath();
                return TextUtils.isEmpty(real) ? path.substring("file://".length()) : real;
            } catch (Exception ignored) {
                return path.substring("file://".length());
            }
        }
        return path;
    }

    private static boolean isWebpPath(String path) {
        return !TextUtils.isEmpty(path) && path.toLowerCase(Locale.US).endsWith(".webp");
    }

    private static long getUriSize(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0) return cursor.getLong(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1;
    }

    private static Bitmap.CompressFormat getWebpCompressFormatCompat() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                return Bitmap.CompressFormat.valueOf("WEBP_LOSSY");
            } catch (Exception ignored) {
            }
        }
        //noinspection deprecation
        return Bitmap.CompressFormat.WEBP;
    }

    public interface ICompressListener {
        void onResult(List<File> files);
    }

    private static class ImageFileCompressEngine implements CompressFileEngine {

        @Override
        public void onStartCompress(Context context, ArrayList<Uri> source, OnKeyValueResultCallbackListener call) {
            new Thread(() -> {
                if (source == null) return;
                for (Uri uri : source) {
                    String src = uri == null ? "" : uri.toString();
                    String compressed;
                    if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
                        compressed = compressLocalImageToWebpIfNeeded(context, uri.getPath());
                        if (TextUtils.equals(compressed, uri.getPath())) {
                            compressed = null;
                        }
                    } else {
                        compressed = compressUriImageToWebpIfNeeded(context, uri, src);
                    }
                    if (call != null) {
                        call.onCallback(src, compressed);
                    }
                }
            }).start();
        }
    }

}

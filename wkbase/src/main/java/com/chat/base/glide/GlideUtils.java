package com.chat.base.glide;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
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

    /**
     * 统一聊天图片压缩规则：
     * 1) 小于 100KB 的 WebP 原样保留，避免越压越大、越压越糊。
     * 2) 小于 100KB 的非 WebP 只做格式转换，输出 WebP，不缩尺寸。
     * 3) 大于等于 100KB 的图片统一压缩并输出 WebP。
     * 4) GIF 保留原图，避免动图被 BitmapFactory 解成静态首帧。
     */
    private static final long SMALL_IMAGE_BYTES = 100L * 1024L;
    private static final int MAX_COMPRESS_SIDE = 1440;
    private static final int SMALL_CONVERT_QUALITY = 88;
    private static final int NORMAL_COMPRESS_QUALITY = 76;

    /**
     * 压缩图片
     *
     * @param context context
     * @param paths   图片本地地址
     */
    public void compressImg(Context context, List<String> paths, final ICompressListener iCompressListener) {
        if (iCompressListener == null) return;
        if (paths == null || paths.isEmpty()) {
            iCompressListener.onResult(new ArrayList<>());
            return;
        }
        new Thread(() -> {
            List<File> result = new ArrayList<>();
            for (String path : paths) {
                File file = compressSourceToWebp(context, path);
                if (file != null) {
                    result.add(file);
                }
            }
            new Handler(Looper.getMainLooper()).post(() -> iCompressListener.onResult(result));
        }).start();
    }

    public interface ICompressListener {
        void onResult(List<File> files);
    }

    private static class ImageFileCompressEngine implements CompressFileEngine {

        @Override
        public void onStartCompress(Context context, ArrayList<Uri> source, OnKeyValueResultCallbackListener call) {
            if (source == null || source.isEmpty()) return;
            new Thread(() -> {
                for (Uri uri : source) {
                    String sourceKey = uri == null ? "" : uri.toString();
                    String callbackPath = null;
                    File compressed = compressSourceToWebp(context, sourceKey);
                    if (compressed != null) {
                        callbackPath = compressed.getAbsolutePath();
                    }
                    if (call != null) {
                        call.onCallback(sourceKey, callbackPath);
                    }
                }
            }).start();
        }
    }

    private static File compressSourceToWebp(Context context, String source) {
        if (context == null || TextUtils.isEmpty(source)) return null;
        try {
            if (isGifSource(context, source)) {
                return getOriginalFileOrCopy(context, source, "gif");
            }

            long sourceSize = getSourceLength(context, source);
            File localFile = getExistingLocalFile(source);
            if (isWebpSource(context, source) && sourceSize > 0 && sourceSize < SMALL_IMAGE_BYTES) {
                return getOriginalFileOrCopy(context, source, "webp");
            }

            boolean smallConvertOnly = sourceSize > 0 && sourceSize < SMALL_IMAGE_BYTES;
            Bitmap bitmap = decodeBitmap(context, source, smallConvertOnly ? Integer.MAX_VALUE : MAX_COMPRESS_SIDE);
            if (bitmap == null) {
                return localFile;
            }

            File dir = new File(WKConstants.imageDir);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            File out = new File(dir, DateUtils.getCreateFileName("CMP_") + "_" + System.nanoTime() + ".webp");
            FileOutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(out);
                int quality = smallConvertOnly ? SMALL_CONVERT_QUALITY : NORMAL_COMPRESS_QUALITY;
                boolean ok = bitmap.compress(getWebpCompressFormat(), quality, outputStream);
                outputStream.flush();
                return ok && out.exists() && out.length() > 0 ? out : localFile;
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (Exception ignored) {
                    }
                }
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        } catch (Exception e) {
            WKLogUtils.e("compress image to webp failed: " + e.getMessage());
            return getExistingLocalFile(source);
        }
    }

    private static Bitmap decodeBitmap(Context context, String source, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            decodeSource(context, source, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            if (maxSide != Integer.MAX_VALUE) {
                while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                    sample *= 2;
                }
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return decodeSource(context, source, options);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap decodeSource(Context context, String source, BitmapFactory.Options options) throws Exception {
        if (isContentUri(source)) {
            InputStream inputStream = null;
            try {
                inputStream = context.getContentResolver().openInputStream(Uri.parse(source));
                return BitmapFactory.decodeStream(inputStream, null, options);
            } finally {
                if (inputStream != null) inputStream.close();
            }
        }
        return BitmapFactory.decodeFile(stripFileScheme(source), options);
    }

    private static Bitmap.CompressFormat getWebpCompressFormat() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                return Bitmap.CompressFormat.valueOf("WEBP_LOSSY");
            } catch (Exception ignored) {
            }
        }
        //noinspection deprecation
        return Bitmap.CompressFormat.WEBP;
    }

    private static File getOriginalFileOrCopy(Context context, String source, String extension) {
        File local = getExistingLocalFile(source);
        if (local != null) return local;
        if (!isContentUri(source)) return null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            File dir = new File(WKConstants.imageDir);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            File out = new File(dir, DateUtils.getCreateFileName("SRC_") + "_" + System.nanoTime() + "." + extension);
            inputStream = context.getContentResolver().openInputStream(Uri.parse(source));
            if (inputStream == null) return null;
            outputStream = new FileOutputStream(out);
            byte[] buffer = new byte[16 * 1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
            return out.exists() && out.length() > 0 ? out : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static long getSourceLength(Context context, String source) {
        try {
            File file = getExistingLocalFile(source);
            if (file != null) return file.length();
            if (isContentUri(source)) {
                AssetFileDescriptor afd = null;
                try {
                    afd = context.getContentResolver().openAssetFileDescriptor(Uri.parse(source), "r");
                    return afd == null ? 0 : afd.getLength();
                } finally {
                    if (afd != null) afd.close();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static File getExistingLocalFile(String source) {
        try {
            if (TextUtils.isEmpty(source) || isContentUri(source)) return null;
            File file = new File(stripFileScheme(source));
            return file.exists() ? file : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isContentUri(String source) {
        return !TextUtils.isEmpty(source) && source.toLowerCase().startsWith("content://");
    }

    private static String stripFileScheme(String source) {
        if (!TextUtils.isEmpty(source) && source.toLowerCase().startsWith("file://")) {
            return Uri.parse(source).getPath();
        }
        return source;
    }

    private static boolean isWebpSource(Context context, String source) {
        String lower = source == null ? "" : source.toLowerCase();
        if (lower.endsWith(".webp")) return true;
        if (isContentUri(source)) {
            try {
                String type = context.getContentResolver().getType(Uri.parse(source));
                return type != null && type.toLowerCase().contains("webp");
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static boolean isGifSource(Context context, String source) {
        String lower = source == null ? "" : source.toLowerCase();
        if (lower.endsWith(".gif")) return true;
        if (isContentUri(source)) {
            try {
                String type = context.getContentResolver().getType(Uri.parse(source));
                return type != null && type.toLowerCase().contains("gif");
            } catch (Exception ignored) {
            }
        }
        return false;
    }

}

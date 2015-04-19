package com.tools.imagecache;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.support.v4.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.tools.utils.FileUtil;
import com.tools.utils.LogManager;
import com.tools.utils.TargetVersionUtils;
import com.tools.utils.TranscoderUtil;

/**
 * 位图缓存处理类(内存和磁盘)。
 * @author Ivan
 * @version 1.0
 */
public class ImageCache {
    private static final String TAG = "ImageCache";

    /** 默认内存缓存大小(5MB) */
    private static final int DEFAULT_MEM_CACHE_SIZE = 1024 * 1024 * 5;

    /**默认的磁盘缓存大小(10MB) */
    private static final int DEFAULT_DISK_CACHE_SIZE = 1024 * 1024 * 10;

    /* 图像压缩设置(磁盘缓存) */
    private static final CompressFormat DEFAULT_COMPRESS_FORMAT = CompressFormat.PNG;
    private static final int DEFAULT_COMPRESS_QUALITY = 70;
    private static final int DISK_CACHE_INDEX = 0;

    /* 流程控制参数 */
    private static final boolean DEFAULT_MEM_CACHE_ENABLED = true;
    private static final boolean DEFAULT_DISK_CACHE_ENABLED = true;
    private static final boolean DEFAULT_CLEAR_DISK_CACHE_ON_START = false;
    private static final boolean DEFAULT_INIT_DISK_CACHE_ON_CREATE = false;

    private DiskLruCache diskLruCache;
    private LruCache<String, Bitmap> memoryCache;
    private ImageCacheParams cacheParams;
    private final Object diskCacheLock = new Object();
    private boolean diskCacheStarting = true;

    /**
     * 使用指定的参数创建一个新的ImageCache对象。
     * @param cacheParams 初始化缓存参数
     */
    public ImageCache(ImageCacheParams cacheParams) {
        init(cacheParams);
    }

    /**
     * 使用指定的参数创建一个新的ImageCache对象。
     * @param context 应用上下文
     * @param uniqueName 缓存目录名称
     */
    public ImageCache(Context context, String uniqueName) {
        init(new ImageCacheParams(context, uniqueName));
    }

    /**
     * 初始化缓存
     * @param cacheParams 初始化缓存参数
     */
    private void init(ImageCacheParams cacheParams) {
        this.cacheParams = cacheParams;

        // 设置内存缓存
        if (cacheParams.memoryCacheEnabled) {
            LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG
            		, "Memory cache created (size = " +  this.cacheParams.memCacheSize + ")");
            memoryCache = new LruCache<String, Bitmap>(this.cacheParams.memCacheSize) {
                
            	/** 使用字节数衡量图片大小 */
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return getBitmapSize(bitmap);
                }
            };
        }

        //默认情况下,这里不初始化磁盘高速缓存,因为应该在一个单独的线程中做初始化。
        if (cacheParams.initDiskCacheOnCreate) {
            // 设置磁盘高速缓存
            initDiskCache();
        }
    }

    /**
     * 初始化磁盘缓存。
     * 注意：磁盘访问不应该在（主要/UI）线程上执行。
     * 默认情况下ImageCache不初始化磁盘缓存。
     */
    public void initDiskCache() {
        // 设置磁盘高速缓存
        synchronized (diskCacheLock) {
            if (diskLruCache == null || diskLruCache.isClosed()) {
                File diskCacheDir = cacheParams.diskCacheDir;
                if (cacheParams.diskCacheEnabled && diskCacheDir != null) {
                    if (!diskCacheDir.exists()) {
                        diskCacheDir.mkdirs();
                    }
                    if (diskCacheDir.getUsableSpace() > cacheParams.diskCacheSize) {
                        try {
                            diskLruCache = DiskLruCache.open(
                                    diskCacheDir, 1, 1, cacheParams.diskCacheSize);
                            LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "Disk cache initialized");
                        } catch (final IOException e) {
                            cacheParams.diskCacheDir = null;
                            LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG, "initDiskCache:", e);
                        }
                    }
                }
            }
            diskCacheStarting = false;
            diskCacheLock.notifyAll();
        }
    }

    /**
     * 添加一个位图内存和磁盘缓存。
     * @param data 位图存储惟一标识符
     * @param bitmap 位图存储
     */
    public void addBitmapToCache(String data, Bitmap bitmap) {
        if (data == null || bitmap == null) {
            return;
        }

        // 添加到内存缓存
        if (memoryCache != null && memoryCache.get(data) == null) {
            memoryCache.put(data, bitmap);
        }

        synchronized (diskCacheLock) {
            // 添加到磁盘缓存
            if (diskLruCache != null) {
                final String key = hashKeyForDisk(data);
                OutputStream out = null;
                try {
                    DiskLruCache.Snapshot snapshot = diskLruCache.get(key);
                    if (snapshot == null) {
                        final DiskLruCache.Editor editor = diskLruCache.edit(key);
                        if (editor != null) {
                            out = editor.newOutputStream(DISK_CACHE_INDEX);
                            bitmap.compress(
                                    cacheParams.compressFormat, cacheParams.compressQuality, out);
                            editor.commit();
                            out.close();
                        }
                    } else {
                        snapshot.getInputStream(DISK_CACHE_INDEX).close();
                    }
                } catch (IOException e) {
                	LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG, "addBitmapToCache:", e);
                } catch (Exception e) {
                	LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG, "addBitmapToCache:", e);
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (IOException e) {
                    	LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG, "Close outputStream:", e);
                    }
                }
            }
        }
    }

    /**
     * 从内存缓存获取指定数据。
     *
     * @param 数据项的唯一标识符
     * @return 缓存中的位图或者null
     */
    public Bitmap getBitmapFromMemCache(String data) {
        if (memoryCache != null) {
            final Bitmap memBitmap = memoryCache.get(data);
            if (memBitmap != null) {
                LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "Memory cache hit");
                return memBitmap;
            }
        }
        return null;
    }

    /**
     * 从磁盘缓存获取指定数据
     * @param 数据项的唯一标识符
     * @return 缓存中的位图或者null
     */
    public Bitmap getBitmapFromDiskCache(String data) {
        final String key = hashKeyForDisk(data);
        synchronized (diskCacheLock) {
            while (diskCacheStarting) {
                try {
                    diskCacheLock.wait();
                } catch (InterruptedException e) {}
            }
            if (diskLruCache != null) {
                InputStream inputStream = null;
                try {
                    final DiskLruCache.Snapshot snapshot = diskLruCache.get(key);
                    if (snapshot != null) {
                        LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "Disk cache hit");
                        inputStream = snapshot.getInputStream(DISK_CACHE_INDEX);
                        if (inputStream != null) {
                            final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            return bitmap;
                        }
                    }
                } catch (final IOException e) {
                    LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG, "getBitmapFromDiskCache:", e);
                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    } catch (IOException e) {}
                }
            }
            return null;
        }
    }

    /**
     * 清除ImageCache对象关联的内存和磁盘高速缓存。
     * 注意：磁盘访问不应该在（主要/UI）线程上执行。
     */
    public void clearCache() {
        if (memoryCache != null) {
            memoryCache.evictAll();
            LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "Memory cache cleared");
        }

        synchronized (diskCacheLock) {
            diskCacheStarting = true;
            if (diskLruCache != null && !diskLruCache.isClosed()) {
                try {
                    diskLruCache.delete();
                    LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "Disk cache cleared");
                } catch (IOException e) {
                    LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG, "clearCache:", e);
                }
                diskLruCache = null;
                initDiskCache();
            }
        }
    }

    /**
     * 刷新磁盘高速缓存与ImageCache对象的关联。
     * 注意：磁盘访问不应该在（主要/UI）线程上执行。
     */
    public void flush() {
        synchronized (diskCacheLock) {
            if (diskLruCache != null) {
                try {
                    diskLruCache.flush();
                    LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "Disk cache flushed");
                } catch (IOException e) {
                    LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG, "flush:", e);
                }
            }
        }
    }

    /**
     * 关闭磁盘高速缓存与ImageCache的关联。
     * 注意：磁盘访问不应该在（主要/UI）线程上执行。
     */
    public void close() {
        synchronized (diskCacheLock) {
            if (diskLruCache != null) {
                try {
                    if (!diskLruCache.isClosed()) {
                        diskLruCache.close();
                        diskLruCache = null;
                        LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "Disk cache closed");
                    }
                } catch (IOException e) {
                    LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG, "close:", e);
                }
            }
        }
    }

    /**
     * 散列方法,改变一个字符串(如URL)到一个散列适合使用的磁盘文件名。
     * @param key 需要做转换的字符串
     * @return cacheKey 转换后的字符串
     */
    public static String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = TranscoderUtil.bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    /**
     * 获取位图的大小（字节）
     * @param bitmap
     * @return 位图的大小（字节）
     */
    @TargetApi(12)
    public static int getBitmapSize(Bitmap bitmap) {
        if (TargetVersionUtils.hasHoneycombMR1()) {
            return bitmap.getByteCount();
        }

        return bitmap.getRowBytes() * bitmap.getHeight();
    }

    /**
     * 缓存参数类
     * @author Ivan
     * @version 1.0
     */
    public static class ImageCacheParams {
        public int memCacheSize = DEFAULT_MEM_CACHE_SIZE;
        public int diskCacheSize = DEFAULT_DISK_CACHE_SIZE;
        public File diskCacheDir;
        public CompressFormat compressFormat = DEFAULT_COMPRESS_FORMAT;
        public int compressQuality = DEFAULT_COMPRESS_QUALITY;
        public boolean memoryCacheEnabled = DEFAULT_MEM_CACHE_ENABLED;
        public boolean diskCacheEnabled = DEFAULT_DISK_CACHE_ENABLED;
        public boolean clearDiskCacheOnStart = DEFAULT_CLEAR_DISK_CACHE_ON_START;
        public boolean initDiskCacheOnCreate = DEFAULT_INIT_DISK_CACHE_ON_CREATE;

        public ImageCacheParams(Context context, String uniqueName) {
            diskCacheDir = FileUtil.getCacheDir(context, uniqueName);
            //LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, diskCacheDir.getPath());
        }

        public ImageCacheParams(File diskCacheDir) {
            this.diskCacheDir = diskCacheDir;
        }

        /**
         * 根据设备内存的百分比设置内存缓存大小。
         * @param context 应用上下文
         * @param percent 内存类使用内存缓存大小（百分比）
         * @throws IllegalArgumentException percent < 0.05f || percent > 0.8f
         */
        public void setMemCacheSizePercent(Context context, float percent) {
            if (percent < 0.05f || percent > 0.8f) {
                throw new IllegalArgumentException("setMemCacheSizePercent - percent must be "
                        + "between 0.05 and 0.8 (inclusive)");
            }
            
            //获取内存记忆类中的程序最大内存可用值
            int memoryTotal = ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
            memCacheSize = Math.round(percent * memoryTotal * 1024 * 1024);
        }
    }
    
}

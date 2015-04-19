package com.tools.imagecache;

import android.content.Context;
import android.graphics.Bitmap;

import com.tools.imagecache.ImageCache.ImageCacheParams;
import com.tools.utils.FileUtil;
import com.tools.utils.LogManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 根据图片的URL获取图片并缓存。 缓存同时会根据设置调整图片的大小。 支持内存和硬盘的二级缓存。
 * 
 * @author Ivan
 */
public class ImageFetcher extends ImageResizer {
	private static final String TAG = "ImageFetcher";

	private static ImageFetcher imageFetcher;

	private static ImageFetcher imageFetcher1;

	private static final int HTTP_CACHE_SIZE = 10 * 1024 * 1024; // 10MB
	private static final String HTTP_CACHE_DIR = "http";
	private static final int IO_BUFFER_SIZE = 8 * 1024;

	private File httpCacheDir;
	private DiskLruCache httpDiskCache;

	private boolean httpDiskCacheStarting = true;
	private final Object httpDiskCacheLock = new Object();
	private static final int DISK_CACHE_INDEX = 0;

	public ImageFetcher(Context context, int imageWidth, int imageHeight) {
		super(context, imageWidth, imageHeight);
		init(context);
	}

	public ImageFetcher(Context context, int imageSize) {
		super(context, imageSize);
		init(context);
	}

	/**
	 * 设置imageThumSize，高宽均按次比例压缩
	 * 
	 * @param context
	 *            上下文
	 * @param imageThumSize
	 *            图片的大小 dp
	 * @param imageCacheDir
	 *            图片环迅的大小
	 * @param memCacheSize
	 *            图片缓存控件的大小
	 * @return
	 */
	public static synchronized ImageFetcher getInstance(Context context,
			int imageThumSize, String imageCacheDir, float memCacheSize) {
		if (null == imageFetcher) {
			synchronized (ImageFetcher.class) {
				if (null == imageFetcher) {
					LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG,
							"getInstance");
					final ImageCacheParams cacheParams = new ImageCacheParams(
							context, imageCacheDir);
					// 设置缓存大小为程序可用内存的比例 如0.25f就是25%
					cacheParams.setMemCacheSizePercent(context, memCacheSize);
					// ImageFetcher负责异步加载图片到ImageView
					imageFetcher = new ImageFetcher(context, imageThumSize, 100);
					imageFetcher.addImageCache(new ImageCache(cacheParams));
				}
			}
		}

		return imageFetcher;
	}

	/**
	 * 设置width和height，高宽均按次比例压缩
	 * 
	 * @param context
	 *            上下文
	 * @param imageWidth
	 *            处理后的宽度
	 * @param imageHeight
	 *            处理后的高度
	 * @param imageCacheDir
	 *            文件夹名称
	 * @param memCacheSize
	 *            缓存区的大小
	 * @return
	 */
	public static synchronized ImageFetcher getInstance(Context context,
			int imageWidth, int imageHeight, String imageCacheDir,
			float memCacheSize) {
		if (null == imageFetcher1) {
			synchronized (ImageFetcher.class) {
				if (null == imageFetcher1) {
					LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG,
							"getInstance1");
					final ImageCacheParams cacheParams = new ImageCacheParams(
							context, imageCacheDir);
					// 设置缓存大小为程序可用内存的比例 如0.25f就是25%
					cacheParams.setMemCacheSizePercent(context, memCacheSize);
					// ImageFetcher负责异步加载图片到ImageView
					imageFetcher1 = new ImageFetcher(context, imageWidth,
							imageHeight);
					imageFetcher1.addImageCache(new ImageCache(cacheParams));
				}
			}
		}

		return imageFetcher1;
	}

	private void init(Context context) {
		/*
		 * if(!NetworkUtil.isConnection(context)){ Toast.makeText(context,
		 * R.string.frameToast_noNetworkConnection, Toast.LENGTH_SHORT).show();
		 * }
		 */

		httpCacheDir = FileUtil.getCacheDir(context, HTTP_CACHE_DIR);
	}

	@Override
	protected void initDiskCacheInternal() {
		super.initDiskCacheInternal();
		initHttpDiskCache();
	}

	private void initHttpDiskCache() {
		if (!httpCacheDir.exists()) {
			httpCacheDir.mkdirs();
		}
		synchronized (httpDiskCacheLock) {
			if (httpCacheDir.getUsableSpace() > HTTP_CACHE_SIZE) {
				try {
					httpDiskCache = DiskLruCache.open(httpCacheDir, 1, 1,
							HTTP_CACHE_SIZE);
					LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG,
							"HTTP cache initialized");
				} catch (IOException e) {
					httpDiskCache = null;
				}
			}
			httpDiskCacheStarting = false;
			httpDiskCacheLock.notifyAll();
		}
	}

	@Override
	protected void clearCacheInternal() {
		super.clearCacheInternal();
		synchronized (httpDiskCacheLock) {
			if (httpDiskCache != null && !httpDiskCache.isClosed()) {
				try {
					httpDiskCache.delete();
					LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG,
							"HTTP cache cleared");
				} catch (IOException e) {
					LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG,
							"clearCacheInternal:", e);
				}
				httpDiskCache = null;
				httpDiskCacheStarting = true;
				initHttpDiskCache();
			}
		}
	}

	@Override
	protected void flushCacheInternal() {
		super.flushCacheInternal();
		synchronized (httpDiskCacheLock) {
			if (httpDiskCache != null) {
				try {
					httpDiskCache.flush();
					LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG,
							"HTTP cache flushed");
				} catch (IOException e) {
					LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG,
							"flush:", e);
				}
			}
		}
	}

	@Override
	protected void closeCacheInternal() {
		super.closeCacheInternal();
		synchronized (httpDiskCacheLock) {
			if (httpDiskCache != null) {
				try {
					if (!httpDiskCache.isClosed()) {
						httpDiskCache.close();
						httpDiskCache = null;
						LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG,
								"HTTP cache closed");
					}
				} catch (IOException e) {
					LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG,
							"closeCacheInternal:", e);
				}
			}
		}
	}

	/**
	 * The main process method, which will be called by the ImageWorker in the
	 * AsyncTask background thread.
	 * 
	 * @param data
	 *            The data to load the bitmap, in this case, a regular http URL
	 * @return The downloaded and resized bitmap
	 */
	@Override
	protected Bitmap processBitmap(String url) {
		LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "processBitmap:"
				+ url);

		final String key = ImageCache.hashKeyForDisk(url);
		FileDescriptor fileDescriptor = null;
		FileInputStream fileInputStream = null;
		DiskLruCache.Snapshot snapshot;
		synchronized (httpDiskCacheLock) {
			// Wait for disk cache to initialize
			while (httpDiskCacheStarting) {
				try {
					httpDiskCacheLock.wait();
				} catch (InterruptedException e) {
					LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG,
							"processBitmap", e);
				}
			}

			if (httpDiskCache != null) {
				try {
					snapshot = httpDiskCache.get(key);
					if (snapshot == null) {
						LogManager
								.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG,
										"processBitmap, not found in http cache, downloading...");
						DiskLruCache.Editor editor = httpDiskCache.edit(key);
						if (editor != null) {
							if (downloadUrlToStream(url,
									editor.newOutputStream(DISK_CACHE_INDEX))) {
								editor.commit();
							} else {
								editor.abort();
							}
						}
						snapshot = httpDiskCache.get(key);
					}
					if (snapshot != null) {
						fileInputStream = (FileInputStream) snapshot
								.getInputStream(DISK_CACHE_INDEX);
						fileDescriptor = fileInputStream.getFD();
					}
				} catch (IOException e) {
					LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG,
							"processBitmap:", e);
				} catch (IllegalStateException e) {
					LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG,
							"processBitmap:", e);
				} finally {
					if (fileDescriptor == null && fileInputStream != null) {
						try {
							fileInputStream.close();
						} catch (IOException e) {
							LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR,
									TAG, "processBitmap", e);
						}
					}
				}
			}
		}

		Bitmap bitmap = null;
		if (fileDescriptor != null) {
			bitmap = decodeSampledBitmapFromDescriptor(fileDescriptor,
					imageWidth, imageHeight);
		}
		if (fileInputStream != null) {
			try {
				fileInputStream.close();
			} catch (IOException e) {
				LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG,
						"processBitmap", e);
			}
		}
		return bitmap;
	}

	/**
	 * 根据指定的URL下载图片的位图数据，并通过指定的输出流写出
	 * 
	 * @param urlString
	 * @param outputStream
	 * @return true 成功， false 出现异常
	 */
	public boolean downloadUrlToStream(String urlString,
			OutputStream outputStream) {
		HttpURLConnection urlConnection = null;
		BufferedOutputStream out = null;
		BufferedInputStream in = null;

		try {
			final URL url = new URL(urlString);
			urlConnection = (HttpURLConnection) url.openConnection();
			in = new BufferedInputStream(urlConnection.getInputStream(),
					IO_BUFFER_SIZE);
			out = new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);

			int b;
			while ((b = in.read()) != -1) {
				out.write(b);
			}
			out.flush();
			return true;
		} catch (IOException e) {
			LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG,
					"Error in downloadBitmap:", e);
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
			try {
				if (out != null) {
					out.close();
				}
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG,
						"Error in stream close:", e);
			}
		}
		return false;
	}
}

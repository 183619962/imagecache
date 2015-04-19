package com.tools.imagecache;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.tools.thread.ImageCacheThreadPool;
import com.tools.utils.LogManager;


public abstract class ImageWorker {
	private static final String TAG="ImageWorker";

    protected Resources resources;
    
    private ImageCache imageCache;

    private final Object mPauseWorkLock = new Object();
    
    protected boolean mPauseWork = false;
    
    protected static final List<String> LOADING_URLLIST = new ArrayList<String>();
    
    private static final int MESSAGE_CLEAR = 0;
    private static final int MESSAGE_INIT_DISK_CACHE = 1;
    private static final int MESSAGE_FLUSH = 2;
    private static final int MESSAGE_CLOSE = 3;

    protected ImageWorker(Context context) {
    	resources = context.getResources();
    }
    
    public void setImageCache(ImageCache imageCache) {
    	this.imageCache = imageCache;
    }
    
    protected abstract Bitmap processBitmap(String url);
    
    public synchronized void loadImage(String url, ImageView imageView) {
        if (TextUtils.isEmpty(url)) {
        	LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "url is null");
            return;
        }

        Bitmap bitmap = null;
        if (imageCache != null) {
            bitmap = imageCache.getBitmapFromMemCache(url);
        }

        if (bitmap != null) {
            // 图片在缓存中存在则直接使用
        	LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "bitmap is not null");
            imageView.setImageBitmap(bitmap);
        }else{
        	synchronized(this){
            	if (!LOADING_URLLIST.contains(url)) {
            		LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, "bitmap is loading");
                	LOADING_URLLIST.add(url);
                    ImageCacheThreadPool.execute(new BitmapWorkerTask(imageView, url));
                }
            }
        }
        
    }
    
    /**
     * 特殊方法，仅用于FirstPageAdapter。
     * APP启动时，第一与第二个页签均会加载已安装应用，且第二个页面加载早于第一个页面（viewPager机制）
     * ，此处就出现了不同VIEW近乎同时下载同一张图片的情况，此方法放开了同一张图片不能被多次下载的限制。
     * @param url
     * @param imageView
     */
    public synchronized void loadImageNoSync(String url, ImageView imageView) {
        if (TextUtils.isEmpty(url)) {
            return;
        }

        Bitmap bitmap = null;
        if (imageCache != null) {
            bitmap = imageCache.getBitmapFromMemCache(url);
        }

        if (bitmap != null) {
            // 图片在缓存中存在则直接使用
            imageView.setImageBitmap(bitmap);
        }else{
            ImageCacheThreadPool.execute(new BitmapWorkerTask(imageView, url));
        }
    }

    public void setPauseWork(boolean pauseWork) {
        synchronized (mPauseWorkLock) {
            mPauseWork = pauseWork;
            if (!mPauseWork) {
                mPauseWorkLock.notifyAll();
            }
        }
    }
    
    protected void initDiskCacheInternal() {
        if (imageCache != null) {
        	imageCache.initDiskCache();
        }
    }

    protected void clearCacheInternal() {
        if (imageCache != null) {
        	imageCache.clearCache();
        }
    }

    protected void flushCacheInternal() {
        if (imageCache != null) {
        	imageCache.flush();
        }
    }

    protected void closeCacheInternal() {
        if (imageCache != null) {
        	imageCache.close();
        	imageCache = null;
        }
    }

    public void addImageCache(ImageCache imageCache) {
        setImageCache(imageCache);
        ImageCacheThreadPool.execute(new CacheAsyncTask(MESSAGE_INIT_DISK_CACHE));
    }
    
    public void clearCache() {
    	ImageCacheThreadPool.execute(new CacheAsyncTask(MESSAGE_CLEAR));
    }

    public void flushCache() {
    	ImageCacheThreadPool.execute(new CacheAsyncTask(MESSAGE_FLUSH));
    }

    public void closeCache() {
    	ImageCacheThreadPool.execute(new CacheAsyncTask(MESSAGE_CLOSE));
    }
    
    private class BitmapWorkerTask implements Runnable {
        private final String url;
        private final WeakReference<ImageView> imageViewReference;
        
        public BitmapWorkerTask(ImageView imageView,String url) {
        	this.url = url;
            this.imageViewReference = new WeakReference<ImageView>(imageView);
        }
        
        @Override
		public void run() {
			synchronized (mPauseWorkLock) {
				while (mPauseWork) {
					try {
						mPauseWorkLock.wait();
					} catch (InterruptedException e) {}
				}
			}
			
			Bitmap bitmap = null;
			if (imageCache != null) {
				bitmap = imageCache.getBitmapFromDiskCache(url);
			}

			if (bitmap == null) {
				bitmap = processBitmap(url);
			} 
			
			if (bitmap != null) {
				if(imageCache != null){
					imageCache.addBitmapToCache(url, bitmap);
				}

				ImageView imageView = imageViewReference.get();
				if(null != imageView){
					final MyHandler myHandler = new MyHandler(imageView, bitmap);
					final Message msg = Message.obtain();
					myHandler.sendMessage(msg);
				}
			}

			LOADING_URLLIST.remove(url);
		}
        
    }
    
    private static class MyHandler extends Handler {
    	private final ImageView imageView;
    	private final Bitmap bitmap;
		public MyHandler(ImageView imageView, Bitmap bitmap) {
			super(Looper.getMainLooper());
			this.imageView = imageView;
			this.bitmap = bitmap;
		}
		
		@Override
		public void handleMessage(Message msg) {
			imageView.setImageBitmap(bitmap);
		}
	}
    
    private class CacheAsyncTask implements Runnable{

    	private int mOperationType;
    	
        public CacheAsyncTask(int operationType){
        	this.mOperationType = operationType;
        }
        
		@Override
		public void run() {
			switch (mOperationType) {
	            case MESSAGE_CLEAR:
	                clearCacheInternal();
	                break;
	            case MESSAGE_INIT_DISK_CACHE:
	                initDiskCacheInternal();
	                break;
	            case MESSAGE_FLUSH:
	                flushCacheInternal();
	                break;
	            case MESSAGE_CLOSE:
	                closeCacheInternal();
	                break;
	        }
		}
    }

}

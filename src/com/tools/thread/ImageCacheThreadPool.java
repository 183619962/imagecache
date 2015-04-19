package com.tools.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * 线程池
 * @author Ivan
 * @version 1.0.0
 */
public class ImageCacheThreadPool {
	private static final ExecutorService pool;
	
	/** 应用图片加载核心线程数 */
	public static final int THREAD_COREPOOLSIZE_FORIMAGECACHE = 5;
	
	/** 应用图片加载最大核心线程数 */
	public static final int THREAD_MAXIMUMPOOLSIZE_FORIMAGECACHE = 15;
	
	/** 应用图片加载线程池中空闲线程的销毁周期 */
	public static final long THREAD_KEEPALIVETIME_FORIMAGECACHE = 10000L;
	
	/** 应用图片加载线程池队列大小 */
	public static final int THREAD_QUEUE_FORIMAGECACHE = 30;
	
	static {
		pool = new ThreadPoolExecutor(THREAD_COREPOOLSIZE_FORIMAGECACHE
				, THREAD_MAXIMUMPOOLSIZE_FORIMAGECACHE
				, THREAD_KEEPALIVETIME_FORIMAGECACHE
				, TimeUnit.MILLISECONDS
				,  new LinkedBlockingQueue<Runnable>(THREAD_QUEUE_FORIMAGECACHE));
	}
	
	/**
	 * @param command
	 */
	public static synchronized void execute(Runnable command) {
			pool.execute(command);
	}
}

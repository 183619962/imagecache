package com.tools.utils;

import android.util.Log;

public class LogManager {
	public static final int DEBUG_LEVEL_INFO = 0;
	public static final int DEBUG_LEVEL_WARN = 1;
	public static final int DEBUG_LEVEL_ERROR = 2;
	public static final int DEBUG_OFF = 3;
	
	private static final int SYSTEM_DEBUG_LEVEL = DEBUG_LEVEL_INFO;
	
	/**
	 * 日志输出
	 * @param debugLevel
	 * @param tag
	 * @param conent
	 */
	public static void writeLog(int debugLevel, String tag, String conent)
	{
		writeLogByAndroidAPI(debugLevel, tag, conent, null);
	}
	
	/**
	 * 日志输出
	 * @param debugLevel
	 * @param tag
	 * @param throwable
	 */
	public static void writeLog(int debugLevel, String tag, Throwable throwable)
	{
		writeLogByAndroidAPI(debugLevel, tag, "", throwable);
	}
	
	/**
	 * 日志输出
	 * @param debugLevel
	 * @param tag
	 * @param conent
	 * @param throwable
	 */
	public static void writeLog(int debugLevel, String tag, String conent, Throwable throwable)
	{
		writeLogByAndroidAPI(debugLevel, tag, conent, throwable);
	}
	
	/**
	 * 在日志中输入当前时间，时间会附加在conent后
	 * @param debugLevel
	 * @param tag
	 * @param conent
	 */
	public static void writeCurrentTimeMillisInLog(int debugLevel, String tag, String conent)
	{
		writeLogByAndroidAPI(debugLevel, tag, conent + System.currentTimeMillis(), null);
	}
	
	/**
	 * 使用android API记录日志
	 * @param debugLevel
	 * @param tag
	 * @param msg
	 */
	private static void writeLogByAndroidAPI(int debugLevel, String tag, String msg, Throwable tr)
	{
		if(SYSTEM_DEBUG_LEVEL > debugLevel)
		{
			return;
		}
		
		switch (debugLevel) {
			case DEBUG_LEVEL_INFO:
				if(null == tr){
					Log.i(tag, msg);
				}else{
					Log.i(tag, msg, tr);
				}
				break;
			case DEBUG_LEVEL_WARN:
				if(null == tr){
					Log.w(tag, msg);
				}else{
					Log.w(tag, msg, tr);
				}
				break;
			case DEBUG_LEVEL_ERROR:
				if(null == tr){
					Log.e(tag, msg);
				}else{
					Log.e(tag, msg, tr);
				}
				break;
			default:
				break;
		}
	}
}

package com.tools.utils;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import android.content.Context;

/**
 * File操作工具类
 * @author Ivan
 */
public class FileUtil {

	public static final String TAG = "FileUtil";
	
	/**
	 * 获取缓存目录
	 * @param context 应用上下文<Context>
	 * @param uniqueName 缓存目录下需要建立的目录名称<String>
	 * @return 传入参数uniqueName对应File对象<File>
	 */
	public static File getCacheDir(Context context, String uniqueName) {
		
		File dir = context.getExternalFilesDir(uniqueName);

		if(null == dir){
			// 不存在SD卡
			dir =  new File(context.getCacheDir().getAbsolutePath() + File.separator + uniqueName);
		}
    	
    	if (!dir.exists()) {
			dir.mkdirs();
		}
    	
    	return dir;
    }
	
	/**
	 * 上传文件（夹）
	 * 如果resFile为文件，则上传。
	 * 如果resFile为目录，则上传目录下的文件。
	 * @param resFile 上传文件的File对象<File>
	 * @param strUrl 需要上传的服务器URL<String>
	 * @throws IOException 
	 */
	public static void uploadFiles(File resFile, String strUrl) 
			throws IOException
	{
		if(resFile.isFile()){
			uploadFile(resFile, strUrl);
			
		}else if(resFile.isDirectory()){
			File[] fileList = resFile.listFiles();
			for (File file : fileList) {
				if (file.isFile()) {
					uploadFile(file, strUrl);
				} else if (file.isDirectory()) {
					uploadFiles(file, strUrl);
				}
			}
		} 
	}
	
	/**
	 * 上传文件
	 * @param resFile 上传文件的File对象（只能为文件，不能为目录）<File>。
	 * @param strUrl 需要上传的服务器URL<String>
	 * @throws IOException 
	 */
	private static void uploadFile(File resFile, String strUrl) 
			throws IOException
	{	
		if (!resFile.isFile()){
			LogManager.writeLog(LogManager.DEBUG_LEVEL_ERROR, TAG
					, "Does not support multilayer nested directory");
			return;
		}
		
		String end = "\r\n";
		String twoHyphens = "--";
		String boundary = "----" + UUID.randomUUID().toString();
		
		HttpURLConnection httpURLConnection = null;
		DataOutputStream dos = null;
		FileInputStream fis = null;
		
		try
		{
			URL url = new URL(strUrl);
			httpURLConnection = (HttpURLConnection) url.openConnection();
			httpURLConnection.setDoInput(true);
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setUseCaches(false);
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
			httpURLConnection.setRequestProperty("Charset", "UTF-8");
			httpURLConnection.setRequestProperty("Content-Type"
					, "multipart/form-data; boundary=" + boundary);

			final String fileName = resFile.getName();
			dos = new DataOutputStream(httpURLConnection.getOutputStream());
			dos.writeBytes(twoHyphens + boundary + end);
			dos.writeBytes("Content-Disposition: form-data; name=\"upload\";"
					+ "filename=\"" + fileName + "\"" + end);
			dos.writeBytes("Content-Type: application/x-zip-compressed" + end);

			dos.writeBytes(end);//空行

			fis = new FileInputStream(resFile);
			byte[] buffer = new byte[8192]; // 8k
			int count = 0;
			while ((count = fis.read(buffer)) != -1)
			{
				dos.write(buffer, 0, count);
			}
			dos.writeBytes(end + twoHyphens + boundary + twoHyphens + end);
			dos.flush();
			
			final int responseCode = httpURLConnection.getResponseCode();
			LogManager.writeLog(LogManager.DEBUG_LEVEL_INFO, TAG, String.valueOf(responseCode));
			
		} finally{
			if (null != fis) {
				fis.close();
			}
			if (null != dos) {
				dos.close();
			}
			if (null != httpURLConnection) {
				httpURLConnection.disconnect();
			}
		}
	}
	
	/**
	 * 删除文件，如果传入的resFile为目录，则删除目录下的文件。
	 * @param resFile 需要删除的File对象。<File>
	 */
	public static void deleteFiles(File resFile)
	{
		if (resFile.isFile()) {
			resFile.delete();

		} else if (resFile.isDirectory()) {
			File[] fileList = resFile.listFiles();
			for (File file : fileList) {
				if (file.isFile()) {
					file.delete();
				} else if (file.isDirectory()) {
					deleteFiles(file);
				}
			}
		}
	}
	
	public static void copyFile(String filePath, String newFilePath) throws IOException {
		
		byte[] iobuff = new byte[1024];
		
		FileInputStream fis = null;
		FileOutputStream fos = null;
		
		int bytes;
		try {
			fis = new FileInputStream(filePath);
			fos = new FileOutputStream(newFilePath);
			while ((bytes = fis.read(iobuff)) != -1) {
				fos.write(iobuff, 0, bytes);
			}
		} finally {
			if(null != fis){
				fis.close();
			}
			
			if(null != fos){
				fos.close();
			}
			
		}
	}
}

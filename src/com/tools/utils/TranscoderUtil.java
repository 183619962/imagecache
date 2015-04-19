package com.tools.utils;

/**
 * 编码格式转换器
 * @author Ivan
 */
public class TranscoderUtil {
	
	/**
	 * 将16进制字符串转换为字节数组
	 * @param hexString 16进制字符串<String>
	 * @return 字节数组
	 */
	public static byte[] HexStringToByteArray(String hexString) {
		byte[] byteArray = new byte[hexString.length() / 2];
		for (int i = 0; i < byteArray.length; i++) {
			byteArray[i] = Integer.decode("#" + hexString.substring(2 * i, 2 * i + 2))
					.byteValue();
		}
		return byteArray;
	}
	
	/**
	 * 将字节数组转换为16进制字符串
	 * @param bytes 字节数组
	 * @return 16进制字符串
	 */
	public static String bytesToHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b & 0xFF));
		}
		return sb.toString();
	}
}

package net.ncmusic.netease;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 网易云音乐 weapi 加密（纯 Java 实现，无需外部服务）。
 * 流程与官方网页端一致：AES-128-CBC 加密两次 + RSA(无填充) 加密随机会话密钥。
 */
public final class CryptoUtil {
	private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
	private static final String IV = "0102030405060708";
	private static final String MODULUS =
			"00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725" +
			"152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ec" +
			"bda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cf" +
			"e4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";
	private static final String PUB_EXPONENT = "10001";
	private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private CryptoUtil() {}

	/** 返回 {params, encSecKey} */
	public static String[] weapiEncrypt(String json) {
		String secretKey = randomKey(16);
		String params = aesEncrypt(aesEncrypt(json, PRESET_KEY), secretKey);
		String encSecKey = rsaEncrypt(new StringBuilder(secretKey).reverse().toString());
		return new String[]{params, encSecKey};
	}

	private static String randomKey(int len) {
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
		}
		return sb.toString();
	}

	private static String aesEncrypt(String text, String key) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE,
					new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
					new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
			byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(encrypted);
		} catch (Exception e) {
			throw new RuntimeException("AES 加密失败", e);
		}
	}

	private static String rsaEncrypt(String reversedKey) {
		BigInteger text = new BigInteger(1, reversedKey.getBytes(StandardCharsets.UTF_8));
		BigInteger modulus = new BigInteger(MODULUS, 16);
		BigInteger exponent = new BigInteger(PUB_EXPONENT, 16);
		String hex = text.modPow(exponent, modulus).toString(16);
		// 左侧补零到 256 个十六进制字符
		StringBuilder sb = new StringBuilder(hex);
		while (sb.length() < 256) sb.insert(0, '0');
		return sb.toString();
	}

	public static String md5Hex(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (Exception e) {
			throw new RuntimeException("MD5 失败", e);
		}
	}
}

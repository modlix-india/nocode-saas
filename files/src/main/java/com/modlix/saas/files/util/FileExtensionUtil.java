package com.modlix.saas.files.util;

public class FileExtensionUtil {

	public static String getExtension(String name) {

		if (name == null || name.isBlank())
			return "";

		int ind = name.lastIndexOf('.');
		if (ind == -1 || ind + 1 == name.length())
			return "";

		return name.substring(ind + 1);
	}

	public static String getFileNameWithExtension(String actualFile, String fileName) {

		if (fileName == null || fileName.isBlank())
			return "";

		String acutalExt = getExtension(actualFile);
		String ext = getExtension(fileName);

		if (ext.isBlank() || !ext.equals(acutalExt))
			return fileName + "." + acutalExt;

		return fileName;
	}

	private FileExtensionUtil() {
	}
}

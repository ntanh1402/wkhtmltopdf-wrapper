package com.wkhtmltopdf;

public class HtmlToPdf {

	public static boolean convertHtmlBytes(byte[] htmlBytes, String pdfFile, boolean landscape) {
		try {
			BinaryWrapper.getInstance().convert(htmlBytes, pdfFile, landscape);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

}

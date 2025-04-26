package com.franco.dev.utilitarios;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {

    public static String encryptString(String strClearText,String strKey) throws Exception{
        String strData="";

        try {
            SecretKeySpec skeyspec=new SecretKeySpec(strKey.getBytes(),"Blowfish");
            Cipher cipher=Cipher.getInstance("Blowfish");
            cipher.init(Cipher.ENCRYPT_MODE, skeyspec);
            byte[] encrypted=cipher.doFinal(strClearText.getBytes());
            strData=new String(encrypted);

        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(e);
        }
        return strData;
    }

    public static String decryptString(String strEncrypted,String strKey) throws Exception{
        String strData="";

        try {
            SecretKeySpec skeyspec=new SecretKeySpec(strKey.getBytes(),"Blowfish");
            Cipher cipher=Cipher.getInstance("Blowfish");
            cipher.init(Cipher.DECRYPT_MODE, skeyspec);
            byte[] decrypted=cipher.doFinal(strEncrypted.getBytes());
            strData=new String(decrypted);

        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception(e);
        }
        return strData;
    }

    private static String getValue(String regex, String text) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static boolean isValidLong(String str) {
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String convertToCustomFormat(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        String upperInput = input.toUpperCase(); // Convert to uppercase first
        for (char c : upperInput.toCharArray()) {
            if (c != ' ') { // Ignore spaces
                result.append("%").append(c);
            }
        }
        result.append("%"); // Add trailing '%'
        return result.toString();
    }

}

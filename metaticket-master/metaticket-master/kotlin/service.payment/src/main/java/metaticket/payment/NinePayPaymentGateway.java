package metaticket.payment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class NinePayPaymentGateway {

    private static final String END_POINT = "https://sand-payment.9pay.vn";

    private static final String CHECKSUM_KEY = "h4FxtOIznuEiFpeirLobRXH02btDWxjw";

    public static String createNinePayPaymentRedirectUrl(
            String merchantKey,
            String merchantSecretKey,
            String baseUrl,
            String invoiceNo,
            String amount,
            String description,
            String method,
            String returnUrl
    ) throws UnsupportedEncodingException {
        String time = String.valueOf(System.currentTimeMillis() / 1000);

        Map<String, String> map = new TreeMap<String, String>();
        map.put("merchantKey", merchantKey);
        map.put("time", time);
        map.put("invoice_no", invoiceNo);
        map.put("amount", amount);
        map.put("description", description);
        map.put("return_url", returnUrl);
        if(!method.isEmpty()) {
            map.put("method", method);
        }
        String queryHttp = http_build_query(map);
        String message = buildMessage(baseUrl, queryHttp, time);
        String signature = signature(message, merchantSecretKey);
        byte[] byteArray = jsonEncode(map).getBytes();
        String baseEncode = Base64.getEncoder().encodeToString(byteArray);

        Map<String, String> queryBuild = new HashMap<String, String>();
        queryBuild.put("baseEncode", baseEncode);
        queryBuild.put("signature", signature);
        // return direct link for 9Pay Gateway
        return baseUrl + "/portal?" + http_build_query(queryBuild);
    }

    public static String inquiryNinePayPayment(String merchantKey, String merchantSecretKey, String baseUrl, String invoiceNo) throws Exception {
        String time = String.valueOf(System.currentTimeMillis() / 1000);
        String mesage = buildInquiryMessage(baseUrl, time, invoiceNo);
        String signature = signature(mesage, merchantSecretKey);

        URL url = new URL(baseUrl + "/v2/payments/" + invoiceNo + "/inquire");
        HttpURLConnection http = (HttpURLConnection)url.openConnection();
        http.setRequestProperty("Date", time);
        http.setRequestProperty("Authorization", "Signature Algorithm=HS256,Credential=" + merchantKey + ",SignedHeaders=,Signature=" + signature);

        System.out.println(http.getResponseCode() + " " + http.getResponseMessage());

        BufferedReader in = new BufferedReader(new InputStreamReader(http.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        http.disconnect();

        return response.toString();
    }

    public static String jsonEncode(Map<String, String> array) throws UnsupportedEncodingException {
        String string = "";
        Iterator it = array.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = (Map.Entry) it.next();
            String key = entry.getKey();
            String value = entry.getValue();
            if (!it.hasNext()) {
                string = string + '"' + key + '"' + ":" + '"' + value + '"';
            } else {
                string = string + '"' + key + '"' + ":" + '"' + value + '"' + ",";
            }
        }
        return "{" + string + "}";
    }

    public static String buildMessage(String baseUrl, String queryHttp, String time) {
        StringBuffer sb = new StringBuffer();
        sb.append("POST");
        sb.append("\n");
        sb.append(baseUrl + "/payments/create");
        sb.append("\n");
        sb.append(time);
        sb.append("\n");
        sb.append(queryHttp);
        return sb.toString();
    }

    public static String buildInquiryMessage(String baseUrl, String time, String invoiceNo) {
        StringBuffer sb = new StringBuffer();
        sb.append("GET");
        sb.append("\n");
        sb.append(baseUrl + "/v2/payments/" + invoiceNo + "/inquire");
        sb.append("\n");
        sb.append(time);
        return sb.toString();
    }


    public static String signature(String queryHttp, String secretKey) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hmac = sha256_HMAC.doFinal(queryHttp.getBytes());
            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String http_build_query(Map<String, String> array) {
        String reString = "";
        Iterator it = array.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = (Map.Entry) it.next();
            String key = entry.getKey();
            String value = entry.getValue();
            reString += key + "=" + value + "&";
        }
        reString = reString.substring(0, reString.length() - 1);
        reString = java.net.URLEncoder.encode(reString);
        reString = reString.replace("%3D", "=").replace("%26", "&");
        return reString;
    }

    //decode result

    public static String decodePaymentResult(String result, String checksum, String checksumKey) throws NoSuchAlgorithmException {
        String checksum_gen = toHexString(getSHA(result + checksumKey));
        if(checksum.equals(checksum_gen)) {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(result);
            String decodedString = new String(decodedBytes);
            return decodedString;
        }
        return "";
    }

    public static byte[] getSHA(String input) throws NoSuchAlgorithmException
    {
        // Static getInstance method is called with hashing SHA
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        // digest() method called
        // to calculate message digest of an input
        // and return array of byte
        return md.digest(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String toHexString(byte[] hash)
    {
        // Convert byte array into signum representation
        BigInteger number = new BigInteger(1, hash);

        // Convert message digest into hex value
        StringBuilder hexString = new StringBuilder(number.toString(16));

        // Pad with leading zeros
        while (hexString.length() < 64)
        {
            hexString.insert(0, '0');
        }

        return hexString.toString().toUpperCase();
    }
}

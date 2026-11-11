package metaticket.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class OnePayUtil {

    public static final char[] hexCode = "0123456789ABCDEF".toCharArray();

    public static String printHexBinary(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(hexCode[(b >> 4) & 0xF]);
            r.append(hexCode[(b & 0xF)]);
        }
        return r.toString();
    }

    public static int hexToBin(char ch) {
        if ('0' <= ch && ch <= '9')
            return ch - '0';
        if ('A' <= ch && ch <= 'F')
            return ch - 'A' + 10;
        if ('a' <= ch && ch <= 'f')
            return ch - 'a' + 10;
        return -1;
    }

    public static byte[] parseHexBinary(String s) {
        final int len = s.length();

        // "111" is not a valid hex encoding.
        if (len % 2 != 0)
            throw new IllegalArgumentException("hexBinary needs to be even-length: " + s);

        byte[] out = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            int h = hexToBin(s.charAt(i));
            int l = hexToBin(s.charAt(i + 1));
            if (h == -1 || l == -1)
                throw new IllegalArgumentException("contains illegal character for hexBinary: " + s);

            out[i / 2] = (byte) (h * 16 + l);
        }

        return out;
    }

    public static TreeMap<String, String> sortMap(Map<String, String> queryParamMap) {
        return new TreeMap<>(queryParamMap);
    }

    public static String generateStringToHash(Map<String, String> queryParamSorted) {
        String stringToHash = "";
        for (Map.Entry<String, String> items : queryParamSorted.entrySet()) {
            String key = items.getKey();
            String value = items.getValue().toString();
            String pref4 = key.substring(0, 4);
            String pref5 = key.substring(0, 5);
            if ("vpc_".equals(pref4) || "user_".equals(pref5)) {
                if (!"vpc_SecureHash".equals(key) && !"vpc_SecureHashType".equals(key)) {
                    if (value.length() > 0) {
                        if (stringToHash.length() > 0) {
                            stringToHash += "&";
                        }
                        stringToHash += key + "=" + value;
                    }
                }
            }
        }
        return stringToHash;
    }

    public static String generateSecureHash(String stringToHash, String merchantHashCode) throws Exception {
        byte[] merchantHashHex = parseHexBinary(merchantHashCode);
        SecretKeySpec signingKey = new SecretKeySpec(merchantHashHex, "HMACSHA256");
        Mac mac = Mac.getInstance("HMACSHA256");
        mac.init(signingKey);
        String secureHash = printHexBinary(mac.doFinal(stringToHash.getBytes(UTF_8)));
        return secureHash;
    }

    public static Map<String, String> parseQueryParams(String url) throws Exception {
        String queryString = new URL(url).getQuery();
        if (queryString == null) {
            return new HashMap<>();
        }
        Map<String, String> params = new HashMap<>();
        if (queryString != null && queryString.length() > 0) {
            for (String kv : queryString.split("&")) {
                if (kv != null && kv.contains("=")) {
                    String[] aryKV = kv.split("=");
                    if (aryKV.length == 2) {
                        params.put(URLDecoder.decode(aryKV[0], "UTF-8"), URLDecoder.decode(aryKV[1], "UTF-8"));
                    } else {
                        params.put(URLDecoder.decode(aryKV[0], "UTF-8"), "");
                    }
                }
            }
        }
        return params;
    }

    public static void executeGet(String urlRequest) {
        try {
            // Create a URL object with the endpoint URL
            URL url = new URL(urlRequest);

            // Open a connection to the URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set the request method to GET
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);

            // Set the timeout for the connection
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // Send the GET request
            int responseCode = connection.getResponseCode();

            // Check if the response code is successful (200 OK)
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                String redirectURL = connection.getHeaderField("location");
                if (redirectURL != null) {
                    System.out.println("Link: " + redirectURL);
                } else {
                    System.out.println("Link: null");
                }
            } else {
                // If the response code is not successful, print error message
                System.out.println("GET request failed with response code: " + responseCode);
            }

            // Disconnect the connection
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void executeGetWithHeader(String urlRequest, Map<String, String> headerRequest) {
        try {
            // Create a URL object with the endpoint URL
            URL url = new URL(urlRequest);

            // Open a connection to the URL
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // set header
            for (Map.Entry<String, String> entry : headerRequest.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                connection.setRequestProperty(key, value);
            }

            // Set the request method to GET
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);

            // Set the timeout for the connection
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // Send the GET request
            int responseCode = connection.getResponseCode();

            // Check if the response code is successful (200 OK)
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Get input stream from the connection
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                // Read the response line by line
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                // Close the input stream
                in.close();

                // Print the response
                System.out.println("Response: " + response.toString());
            } else {
                // If the response code is not successful, print error message
                System.out.println("GET request failed with response code: " + responseCode);
            }

            // Disconnect the connection
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void executePost(String urlRequest, String queryParam) {
        try {
            // Construct the URL
            URL url = new URL(urlRequest);

            // Create connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set request method to POST
            connection.setRequestMethod("POST");

            // Set content type
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            // Enable output and disable caching
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            // Construct the form data
            String formData = queryParam; // Adjust accordingly

            // Encode the form data
            byte[] postData = formData.getBytes("UTF-8");

            // Write the encoded form data to the request body
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(postData);
            }

            // Get the response
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read the response
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    System.out.println("Response: " + response.toString());
                }
            } else {
                System.out.println("POST request failed with response code: " + responseCode);
            }

            // Close connection
            connection.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String createRequestSignatureITA(String method, String uri, List<String> signedHeaderNames,
            Map<String, String> httpHeaders, String merchantId, String merchantHashCode) {
        try {
            long created = Instant.now().getEpochSecond();
            Map<String, String> lowercaseHeaders = new HashMap<>();

            for (Map.Entry<String, String> entry : httpHeaders.entrySet()) {
                lowercaseHeaders.put(entry.getKey().toLowerCase(), entry.getValue());
            }

            lowercaseHeaders.put("(request-target)", method.toLowerCase() + " " + uri);
            lowercaseHeaders.put("(created)", String.valueOf(created));

            StringBuilder signingString = new StringBuilder();

            String headerNames = "";
            for (String element : signedHeaderNames) {
                String headerName = element;
                if (!lowercaseHeaders.containsKey(headerName)) {
                    throw new IllegalArgumentException(headerName);
                }
                if (signingString.length() != 0) {
                    signingString.append("\n");
                }
                signingString.append(headerName).append(": ").append(lowercaseHeaders.get(headerName));

                if (!headerNames.isEmpty()) {
                    headerNames += " ";
                }
                headerNames += headerName;
            }

            System.out.println("signingString=" + signingString);

            byte[] hmacKey = parseHexBinary(merchantHashCode);
            byte[] signatureBytes;

            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(hmacKey, "HmacSHA512"));
            byte[] data = signingString.toString().getBytes(StandardCharsets.UTF_8);
            signatureBytes = hmac.doFinal(data);

            String signature = Base64.getEncoder().encodeToString(signatureBytes);
            String signingAlgorithm = "hs2019";

            return String.format("algorithm=\"%s\", keyId=\"%s\", headers=\"%s\", created=%d, signature=\"%s\"",
                    signingAlgorithm, merchantId, headerNames, created, signature);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "";
        }
    }
}

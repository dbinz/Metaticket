
import java.util.regex.Pattern;
public class TestRegex {
    public static void main(String[] args) {
        String url = "https://news.staging.metaticket.vn/wp-content/uploads/2026/08/Anh-META-Ticket.jpg";
        String base = "https://news.staging.metaticket.vn";
        
        String out = url;
        out = Pattern.compile("https?://(?:admin|news)\\.[^/\"'\\s]+/").matcher(out).replaceAll(base + "/");
        out = Pattern.compile("https?://(?:admin|news)\\.[^/\"'\\s]+/(wp-content|wp-includes)/").matcher(out).replaceAll(base + "/$1/");
        
        System.out.println("Result: " + out);
    }
}


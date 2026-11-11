
val url = "https://news.staging.metaticket.vn/wp-content/uploads/2026/08/Anh-META-Ticket.jpg"
val base = "https://news.staging.metaticket.vn"
var out = url
out = out.replace(
    Regex("https?://(?:admin|news)\\.[^/\"'\\s]+/"),
    "$base/",
)
out = out.replace(
    Regex("https?://(?:admin|news)\\.[^/\"'\\s]+/(wp-content|wp-includes)/"),
    "$base/$1/",
)
println("Result: " + out)


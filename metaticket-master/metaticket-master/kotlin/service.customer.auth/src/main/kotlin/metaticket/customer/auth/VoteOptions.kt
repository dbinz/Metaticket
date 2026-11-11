package metaticket.customer.auth

/**
 * Danh sách lựa chọn vote hardcode — tính năng tạm thời, không cần config động.
 * Đây là nguồn sự thật duy nhất: FE fetch danh sách này qua API thay vì hardcode riêng.
 */
object VoteOptions {
    data class Option(
        val id: String,
        val label: String,
        val thumbnailUrl: String?,
        val description: String?
    )

    val ALL: List<Option> = listOf(
        Option(
            "quang_hung_master_d",
            "Quang Hùng Master D",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/quang_hung.png",
            ""
        ),

        Option(
            "hoang_ton",
            "Hoàng Tôn",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/hoang_ton.png",
            ""
        ),

        Option(
            "trong_hieu",
            "Trọng Hiếu",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/trong_hieu.png",
            ""
        ),

        Option(
            "mono",
            "Mono",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/mono.png",
            ""
        ),

        Option(
            "hoang_dung",
            "Hoàng Dũng",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/hoang_dung.png",
            ""
        ),

        Option(
            "den_vau",
            "Đen Vâu",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/den_vau.png",
            ""
        ),

        Option(
            "wren_evans",
            "Wren Evans",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/wren_evans.png",
            ""
        ),

        Option(
            "dalab",
            "DaLab",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/dalab.png",
            ""
        ),

        Option(
            "quan_ap",
            "Quân AP",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/quan_ap.png",
            ""
        ),

        Option(
            "captain_boy",
            "Captain Boy",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/captain_boy.png",
            ""
        ),

        Option(
            "cheng",
            "Cheng",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/cheng.png",
            ""
        ),

        Option(
            "buitruonglinh",
            "buitruonglinh",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/buitruonglinh.png",
            ""
        ),

        Option(
            "dinh_manh_ninh",
            "Đinh Mạnh Ninh",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/dinh_manh_ninh.png",
            ""
        ),

        Option(
            "grey_d",
            "Grey D",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/grey_d.png",
            ""
        ),

        Option(
            "double_2t",
            "Double 2T",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/double_2t.png",
            ""
        ),

        Option(
            "low_g",
            "LowG",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/low_g.png",
            ""
        ),

        Option(
            "son_k",
            "Sơn K",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/son_k.png",
            ""
        ),

        Option(
            "dangrangto",
            "dangrangto",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/dangrangto.png",
            ""
        ),

        Option(
            "the_flob",
            "The Flob",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/the_flob.png",
            ""
        ),

        Option(
            "tlinh",
            "Tlinh",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/tlinh.png",
            ""
        ),

        Option(
            "amee",
            "Amee",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/amee.png",
            ""
        ),

        Option(
            "orange",
            "Orange",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/orange.png",
            ""
        ),

        Option(
            "la_moon",
            "La Moon",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/la_moon.png",
            ""
        ),

        Option(
            "muoi",
            "Muội",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/muoi.png",
            ""
        ),

        Option(
            "lyhan",
            "LyHan",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/lyhan.png",
            ""
        ),

        Option(
            "bich_phuong",
            "Bích Phương",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/bich_phuong.png",
            ""
        ),

        Option(
            "bao_anh",
            "Bảo Anh",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/bao_anh.png",
            ""
        ),

        Option(
            "huong_tram",
            "Hương Tràm",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/huong_tram.png",
            ""
        ),

        Option(
            "phuong_ly",
            "Phương Ly",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/phuong_ly.png",
            ""
        ),

        Option(
            "juky_san",
            "Juky San",
            "https://metaticket.com.vn/assets/neu/concert-2026/artists/juky_san.png",
            ""
        ),
    )

    const val MULTI: Boolean = true
    const val MAX_CHOICE_PER_VOTE: Int = 7

    fun isValid(id: String) = ALL.any { it.id == id }
}

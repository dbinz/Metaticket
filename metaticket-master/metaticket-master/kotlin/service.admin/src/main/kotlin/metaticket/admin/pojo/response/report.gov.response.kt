package metaticket.admin.pojo.response

data class ReportGovResponse(
    val soLuongTruyCap: Long,
    val soNguoiBan: Long,
    val soNguoiBanMoi: Long,
    val tongSoSanPham: Long,
    val soSanPhamMoi: Long,
    val soLuongGiaoDich: Long,
    val tongSoDonHangThanhCong: Long,
    val tongSoDonHangKhongThanhCong: Long,
    val tongGiaTriGiaoDich: Long,
)
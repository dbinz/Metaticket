package metaticket.payment.gateway.worker

class OnePayConstants {
    companion object {
        const val Approved = "0"                            // Giao dịch thành công
        const val Unspecified_Failure = "1"                 // Ngân hàng từ chối cấp phép giao dịch.
        const val Declined = "2"                            // Ngân hàng phát hành thẻ từ chối cấp phép giao dịch.
        const val Timed_Out = "3"                           // Không nhận được kết quả phản hồi từ Tổ chức phát hành thẻ.
        const val Expired_Card_Date = "4"                   // Tháng/Năm hết hạn của thẻ không đúng hoặc thẻ đã hết hạn sử dụng.
        const val Insufficient_Funds = "5"                  // Số dư/Hạn mức của thẻ không đủ để thanh toán.
        const val Error_Communicating_With_Bank = "6"       // Không nhận được kết quả phản hồi từ Tổ chức phát hành thẻ.
        const val System_Error = "7"                        // Lỗi trong quá trình xử lý giao dịch của Ngân hàng.
        const val Not_Supported = "8"                       // Ngân hàng phát hành thẻ không hỗ trợ thanh toán trực tuyến.
        const val Invalid_Card_Name  = "9"                  // Tên chủ thẻ/tài khoản không hợp lệ.
        const val Expired_Card = "10"                       // Thẻ hết hạn/Thẻ bị khóa.
        const val Not_Registered = "11"                     // Thẻ/Tài khoản chưa đăng ký dịch vụ hỗ trợ thanh toán trực tuyến.
        const val Invalid_Card_Date = "12"                  // Tháng/Năm phát hành hoặc hết hạn của thẻ không hợp lệ.
        const val Exist_Amount = "13"                       // Giao dịch vượt quá hạn mức thanh toán trực tuyến theo quy định của Ngân hàng.
        const val Invalid_Card_Number = "14"                // Số thẻ không hợp lệ.

        const val Insufficient_Fund = "21"                  // Số dư tài khoản không đủ để thanh toán.
        const val Invalid_Account = "22"                    // Thông tin tài khoản không hợp lệ.
        const val Account_Lock = "23"                       // Thẻ/Tài khoản bị khóa hoặc chưa được kích hoạt.
        const val Invalid_Card_Info = "24"                  // Thông tin thẻ/tài khoản không hợp lệ.
        const val Invalid_OTP = "25"                        // Mã xác thực OTP không hợp lệ.
        const val Expired_OTP = "26"                        // Mã xác thực OTP đã hết hiệu lực.

        const val Authentication_Cancelled = "98"           // Xác thực giao dịch bị hủy.
        const val User_Cancel = "99"                        // Người dùng hủy giao dịch.

        const val Transaction_Blocked = "B"                 // Lỗi trong quá trình xác thực giao dịch của Ngân hàng phát hành thẻ.
        const val Awaiting_Processing = "D"                 // Lỗi trong quá trình xác thực giao dịch của Ngân hàng phát hành thẻ.
        const val Secure_Failure = "F"                      // Xác thực giao dịch không thành công.
        const val Card_Security_Code_Failed = "U"           // Xác thực mã CSC không thành công.
        const val Cannot_Process_Card = "Z"                 // Giao dịch bị từ chối.

        const val Expired = "253"                           // Hết thời hạn nhập thông tin thanh toán.

        const val Other = ""                                // Lỗi không xác định.

        val onepayMessages = mapOf(
            Approved to "Giao dịch thành công",
            Unspecified_Failure to "Ngân hàng từ chối cấp phép giao dịch.",
            Declined to "Ngân hàng phát hành thẻ từ chối cấp phép giao dịch.",
            Timed_Out to "Không nhận được kết quả phản hồi từ Tổ chức phát hành thẻ.",
            Expired_Card_Date to "Tháng/Năm hết hạn của thẻ không đúng hoặc thẻ đã hết hạn sử dụng.",
            Insufficient_Funds to "Số dư/Hạn mức của thẻ không đủ để thanh toán",
            Error_Communicating_With_Bank to "Không nhận được kết quả phản hồi từ Tổ chức phát hành thẻ",
            System_Error to "Lỗi trong quá trình xử lý giao dịch của Ngân hàng",
            Not_Supported to "Ngân hàng phát hành thẻ không hỗ trợ thanh toán trực tuyến",
            Invalid_Card_Name  to "Tên chủ thẻ/tài khoản không hợp lệ",
            Expired_Card to "Thẻ hết hạn/Thẻ bị khóa",
            Not_Registered to "Thẻ/Tài khoản chưa đăng ký dịch vụ hỗ trợ thanh toán trực tuyến",
            Invalid_Card_Date to "Tháng/Năm phát hành hoặc hết hạn của thẻ không hợp lệ",
            Exist_Amount to "Giao dịch vượt quá hạn mức thanh toán trực tuyến theo quy định của Ngân hàng",
            Invalid_Card_Number to "Số thẻ không hợp lệ",

            Insufficient_Fund to "Số dư tài khoản không đủ để thanh toán",
            Invalid_Account to "Thông tin tài khoản không hợp lệ",
            Account_Lock to "Thẻ/Tài khoản bị khóa hoặc chưa được kích hoạt",
            Invalid_Card_Info to "Thông tin thẻ/tài khoản không hợp lệ",
            Invalid_OTP to "Mã xác thực OTP không hợp lệ",
            Expired_OTP to "Mã xác thực OTP đã hết hiệu lực",

            Authentication_Cancelled to "Xác thực giao dịch bị hủy",
            User_Cancel to "Người dùng hủy giao dịch",

            Transaction_Blocked to "Lỗi trong quá trình xác thực giao dịch của Ngân hàng phát hành thẻ",
            Awaiting_Processing to "Lỗi trong quá trình xác thực giao dịch của Ngân hàng phát hành thẻ",
            Secure_Failure to "Xác thực giao dịch không thành công",
            Card_Security_Code_Failed to "Xác thực mã CSC không thành công",
            Cannot_Process_Card to "Giao dịch bị từ chối",

            Expired to "Hết thời hạn nhập thông tin thanh toán",

            Other to "Lỗi không xác định",
        )
    }
}
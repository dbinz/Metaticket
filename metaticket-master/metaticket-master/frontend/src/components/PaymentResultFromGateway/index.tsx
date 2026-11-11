import { FIVE_SECONDS } from "@/constants"
import { CountdownTimer } from "../ReservationCountdown/CountdownTimer"
import { Button } from "../ui/button"


type PaymentResultFromGatewayProps = {
    loading: Boolean,
    error: Boolean,
    message: String
}

const PaymentResultFromGateway = (props: PaymentResultFromGatewayProps) => {

    const closeWindow = () => {
        window.close();
    }

    const onTimeout = () => {
        closeWindow()
    }

    return (
        <div className="w-full flex items-center justify-center flex-col">
            <h2 className="text-xl font-bold uppercase mt-12">
                {props.loading ? "Đang kiểm tra" : props.error ? "Thanh toán thất bại" : "Thanh toán thành công"}
            </h2>
            {props.error && (
                <h6 className="text-xl mt-12">
                    {props.message}
                </h6>
            )}
            
            <div className="flex flex-row items-center mt-12 ">
                <div className="mr-1">
                    Trang sẽ tự động đóng sau: 
                </div>
                
                <CountdownTimer
                    startTime={Date.now()}
                    duration={FIVE_SECONDS}
                    onTimeout={onTimeout}
                    />
            </div>
            
            <Button
                className="mt-12"
                onClick={closeWindow}
            >
                Quay lại ngay
            </Button>
            
        </div>
    )
}

export { PaymentResultFromGateway }
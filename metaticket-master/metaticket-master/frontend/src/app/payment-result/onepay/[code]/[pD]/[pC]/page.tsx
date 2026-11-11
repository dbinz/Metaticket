
'use client';
import {useUpdateOnePayPaymentResultMutation} from "@/data/payment/query";
import {useEffect, useState} from "react";
import {PaymentResultFromGateway} from "@/components/PaymentResultFromGateway";


export default function OnePayPaymentResult() {

    const updateOnePayPaymentResultMutation = useUpdateOnePayPaymentResultMutation()

    const [error, setError] = useState(false)
    const [message, setMessage] = useState("")
    const [loading, setLoading] = useState(true)
    const [isChecking, setIsChecking] = useState(false)


    useEffect(() => {
        var pageName = "payment-result/onepay/"
        var urlFromWindow = window.location.href
        var start = urlFromWindow.lastIndexOf(pageName)
        var url = urlFromWindow.slice(start + pageName.length, urlFromWindow.length)
        
        updateOnePayPaymentResultMutation.mutate(
            url,
            {
                onError: (err) => {
                    setError(true)
                    setMessage(err.message)
                    setLoading(false)
                },
                onSuccess: (data) => {
                    if(data.code == 0) {
                        setError(false)
                    } else if(data.code == 1) {
                        setError(true)
                    } else if(data.code == 3) {
                        setIsChecking(true)
                        setError(false)
                    }
                    setMessage(data.message)
                    setLoading(false)
                },
            }
          )
    }, [])
    
    return (
        <PaymentResultFromGateway loading={loading || isChecking} error={error} message={message}/>
    )
}

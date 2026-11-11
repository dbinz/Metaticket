import { useStepDataContext } from "@/components/BookingSteps/StepDataContext";
import { Button } from "@/components/ui/button";
import { BrowserBlockPopupDialog } from "@/components/utils/MessageDialogs";
import { OrderCustomField, OrderCustomFieldDataCollectorConstraints, OrderDataCollectorStatus, useOrderDataCollectorGenerateLinkMutation, useOrderDataCollectorIsValidLinkMutation } from "@/data/order";
import { useEffect, useState } from "react";


const CustomFieldDataCollector = ({
    fieldInfo,
    setValue,
    onDataCollectorSucceed,
}: {
    fieldInfo: OrderCustomField,
    setValue: (value: string) => void,
    onDataCollectorSucceed: (id: string) => void,
}) => {
    const [require, setRequire] = useState(false);
    const [openBrowserBlockPopup, setOpenBrowserBlockPopup] = useState(false);
    const [linkStatus, setLinkStatus] = useState(OrderDataCollectorStatus.empty);
    const [newTab, setNewTab] = useState<Window | null>(null);

    const {
        seatSelectionState,
    } = useStepDataContext();

    useEffect(() => {
        if(fieldInfo.value.trim()) {
            checkLinkValid(fieldInfo.value)
        }
    }, [fieldInfo.value])

    useEffect(() => {
        switch(linkStatus) {
            case OrderDataCollectorStatus.valid:
                onDataCollectorSucceed(fieldInfo.id);
                break;
            case OrderDataCollectorStatus.invalid:
        }
    }, [linkStatus])

    useEffect(() => {
        var bc = new BroadcastChannel('data-collector-channel');
        bc.onmessage = function(event) {
            switch(event.data.action) {
                case `collect-success`:
                    setLinkStatus(OrderDataCollectorStatus.valid);
                    break;
                case `collect-fail`:
                    setLinkStatus(OrderDataCollectorStatus.invalid);
            }
        }
        generateLink()
        if(fieldInfo.constraints) {
            var constraints = OrderCustomFieldDataCollectorConstraints.parse(JSON.parse(fieldInfo.constraints));
            setRequire(constraints.require);
            
        }
        
    }, []);

    const orderDataCollectorGenerateLinkMutation = useOrderDataCollectorGenerateLinkMutation()
    const generateLink = () => {
        // setValue("http://localhost:3000/test")

        return orderDataCollectorGenerateLinkMutation.mutate(
            {resId: seatSelectionState.reservation?.id ?? ""},
            {
                onError: (err: Error) => {
                    console.error(err)
                },
                onSuccess: (link: string) => {
                    setValue(link)
                },
            }
        )
    }

    const orderDataCollectorIsValidLinkMutation = useOrderDataCollectorIsValidLinkMutation()
    const checkLinkValid = (link: string) => {
        return orderDataCollectorIsValidLinkMutation.mutate(
            link,
            {
                onError: (err: Error) => {
                    console.error(err);
                },
                onSuccess: (status: OrderDataCollectorStatus) => {
                    setLinkStatus(status);
                },
            }
        )
    }


    const openDataCollectorWindow = () => {
        if(newTab) {
            newTab.focus()
        } else {
            const tab = window.open(fieldInfo.value, '_blank', "height=600,width=1000");
            setNewTab(tab);
            
            if(tab) {
                tab.focus();
                var timer = setInterval(function() { 
                    if(tab.closed) {
                        clearInterval(timer);
                        setNewTab(null);
                    }
                }, 1000);
            } else {
                setOpenBrowserBlockPopup(true);
            }
        }
    }

    return (
        <div>
            <label className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">{fieldInfo.displayName} {require && <>(<span className="text-destructive">*</span>)</>}</label>
            <div
                className="content-center flex mb-1 bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white"
            >
                <img
                    src={
                        linkStatus == OrderDataCollectorStatus.valid ? '/assets/frontend/icons/check_icon.png' : linkStatus == OrderDataCollectorStatus.invalid ? "/assets/frontend/icons/error_icon.png" : "/assets/frontend/icons/alert_icon.png"
                    }
                    alt='data-collector-status-icon'
                    className="w-4 h-4"
                />
                <div className="grow block truncate cursor-pointer ml-1" onClick={() => openDataCollectorWindow()}>
                    {fieldInfo.value}
                    {/* {fieldInfo.value && (
                        <a href={fieldInfo.value} target="_blank,">
                            <div className="block truncate">
                                {fieldInfo.value}
                            </div>
                        </a>
                    )} */}
                </div>
                
                <div className="ml-1 text-sm font-medium cursor-pointer" onClick={() => navigator.clipboard.writeText(fieldInfo.value)}>
                    Copy
                </div>
                
            </div>
            
            
            <BrowserBlockPopupDialog open={openBrowserBlockPopup} processPayment={openDataCollectorWindow} cancelPayment={() => {}}/>
        </div>
    )
}

export default CustomFieldDataCollector;
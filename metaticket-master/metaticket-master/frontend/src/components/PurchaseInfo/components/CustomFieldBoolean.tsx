
import { OrderCustomField, OrderCustomFieldBooleanConstraints, OrderCustomFieldNumberConstraints } from "@/data/order";
import { cn } from "@/lib/utils";
import { ChangeEvent, useEffect, useState } from "react";

const CustomFieldBoolean = ({
    fieldInfo,
    setValue
}: {
    fieldInfo: OrderCustomField,
    setValue: (value: string) => void
}) => {

    const [require, setRequire] = useState(false)

    useEffect(() => {
        if(fieldInfo.constraints) {
            var constraints = OrderCustomFieldBooleanConstraints.parse(JSON.parse(fieldInfo.constraints));
            setRequire(constraints.require);
            if(fieldInfo.value === '') {
                setValue('false');
            }
        }
    }, [])

    const setBooleanValue = (event: ChangeEvent<HTMLInputElement>) => {
        setValue(`${event.target.checked}`)
    }
    return (
        <div>
            <label className="block text-sm font-medium text-gray-900 dark:text-white">&nbsp;</label>
            <div className="flex items-center">
                <input 
                    type="checkbox" 
                    onChange={setBooleanValue}
                    defaultChecked={fieldInfo.value === "true"}
                    className={cn(
                        
                        "w-6 h-6 text-blue-600 bg-gray-100 border-gray-300 rounded",
                        fieldInfo.error ? 'bg-red-100 border-red-300 focus:border-red-500 focus:ring-red-500 dark:focus:ring-red-500 dark:focus:border-red-500' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500 dark:focus:ring-blue-500 dark:focus:border-blue-500',
                    )}/>
                <label className="w-full py-4 ml-2 text-sm font-medium text-gray-900 dark:text-gray-300">{fieldInfo.displayName} {require && <>(<span className="text-destructive">*</span>)</>}</label>

            </div>
        </div>
        
    )
}

export default CustomFieldBoolean;
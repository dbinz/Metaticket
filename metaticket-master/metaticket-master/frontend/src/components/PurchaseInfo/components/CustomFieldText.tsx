
import { OrderCustomField, OrderCustomFieldStringConstraints } from "@/data/order";
import { cn } from "@/lib/utils";
import { useEffect, useState } from "react";

const CustomFieldText = ({
    fieldInfo,
    setValue,
}: {
    fieldInfo: OrderCustomField,
    setValue: (value: string) => void
}) => {
    const [require, setRequire] = useState(false)

    useEffect(() => {
        if(fieldInfo.constraints) {
            var constraints = OrderCustomFieldStringConstraints.parse(JSON.parse(fieldInfo.constraints));
            setRequire(constraints.require);
        }
    }, [])

    return (
        <div>
            <label className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">{fieldInfo.displayName} {require && <>(<span className="text-destructive">*</span>)</>}</label>
            <input 
                type="text" 
                className={cn(
                    'bg-gray-50 border text-gray-900 text-sm rounded-lg block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white',
                    fieldInfo.error ? 'bg-red-100 border-red-300 focus:border-red-500 focus:ring-red-500 dark:focus:ring-red-500 dark:focus:border-red-500' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500 dark:focus:ring-blue-500 dark:focus:border-blue-500',
                )}
                onChange={(event) => setValue(event.target.value)}
                value={fieldInfo.value}
            />
            {fieldInfo.error && <p className="mt-2 text-sm font-medium text-red-500">{fieldInfo.errorMessage}</p>}
        </div>
    )
}

export default CustomFieldText;
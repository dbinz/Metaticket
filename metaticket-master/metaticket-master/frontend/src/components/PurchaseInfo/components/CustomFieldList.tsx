import { OrderCustomField, OrderCustomFieldTypeListConstraints, OrderCustomFieldTypeListOptionConstraint } from "@/data/order"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
  } from '@/components/ui/select'
import { useEffect, useState } from "react"
import { cn } from "@/lib/utils"

const CustomFieldList = ({
    fieldInfo,
    setValue
}: {
    fieldInfo: OrderCustomField,
    setValue: (value: string) => void
}) => {
    const [placeHolder, setPlaceHolder] = useState("")
    const [options, setOptions] = useState<OrderCustomFieldTypeListOptionConstraint[]>([]);
    const [require, setRequire] = useState(false);

    useEffect(() => {
        if(fieldInfo.constraints) {
            var constraints = OrderCustomFieldTypeListConstraints.parse(JSON.parse(fieldInfo.constraints))
            setOptions(constraints.options);
            setPlaceHolder(constraints.placeHolder ?? "");
            setRequire(constraints.require)
        }
       
    }, [])

    const onChange = (value: string) => {
        setValue(value);
    }

    return (
        <div>
            <label className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">{fieldInfo.displayName} {require && <>(<span className="text-destructive">*</span>)</>}</label>
            <Select
                
                onValueChange={onChange}
                value={fieldInfo.value}
            >
                <SelectTrigger
                    className={cn(
                        fieldInfo.error ? 'bg-red-100 border-red-300 focus:border-red-500 focus:ring-red-500 dark:focus:ring-red-500 dark:focus:border-red-500' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500 dark:focus:ring-blue-500 dark:focus:border-blue-500',
                    )}
                    // className={cn(
                    //     'bg-gray-50 border text-gray-900 text-sm rounded-lg block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white',
                    //     fieldInfo.error ? 'bg-red-100 border-red-300 focus:border-red-500 focus:ring-red-500 dark:focus:ring-red-500 dark:focus:border-red-500' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500 dark:focus:ring-blue-500 dark:focus:border-blue-500',
                    // )}
                >
                    <SelectValue placeholder={placeHolder}/>
                </SelectTrigger>
                <SelectContent
                >
                    {options.map((option, index) => (
                        <SelectItem key={index} value={option.value}>
                            {option.name}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
        </div>
        
    )
}

export default CustomFieldList;
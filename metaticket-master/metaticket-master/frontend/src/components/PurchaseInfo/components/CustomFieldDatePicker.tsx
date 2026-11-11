import { OrderCustomField, OrderCustomFieldDateConstraints } from "@/data/order";
import { cn } from "@/lib/utils";
import moment from "moment";
import { useEffect, useState } from "react";
import DatePicker from "react-datepicker";

const CustomFieldDatePicker = ({
    fieldInfo,
    setValue
}: {
    fieldInfo: OrderCustomField,
    setValue: (value: string) => void
}) => {

    const [require, setRequire] = useState(false);

    useEffect(() => {
        if(fieldInfo.constraints) {
            var constraints = OrderCustomFieldDateConstraints.parse(JSON.parse(fieldInfo.constraints));
            setRequire(constraints.require);
        }
    }, [])

    const onDateChanged = (date: Date | null) => {
        setValue(moment(date).format("DD/MM/YYYY"));
    }

    return (
        <div className="w-full">
            <label className="block mb-2 text-sm font-medium text-gray-900 dark:text-white">{fieldInfo.displayName} {require && <>(<span className="text-destructive">*</span>)</>}</label>
            <DatePicker 
                dateFormat={"dd/MM/yyyy"}
                showYearDropdown
                dropdownMode="select"
                wrapperClassName="w-full" 
                className={cn(
                    'bg-gray-50 border text-gray-900 text-sm rounded-lg block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white',
                    fieldInfo.error ? 'bg-red-100 border-red-300 focus:border-red-500 focus:ring-red-500 dark:focus:ring-red-500 dark:focus:border-red-500' : 'border-gray-300 focus:border-blue-500 focus:ring-blue-500 dark:focus:ring-blue-500 dark:focus:border-blue-500',
                )}
                // className="mb-1 bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white" 
                value={fieldInfo.value ? moment(fieldInfo.value, "DD/MM/YYYY").format("DD/MM/YYYY") : undefined} 
                onChange={(date) => onDateChanged(date)} 
            />
        </div>
    )
}

export default CustomFieldDatePicker;
import { OrderCustomField, OrderCustomFieldType } from "@/data/order"
import CustomFieldText from "./CustomFieldText"
import CustomFieldDatePicker from "./CustomFieldDatePicker"
import CustomFieldDataCollector from "./CustomFieldDataCollector"
import CustomFieldList from "./CustomFieldList"
import CustomFieldNumber from "./CustomFieldNumber"
import CustomFieldBoolean from "./CustomFieldBoolean"


const CustomField = ({
    fieldInfo,
    setValue,
    onDataCollectorSucceed
}: {
    fieldInfo: OrderCustomField,
    setValue: (value: string) => void,
    onDataCollectorSucceed: (id: string) => void,
}) => {
    return (
        <div>
            {(function(){
            switch(fieldInfo.type) {
                case OrderCustomFieldType.String:
                    return <CustomFieldText fieldInfo={fieldInfo} setValue={setValue}/>
                case OrderCustomFieldType.Number:
                    return <CustomFieldNumber fieldInfo={fieldInfo} setValue={setValue}/>
                case OrderCustomFieldType.Date:
                    return <CustomFieldDatePicker fieldInfo={fieldInfo} setValue={setValue}/>
                case OrderCustomFieldType.DataCollector:
                    return <CustomFieldDataCollector fieldInfo={fieldInfo} setValue={setValue} onDataCollectorSucceed={onDataCollectorSucceed}/>
                case OrderCustomFieldType.List:
                    return <CustomFieldList fieldInfo={fieldInfo} setValue={setValue}/>
                case OrderCustomFieldType.Boolean:
                    return <CustomFieldBoolean fieldInfo={fieldInfo} setValue={setValue}/>
            }
        }())}
        {((fieldInfo.note?.length ?? 0) > 0) ? (
                <div
                    className="mt-2 italic text-sm" dangerouslySetInnerHTML={{__html: fieldInfo.note!!}}
                >
                </div>
                
            ) : null}
        </div>
    )
    
}

export default CustomField;
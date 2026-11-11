

import { PromotionDetailResponse } from '@/data/order';
import { createContext } from 'react';

interface ContextProps {
    priceTotal: number;
    setPriceTotal: (priceTotal: number) => void;
    promotionDetails: PromotionDetailResponse[];
    setPromotionDetails: (details: PromotionDetailResponse[]) => void;
    promotionCode: string;
    setPromotionCode: (code: string) => void;
}

const PromotionDetailsContext = createContext({} as ContextProps)

export const PromotionDetailsContextProvider = PromotionDetailsContext.Provider
export const PromotionDetailsContextConsumer = PromotionDetailsContext.Consumer

export default PromotionDetailsContext
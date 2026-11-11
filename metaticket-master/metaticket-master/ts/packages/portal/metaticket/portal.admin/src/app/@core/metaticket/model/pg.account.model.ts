/**
 * @author dongcatchay
 */
import { PaymentGatewayType } from './';

export interface PGAccountModel {
    gateway: PaymentGatewayType;
    code: string;
    enabled: boolean;
    name: string;
    description: string;
    returnUrl: string;
    ipnUrl: string;
}

export interface OnePayAccountModel extends PGAccountModel {
    baseUrl: string;
}

export interface NinePayAccountModel extends PGAccountModel {
    baseUrl: string;
}

export interface VNPayAccountModel extends PGAccountModel {
    queryUrl: string;
    payUrl: string;
    expireInMinute: number;
}

export interface PGConfigModel {
    pgAccountDefaultCode: string;
    pgAccount: PGAccountModel[];
}

export interface AddOnePayAccountRequest {
    code: string;
    enabled: boolean;
    name: string;
    description: string;

    baseUrl: string;

    merchantId: string;
    merchantAccessCode: string;
    merchantHashCode: string;

    user: string;
    password: string;
}

export interface UpdateOnePayAccountRequest {
    code: string;
    name?: string;
    description?: string;

    baseUrl?: string;
}

export interface AddNinePayAccountRequest {
    code: string;
    enabled: boolean;
    name: string;
    description: string;

    baseUrl: string;

    merchantKey: string;
    merchantSecretKey: string;
    checksumKey: string;
}

export interface UpdateNinePayAccountRequest {
    code: string;
    name?: string;
    description?: string;

    baseUrl?: string;
}

export interface AddVNPayAccountRequest {
    code: string;
    enabled: boolean;
    name: string;
    description: string;

    payUrl: string;
    queryUrl: string;
    expireInMinute: number;

    tmnCode: string;
    hashSecret: string;
}

export interface UpdateVNPayAccountRequest {
    code: string;
    name?: string;
    description?: string;

    payUrl?: string;
    queryUrl?: string;
    expireInMinute?: number;
}

export function policyDescDocReg(vpId: string) {
    return `policy/desc/Doc/${vpId}`
}

export function policyDescLayerReg(vpId: string, docId: number) {
    return `policy/desc/Layer/${vpId}/${docId}`
}

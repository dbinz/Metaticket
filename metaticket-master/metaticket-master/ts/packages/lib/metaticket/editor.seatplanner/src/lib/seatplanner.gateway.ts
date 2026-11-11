import axios from "axios"
import { SeatPlanDocument } from "./seatplanner.model"

export class SeatPlannerGateway {

    constructor(private backendUri: String) { }

    async createDoc(): Promise<SeatPlanDocument> {
        let resp = await axios.get<SeatPlanDocument>(this.apiPrivateUrl('/createDoc'), {
            withCredentials: true,
            headers: {
                "Content-Type": "application/json"
            }
        })

        return resp.data
    }

    async loadDoc(globalId : string): Promise<SeatPlanDocument> {
        let resp = await axios.get<SeatPlanDocument>(this.apiPrivateUrl(`/loadDoc?globalId=${globalId}`), {
            withCredentials: true,
            headers: {
                'Content-Type': 'application/json',
            },
        });

        return resp.data
    }

    async submitStyle(globalId : string, style : string) : Promise<any> {
        let resp = await axios.post<string>(this.apiPrivateUrl(`/saveDocStyle?globalId=${globalId}`), style, {
            withCredentials: true,
            headers: {
                "Content-Type": "text/plain"
            },
        })

        return resp.data
    }

    async submitDef(globalId : string, def : string) : Promise<any> {
        let resp = await axios.post<string>(this.apiPrivateUrl(`/saveDocDef?globalId=${globalId}`), def, {
            withCredentials: true,
            headers: {
                "Content-Type": "text/plain"
            }
        })

        return resp.data
    }

    async saveDocName(globalId: string, name: string): Promise<any> {
        let resp = await axios.post<string>(this.apiPrivateUrl(`/saveDocName?globalId=${globalId}`), name, {
            withCredentials: true,
            headers: {
                "Content-Type": "text/plain"
            }
        })
        return resp.data
    }

    async saveCategories(globalId: string, categories: string[]): Promise<any> {
        let resp = await axios.post<string>(this.apiPrivateUrl(`/saveCategories?globalId=${globalId}`), JSON.stringify(categories), {
            withCredentials: true,
            headers: {
                "Content-Type": "application/json"
            }
        })
        return resp.data
    }

    async uploadImage(globalId: string, file: File): Promise<{ url: string }> {
        const formData = new FormData();
        formData.append("file", file);

        let resp = await axios.post<{ url: string }>(this.apiPrivateUrl(`/uploadImage?globalId=${globalId}`), formData, {
            withCredentials: true,
            headers: {
                "Content-Type": "multipart/form-data"
            }
        })
        return resp.data
    }

    async updateBaseImgUrl(globalId: string, url: string): Promise<any> {
        let resp = await axios.post<string>(this.apiPrivateUrl(`/updateBaseImgUrl?globalId=${globalId}`), { url: url }, {
            withCredentials: true,
            headers: {
                "Content-Type": "application/json"
            }
        })
        return resp.data
    }

    private apiPrivateUrl(path: string): string {
        return this.backendUri + path
    }
}

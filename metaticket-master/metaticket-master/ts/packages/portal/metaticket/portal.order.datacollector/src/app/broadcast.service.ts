import { Injectable } from "@angular/core";

@Injectable({ providedIn: 'root' })
export class BroadcastService {
    private num: number = 0
    private channel = new BroadcastChannel("data-collector-channel")

    onSaveSuccess() {
        this.channel.postMessage({action: "collect-success", payload: {num: this.num++}})
    }

    onSaveFailed() {
        this.channel.postMessage({action: "collect-fail", payload: {num: this.num ++}})
    }
}

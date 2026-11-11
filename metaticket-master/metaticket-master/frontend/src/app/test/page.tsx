
'use client';

import { Button } from "@/components/ui/button";
import { useEffect, useState } from "react";


export default function Test() {

    const [num, setNum] = useState(0)
    const [broastcastChannel, setBroasdcastChannel] = useState<BroadcastChannel | null>(null);

    useEffect(() => {
        setBroasdcastChannel(new BroadcastChannel('data-collector-channel'));
    }, [])

    const updateNumber = () => {
        setNum(num + 1)
        broastcastChannel?.postMessage({action: "collect-success", payload: {num: num + 1}});
    }

    return (
        <div>
            <Button
                onClick={() => updateNumber()}
            >
                {num}
            </Button>
        </div>
    )
}
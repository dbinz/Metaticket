import { Component, OnInit } from "@angular/core";
import { RouterOutlet } from "@angular/router";
import { initFlowbite } from "flowbite";

@Component( {
    standalone: true,
    selector: "[app-root]",
    template: "<router-outlet></router-outlet>",
    imports: [
        RouterOutlet,
    ]
} )
export class AppComponent implements OnInit {
    title = "Order Data Collector";

    constructor() {}

    ngOnInit(): void {
        initFlowbite()
    }

}

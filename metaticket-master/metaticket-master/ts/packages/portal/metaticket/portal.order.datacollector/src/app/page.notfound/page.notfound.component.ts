import { Component, OnInit } from "@angular/core";
import { AppStateService } from "../app.state.service";

@Component( {
    standalone: true,
    selector: "page-notfound",
    templateUrl: "page.notfound.component.html",
    imports: []
})
export class PageNotfoundComponent implements OnInit {

    constructor(public appS: AppStateService) {}

    ngOnInit(): void {}
}

/*
 * Public API Surface of seatplanner.ui
 */

import { ComponentRef, Injector, ViewContainerRef, createNgModule, runInInjectionContext } from '@angular/core';
import { SeatplannerUiModule } from './lib/seatplanner.ui.module';
import { EditorUIComponent } from '@viclass/editorui.loader';
import { SeatplannerUiComponent } from './lib/seatplanner.ui.component';

export * from './lib/seatplanner.ui.service';
export * from './lib/seatplanner.ui.component';
export * from './lib/seatplanner.ui.module';

export function factory(viewContainerRef : ViewContainerRef, injector : Injector) : Promise<ComponentRef<EditorUIComponent>> {
    let module = createNgModule(SeatplannerUiModule, injector)
    return new Promise( (rs, rj) => {
        let t = runInInjectionContext(module.injector, () => viewContainerRef.createComponent(SeatplannerUiComponent, {
            environmentInjector: module,
            injector: injector,
        }))
        rs(t)
    })
}

import { Cmd, CmdOriginType, CmdProcessor, ViewportManager } from "@viclass/editor.core";
import { SeatPlannerEditor } from "./seatplanner.editor";
import { AbstractCmd, InsertDocCmd, InsertLayerCmd, InsertSeatCmd, RemoveDocCmd, SeatPlannerCmdType, StartItemPreviewCmd, UpdateItemCmd } from "./seatplanner.cmd";
import { SPDocCtrl } from "./seatplanner.doc.ctrl";
import { seatPlannerDocReg } from "./util";
import { SeatPlannerGateway } from "./seatplanner.gateway";

export class SeatPlannerCmdProcessor extends CmdProcessor {

    constructor(private editor : SeatPlannerEditor, private gw : SeatPlannerGateway) {
        super()
    }
    
    override processCmd(cmd: Cmd<any>): Cmd<any> | Promise<Cmd<any>> {
        switch (cmd.meta.cmdType) {
            case SeatPlannerCmdType.INSERT_DOC:
                return this.processInsertDocCmd(cmd as InsertDocCmd)
            case SeatPlannerCmdType.INSERT_LAYER:
                this.processInsertLayerCmd(cmd as InsertLayerCmd)
                break

            case SeatPlannerCmdType.INSERT_SEAT:
                this.processInsertSeats(cmd as InsertSeatCmd)
                break

            case SeatPlannerCmdType.START_ITEM_PREVIEW:
            case SeatPlannerCmdType.UPDATE_ITEM:
            case SeatPlannerCmdType.SAVE_ITEMS:
            case SeatPlannerCmdType.CANCEL_DRAWING:
                this.processItemCmd(cmd as AbstractCmd<any>)
                break
        }

        return cmd
    }

    processItemCmd(cmd: AbstractCmd<any>) {
        let docId = cmd.meta.versionable!
        let vm: ViewportManager = cmd.meta.viewport

        let docRegistry = this.editor.regMan.registry<SPDocCtrl>(seatPlannerDocReg(vm.id))
        let docCtrl = docRegistry.getEntity(docId)

        docCtrl.objCtrlMgr.processCommand(cmd)
    }


    // it is important to not use the async / await keyword here
    // because when it is local command, we expect the command is processed immediately
    private processInsertDocCmd(cmd: InsertDocCmd) : Promise<Cmd<any>> | Cmd<any> {
        let boundary = cmd.state.boundary

        if (cmd.meta.origin == CmdOriginType.local) {
            this.editor.createDocCtrl(cmd.meta.viewport, cmd.localCtrlState!, boundary!, cmd.meta.targetId)
            return cmd 
        } else {
            // need to load the document from remote, because this sync command might not have all the information
            return this.gw.loadDoc(cmd.state.globalId!).then(response =>{
                this.editor.createDocCtrl(cmd.meta.viewport, response, boundary!, cmd.meta.targetId)
                return cmd
            })
        }

    }

    private processInsertSeats(cmd : InsertSeatCmd) {
        let docId = cmd.meta.versionable!
        let vm: ViewportManager = cmd.meta.viewport

        let docRegistry = this.editor.regMan.registry<SPDocCtrl>(seatPlannerDocReg(vm.id))
        let docCtrl = docRegistry.getEntity(docId)

        if (!docCtrl.layer || !cmd.state.seats || cmd.state.seats.length == 0) return

        docCtrl.renderMgr.renderItems(cmd.state.seats)
    }

    private async processRemoveDocCmd(cmd: RemoveDocCmd) {
        await this.editor.removeDoc(cmd.meta.viewport.id, cmd.state.localId!)
    }

    private processInsertLayerCmd(cmd: InsertLayerCmd) {
        let layerId = cmd.meta.targetId
        let docId = cmd.meta.versionable!
        let vm: ViewportManager = cmd.meta.viewport

        let docRegistry = this.editor.regMan.registry<SPDocCtrl>(seatPlannerDocReg(vm.id))
        let docCtrl = docRegistry.getEntity(docId)

        this.editor.createLayerCtrl(vm, docCtrl, layerId)
    }

}
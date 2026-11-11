package metaticket.editor.seatplanner.exception

class DocumentNotFoundException(val docId : String) : RuntimeException("Document id $docId is not found") {}

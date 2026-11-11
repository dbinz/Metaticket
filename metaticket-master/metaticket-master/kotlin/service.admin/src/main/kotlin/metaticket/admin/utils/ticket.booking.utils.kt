package metaticket.admin.utils

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import metaticket.admin.pojo.request.BookingTicketDTO
import metaticket.order.pojo.db.CustomFieldData
import metaticket.order.pojo.db.DeliveryInfo
import org.apache.poi.ss.usermodel.*
import org.slf4j.Logger
import java.io.InputStream

// Define expected column headers for the FILE CONTENT
object TicketBookingHelper {
    const val COL_TICKET_SID = "Ticket SID"
    const val COL_QUANTITY = "Quantity"
    const val COL_PROMOTION = "Promotion Code"
    const val COL_CUSTOMER_NAME = "Customer Name"
    const val COL_CUSTOMER_PHONE = "Customer Phone"
    const val COL_CUSTOMER_EMAIL = "Customer Email"
    const val COL_DELIVERY_NAME = "Delivery Name"
    const val COL_DELIVERY_PHONE = "Delivery Phone"
    const val COL_PROVINCE = "Province"
    const val COL_DISTRICT = "District"
    const val COL_WARD = "Ward"
    const val COL_ADDRESS = "Address"
    const val CUSTOM_FIELD_PREFIX = "CustomField:"

    // Combined allowed extensions
    val ALLOWED_EXTENSIONS = listOf(".csv", ".xlsx", ".xls")
    val EXCEL_EXTENSIONS = listOf(".xlsx", ".xls")
    const val CSV_EXTENSION = ".csv"

    // Required headers for validation
    val REQUIRED_HEADERS = listOf(
        COL_TICKET_SID, COL_QUANTITY, COL_CUSTOMER_NAME, COL_CUSTOMER_PHONE, COL_CUSTOMER_EMAIL
    )

    // --- CSV Parser ---
    fun parseCsvToBookingTicketList(logger: Logger, inputStream: InputStream): Result<List<BookingTicketDTO>> {
        return try {
            // Log the available bytes from the input stream
            val availableBytes = inputStream.available()
            logger.info("Parsing CSV file. Available bytes: $availableBytes")

            // It's useful to wrap the original stream to be able to read it multiple times if needed for debugging
            // But for now, let's just check if it's empty.
            if (availableBytes == 0) {
                logger.warn("CSV input stream is empty before parsing.")
            }

            var headerMap: Map<String, Int>? = null
            val rows = mutableListOf<Pair<Int, List<String?>>>() // Store row index (1-based) and data

            // Use csvReader within a block to manage resources
            csvReader { skipEmptyLine = true }.open(inputStream) {
                // 1. Read Header
                val header = readNext()
                logger.info("Raw CSV header: $header")
                if (header == null || header.all { it.isBlank() }) {
                    throw Exception("CSV file is empty or missing header row.")
                }
                headerMap = header.mapIndexedNotNull { index, name ->
                    name.trim().takeIf { it.isNotEmpty() }?.let { it to index }
                }.toMap()
                logger.info("Processed CSV header map: $headerMap")

                // 2. Read Data Rows
                var rowIndex = 1 // Start from 1 for data rows (header is row 0)
                var rowData: List<String?>?
                while (readNext().also { rowData = it } != null) {
                    if (rowIndex == 1) {
                        logger.info("First CSV data row: $rowData")
                    }
                    // Check if row is effectively empty (all null or blank strings)
                    if (rowData?.all { it.isNullOrBlank() } == true) {
                        logger.debug("Skipping empty CSV row ${rowIndex + 1}")
                    } else {
                        rows.add(rowIndex to rowData!!) // Add non-empty row with its 1-based index
                    }
                    rowIndex++
                }
            } // InputStream is closed here by csvReader's open block

            // 3. Process Rows using common logic
            if (headerMap == null) {
                // Should be caught by header check, but for safety
                return Result.failure(Exception("CSV header could not be processed."))
            }
            processParsedRows(headerMap!!, rows.iterator(), "CSV")

        } catch (e: Throwable) {
            logger.error("Error parsing CSV stream: ${e.message}", e)
            Result.failure(Exception("Failed to read or parse CSV file: ${e.message}", e))
        }
    }

    // --- Excel Parser ---
    fun parseExcelToBookingTicketList(logger: Logger, inputStream: InputStream): Result<List<BookingTicketDTO>> {
        return try {
            // Use WorkbookFactory within a 'use' block for resource management
            WorkbookFactory.create(inputStream).use { workbook ->
                val sheet: Sheet = workbook.getSheetAt(0)

                // 1. Read Header
                val headerRow = sheet.getRow(0)
                    ?: return@use Result.failure(Exception("Excel sheet is empty or missing header row (Row 1)."))

                val headerMap = mutableMapOf<String, Int>()
                var maxColIndex = -1
                headerRow.forEach { cell ->
                    val headerText = getCellStringValue(logger, cell)?.trim()
                    if (!headerText.isNullOrBlank()) {
                        headerMap[headerText] = cell.columnIndex
                        if (cell.columnIndex > maxColIndex) maxColIndex = cell.columnIndex
                    }
                }

                // 2. Read Data Rows
                val rows = mutableListOf<Pair<Int, List<String?>>>() // Store row index (1-based) and data
                for (rowIndex in 1..sheet.lastRowNum) { // POI rows are 0-based, data starts at index 1
                    val currentRow = sheet.getRow(rowIndex)
                    if (currentRow == null || isRowEmpty(logger, currentRow)) {
                        logger.debug("Skipping empty Excel row ${rowIndex + 1}")
                        continue
                    }
                    // Convert row to List<String?> up to the max header index found
                    val rowData = List(maxColIndex + 1) { colIdx ->
                        getCellStringValue(logger, currentRow.getCell(colIdx))
                    }
                    rows.add(rowIndex to rowData) // Add non-empty row with its 0-based index
                }

                // 3. Process Rows using common logic
                processParsedRows(headerMap, rows.iterator(), "Excel")

            } // Workbook and InputStream are closed here by .use block
        } catch (e: Throwable) {
            logger.error("Error parsing Excel stream: ${e.message}", e)
            Result.failure(Exception("Failed to read or parse Excel file: ${e.message}", e))
        }
    }

    // --- Common Row Processing Logic ---
    /**
     * Processes rows of data provided by an iterator, using a header map to extract values.
     * Handles data validation, DTO creation, and error aggregation.
     *
     * @param headerMap Map of column header names to their 0-based index.
     * @param rowIterator Iterator providing pairs of (rowIndex, rowData).
     *                    rowIndex is the 1-based index of the row in the original file (for user-friendly errors).
     *                    rowData is a list of nullable strings representing cell values for that row.
     * @param fileTypeDescription String description of the file type ("CSV" or "Excel") for error messages.
     * @return Result containing the list of parsed BookingTicketDTOs or an Exception with aggregated errors.
     */
    private fun processParsedRows(
        headerMap: Map<String, Int>,
        rowIterator: Iterator<Pair<Int, List<String?>>>,
        fileTypeDescription: String
    ): Result<List<BookingTicketDTO>> {
        val bookingTickets = mutableListOf<BookingTicketDTO>()
        val errors = mutableListOf<String>()

        // 1. Validate Header Presence
        val missingHeaders = REQUIRED_HEADERS.filter { !headerMap.containsKey(it) }
        if (missingHeaders.isNotEmpty()) {
            return Result.failure(Exception("Missing required columns in $fileTypeDescription header: ${missingHeaders.joinToString()}"))
        }

        // 2. Process Data Rows
        while (rowIterator.hasNext()) {
            val (rowIndex, currentRowData) = rowIterator.next()
            val rowNumForError = rowIndex + 1 // User-facing row number (1-based, header is row 1)
            var rowHasError = false

            // Helper to get value safely using headerMap and currentRowData
            fun getVal(colName: String): String? = headerMap[colName]?.let { index ->
                currentRowData.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
            }

            // Extract standard fields
            val sid = getVal(COL_TICKET_SID)
            val quantityStr = getVal(COL_QUANTITY)
            val promotionCode = getVal(COL_PROMOTION)
            val name = getVal(COL_CUSTOMER_NAME)
            val phone = getVal(COL_CUSTOMER_PHONE)
            val email = getVal(COL_CUSTOMER_EMAIL)
            val deliveryName = getVal(COL_DELIVERY_NAME)
            val deliveryPhone = getVal(COL_DELIVERY_PHONE)
            val province = getVal(COL_PROVINCE)
            val district = getVal(COL_DISTRICT)
            val ward = getVal(COL_WARD)
            val address = getVal(COL_ADDRESS)

            // Extract custom fields
            val customFields = mutableListOf<CustomFieldData>()
            headerMap.filterKeys { it.startsWith(CUSTOM_FIELD_PREFIX) }.forEach { (header, colIndex) ->
                val key = header.substringAfter(CUSTOM_FIELD_PREFIX).trim()
                val value = currentRowData.getOrNull(colIndex)?.trim()?.takeIf { it.isNotEmpty() } // Allow blank/null values
                if (key.isNotBlank()) {
                    customFields += CustomFieldData(id = key, name = key, value = value)
                }
            }

            // Validate required fields for this row
            if (sid.isNullOrBlank()) { errors.add("Row $rowNumForError: Missing '$COL_TICKET_SID'."); rowHasError = true }
            if (name.isNullOrBlank()) { errors.add("Row $rowNumForError: Missing '$COL_CUSTOMER_NAME'."); rowHasError = true }
            if (phone.isNullOrBlank()) { errors.add("Row $rowNumForError: Missing '$COL_CUSTOMER_PHONE'."); rowHasError = true }
            if (email.isNullOrBlank()) { errors.add("Row $rowNumForError: Missing '$COL_CUSTOMER_EMAIL'."); rowHasError = true }

            val quantity = quantityStr?.toIntOrNull()
            if (quantity == null || quantity <= 0) {
                errors.add("Row $rowNumForError: Invalid or missing '$COL_QUANTITY' (must be a positive number). Found: '$quantityStr'"); rowHasError = true
            }

            // If no errors for this row, create the DTO
            if (!rowHasError && quantity != null && sid != null && name != null && phone != null && email != null) {
                val deliveryInfo = if (listOfNotNull(deliveryName, deliveryPhone, province, district, ward, address).any { it.isNotBlank() }) {
                    // Only create DeliveryInfo if at least one delivery field is present
                    DeliveryInfo(
                        deliveryName = deliveryName ?: "",
                        deliveryPhone = deliveryPhone ?: "",
                        provinceName = province ?: "",
                        districtName = district ?: "",
                        wardName = ward ?: "",
                        address = address ?: ""
                    )
                } else {
                    null // No delivery info provided
                }

                bookingTickets.add(
                    BookingTicketDTO(
                        sid = sid,
                        quantity = quantity,
                        promotionCode = promotionCode,
                        name = name,
                        phone = phone,
                        email = email,
                        delivery = deliveryInfo,
                        customFields = customFields.ifEmpty { null }
                    )
                )
            }
        } // End while loop

        // 3. Return Result
        return if (errors.isNotEmpty()) {
            Result.failure(Exception("Errors found during $fileTypeDescription parsing:\n${errors.joinToString("\n")}"))
        } else {
            Result.success(bookingTickets)
        }
    }


    // --- POI Helper Functions ---
    private fun getCellStringValue(logger: Logger, cell: Cell?): String? {
        return when (cell?.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.localDateTimeCellValue.toString()
                } else {
                    val number = cell.numericCellValue
                    // Handle integers cleanly
                    if (number == Math.floor(number) && !number.isInfinite()) {
                        number.toLong().toString()
                    } else {
                        number.toString()
                    }
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> try { // Attempt to get cached string value first
                cell.stringCellValue.trim()
            } catch (e: Exception) { // If fails, try numeric
                try {
                    val number = cell.numericCellValue
                    if (number == Math.floor(number) && !number.isInfinite()) number.toLong().toString() else number.toString()
                } catch (e2: Exception) {
                    // Log with context if possible, but user IDs aren't directly available here.
                    // The caller (processParsedRows) will log the overall failure.
                    logger.warn("Could not evaluate formula cell at row ${cell.rowIndex}, col ${cell.columnIndex}: ${e.message} / ${e2.message}")
                    "ERROR_IN_FORMULA" // Indicate error
                }
            }
            CellType.BLANK -> null
            null -> null
            else -> cell.toString().trim() // Fallback for other types like ERROR
        }?.takeIf { it.isNotEmpty() } // Treat empty strings as null
    }

    private fun isRowEmpty(logger: Logger, row: Row): Boolean {
        if (row.physicalNumberOfCells == 0) return true
        // Iterate through cells checking if any have actual content
        for (cellNum in row.firstCellNum until row.lastCellNum) {
            val cell = row.getCell(cellNum, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)
            // Use getCellStringValue to consistently check for non-blank content
            if (cell != null && cell.cellType != CellType.BLANK && !getCellStringValue(logger, cell).isNullOrBlank()) {
                return false // Found a non-empty cell
            }
        }
        return true // All cells were effectively empty
    }

}
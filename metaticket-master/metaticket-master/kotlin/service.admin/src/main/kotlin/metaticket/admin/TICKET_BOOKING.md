# Guide: Creating Input Files for the Bulk Ticket Booking API (`bookingTicketFromFile`)

This document provides instructions on how to prepare CSV (Comma Separated Values) or Excel (`.xlsx`, `.xls`) files for
use with the `bookingTicketFromFile` API endpoint to perform bulk ticket bookings. While both formats are accepted, this
guide focuses primarily on the structure and requirements, using CSV examples for clarity.

## Supported File Formats

* **CSV:** `.csv` (Comma Separated Values)
* **Excel:** `.xlsx` (Excel Workbook), `.xls` (Excel 97-2003 Workbook)

## General File Requirements (CSV Focus)

* **Encoding:** Files should be saved using **UTF-8 encoding** to ensure proper handling of all characters, especially
  in names or addresses.
* **Delimiter (CSV):** CSV files must use a **comma (`,`)** as the delimiter between values.
* **Header Row:** The **first row** of the file **must** be a header row containing the column names. The API uses this
  row to identify the data in subsequent rows.

# Guide: Creating Input Files for the Bulk Ticket Booking API (`bookingTicketFromFile`)

This document provides instructions on how to prepare CSV (Comma Separated Values) or Excel (`.xlsx`, `.xls`) files for
use with the `bookingTicketFromFile` API endpoint to perform bulk ticket bookings. While both formats are accepted, this
guide focuses primarily on the structure and requirements, using CSV examples for clarity.

## Supported File Formats

* **CSV:** `.csv` (Comma Separated Values)
* **Excel:** `.xlsx` (Excel Workbook), `.xls` (Excel 97-2003 Workbook)

## General File Requirements (CSV Focus)

* **Encoding:** Files should be saved using **UTF-8 encoding** to ensure proper handling of all characters, especially
  in names or addresses.
* **Delimiter (CSV):** CSV files must use a **comma (`,`)** as the delimiter between values.
* **Header Row:** The **first row** of the file **must** be a header row containing the column names. The API uses this
  row to identify the data in subsequent rows.
  Header names are **case-sensitive** and must match the definitions below exactly.
* **Empty Rows:** Empty rows within the data will be skipped during processing.

## File Structure

1. **Header Row:** The first row defines the columns. The order of columns doesn't strictly matter, but
   including all necessary headers is crucial.
2. **Data Rows:** Each subsequent row represents a single booking request line item for a specific ticket type (
   identified by `Ticket SID`).

### Required Columns

These columns **must** be present in the header row and contain valid, non-blank data for each data row:

* `Ticket SID`
* `Quantity`
* `Customer Name`
* `Customer Phone`
* `Customer Email`

### Optional Columns

These columns can be included if the corresponding information is needed:

* `Promotion Code`
* `Delivery Name`
* `Delivery Phone`
* `Province`
* `District`
* `Ward`
* `Address`

**Important Note on Delivery Information:**

* If you intend to provide delivery information for a booking row, **all** of the delivery columns (`Delivery Name`,
  `Delivery Phone`, `Province`, `District`, `Ward`, `Address`) **must** be present in the header and contain **non-blank
  values** for that specific row.
* If **any** delivery field is provided for a row but **not all** of them, the API will treat this as an **error**
  for that row during the order creation step.
* If **none** of the delivery fields are provided (or they are all blank) for a row, delivery information will simply be
  omitted for that booking.

### Custom Field Columns

You can include additional custom fields required for the order. These columns must follow a specific naming convention
in the header:

* **Format:** `CustomField:<YourFieldName>`
    * Replace `<YourFieldName>` with the actual **ID** or **Key** of the custom field (e.g., `CustomField:MembershipID`,
      `CustomField:DietaryPreference`).
    * The part after `CustomField:` will be used as the `id` and `name` for the custom field data sent to the order
      service.
* **Example Headers:** `CustomField:Company Name`, `CustomField:TShirtSize`
* **Value:** The cell value in the data row for this column will be stored as the custom field's value. Blank values are
  allowed.

## Column Details Table

| Header Name         | Required | Data Type        | Description                                                                        | Notes                                                                                                                                          |
|:--------------------|:--------:|:-----------------|:-----------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------|
| `Ticket SID`        | **Yes**  | String           | The unique Sales Identifier (SID) for the specific ticket type being booked.       | Must correspond to a valid, available ticket SID for the given Show and Session. Must not be blank.                                            |
| `Quantity`          | **Yes**  | Positive Integer | The number of tickets of this specific SID to book in this row's request.          | Must be a whole number greater than 0. Must not be blank.                                                                                      |
| `Promotion Code`    |    No    | String           | An optional promotion or discount code to apply to this specific booking line.     | Leave blank if no promotion code applies.                                                                                                      |
| `Customer Name`     | **Yes**  | String           | The full name of the primary customer for this booking.                            | Must not be blank.                                                                                                                             |
| `Customer Phone`    | **Yes**  | String           | The primary contact phone number for the customer                                  | Should be a valid phone number format. Must not be blank.                                                                                      |
| `Customer Email`    | **Yes**  | String           | The primary contact email address for the customer.                                | Should be a valid email format. Must not be blank.                                                                                             |
| `Delivery Name`     |    No    | String           | The name of the person receiving the tickets (if delivery is required/applicable). | \*See "Important Note on Delivery Information" above. If provided, all other delivery fields must also be provided and non-blank for this row. |
| `Delivery Phone`    |    No    | String           | The contact phone number for the delivery recipient.                               | \*See "Important Note on Delivery Information" above. If provided, all other delivery fields must also be provided and non-blank for this row. |
| `Province`          |    No    | String           | The province/state for the delivery address.                                       | \*See "Important Note on Delivery Information" above. If provided, all other delivery fields must also be provided and non-blank for this row. |
| `District`          |    No    | String           | The district/county/city for the delivery address.                                 | \*See "Important Note on Delivery Information" above. If provided, all other delivery fields must also be provided and non-blank for this row. |
| `Ward`              |    No    | String           | The ward/sub-district/town for the delivery address.                               | \*See "Important Note on Delivery Information" above. If provided, all other delivery fields must also be provided and non-blank for this row. |
| `Address`           |    No    | String           | The street address details for delivery.                                           | \*See "Important Note on Delivery Information" above. If provided, all other delivery fields must also be provided and non-blank for this row. |
| `CustomField:<Key>` |    No    | String           | The value for the custom field identified by `<Key>`.                              | Add one column for each custom field needed. The `<Key>` must match the configured custom field ID. The value can be blank.                    |

## Example Data Table
This table shows the example CSV data formatted for clarity:

| Ticket SID    | Quantity | Customer Name       | Customer Phone | Customer Email      | Promotion Code | Delivery Name   | Delivery Phone | Province         | District   | Ward            | Address         | CustomField:Company | CustomField:ReferenceNo |
|---------------|----------|---------------------|----------------|---------------------|----------------|-----------------|----------------|------------------|------------|-----------------|-----------------|---------------------|-----------------------|
| SID-GA-101    | 2        | Alice Wonderland    | 0901234567     | alice@example.com | EARLYBIRD      | Bob The Builder | 0907654321     | Ho Chi Minh City | District 1 | Ben Nghe Ward   | 123 Main St     | Wonderland Inc.     | REF778                |
| SID-VIP-202   | 1        | Charlie Chaplin     | 0911112222     | charlie@example.com |                | Charlie Chaplin | 0911112222     | Hanoi            | Hoan Kiem  | Trang Tien Ward | 456 Theatre Ave |                     | REF779                |
| SID-GA-101    | 4        | Diana Prince        | 0988776655     | diana@example.com |                |                 |                |                  |            |                 |                 | Amazonia LLC        |                       |
| SID-STALL-305 | 1        | Edward Scissorhands | 0933445566     | edward@example.com | SAVE10         |                 |                |                  |            |                 |                 | Avon Corp           | REF801                |

**Explanation of Example Data:**

*   **Row 1:** Books 2 "SID-GA-101" tickets for Alice, applies "EARLYBIRD" promo, includes **full** delivery info, and provides "Company" and "ReferenceNo" custom fields.
*   **Row 2:** Books 1 "SID-VIP-202" ticket for Charlie, no promo code, includes **full** delivery info (recipient is same as customer), and provides "ReferenceNo" custom field but leaves "Company" blank.
*   **Row 3:** Books 4 "SID-GA-101" tickets for Diana, no promo code, **no delivery info** provided (all delivery columns are blank), includes "Company" custom field.
*   **Row 4:** Books 1 "SID-STALL-305" ticket for Edward, applies "SAVE10" promo, **no delivery info**, includes "Company" and "ReferenceNo" custom fields.

## Data Validation by API

The API performs the following validations when parsing the file and preparing the bookings:

*   **File Level:**
    *   Checks if a valid file (`.csv`, `.xlsx`, `.xls`) was uploaded.
    *   Checks if the required form fields (`showId`, `sessionId`) were provided.
*   **Header Level:**
    *   Checks for the presence of all required header columns (`Ticket SID`, `Quantity`, `Customer Name`, `Customer Phone`, `Customer Email`). If any are missing, the entire file processing fails.
*   **Row Level (Parsing):**
    *   Ensures required fields (`Ticket SID`, `Customer Name`, `Customer Phone`, `Customer Email`) are not blank for each data row.
    *   Ensures `Quantity` is a positive integer.
    *   Rows with parsing errors (e.g., missing required field, invalid quantity) will be reported, and processing might stop if errors are found.
*   **Row Level (Booking Preparation):**
    *   Verifies that the `Ticket SID` corresponds to a valid ticket for the specified `showId` and `sessionId`.
    *   Checks the delivery information consistency (all or none rule). Providing partial delivery info will cause an error during the order creation step for that specific booking.

Rows with validation errors during parsing will prevent the booking process from starting. Errors encountered during the individual booking steps (like partial delivery info or invalid Ticket SID) will cause that specific booking to fail, potentially resulting in a partial success/failure response for the entire file.

## API Usage

When calling the `POST /api/.../bookingTicketFromFile` endpoint (replace `/api/.../` with the actual path):

1.  Use `multipart/form-data` as the request `Content-Type`.
2.  Include the CSV or Excel file as a file part (the API expects the part name to be `file`).
3.  Include the `showId` as a form field part (e.g., `showId=SHOW123`).
4.  Include the `sessionId` as a form field part (e.g., `sessionId=SESSION456`).

The API will then:
1.  Receive and validate the request parts.
2.  Parse the file (CSV or Excel) based on its extension.
3.  Validate headers and row data according to the rules above.
4.  If parsing and initial validation succeed, attempt to process each valid row concurrently:
    *   Create a ticket reservation.
    *   Create an order linked to the reservation.
    *   Handle potential errors and compensation (e.g., releasing reservation if order fails).
5.  Respond with the overall status (success, failure, or multi-status if some rows succeeded and others failed), including details about successful and failed booking attempts.

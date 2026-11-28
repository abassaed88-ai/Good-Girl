# Good Girl — Approved next development batch

Branch: `hardening/inventory-accounting-20261123`
Base branch: `main` (must remain untouched; no merge without explicit approval)

## Accepted work items

1. Fix the dashboard menu button so it opens its navigation panel.
2. Fix the master-data entry so it opens the intended screen.
3. Investigate and fix Arabic text input composition/display across affected fields, including POS search; avoid field-by-field workarounds if the root cause is shared.
4. Add a dedicated central product list with search and product management.
5. Allow permanent deletion only when safe; archive products with historical transactions, hide them from new transaction selection, and provide restoration.
6. Improve internal/original barcode management and product association.
7. Provide batch barcode label printing: select multiple products, specify copies per product, generate a single PDF.
8. Replace the current POS interaction with a Daily Sales ledger-like table (Excel-style): item, quantity/unit, sale price, profit snapshot, optional customer for credit sale, barcode scan/search, confirm sale, and integration with inventory, customers, reports, and accounting. Avoid duplicate postings.
9. Prepare the user's provided Good Girl logo as the Android launcher icon (remove phone-number strip; preserve logo identity and make adaptive-icon assets).

## Execution and validation rules

- Work only on the development branch; do not modify `main` or merge PR #1 without explicit approval.
- Prioritize reusable existing accounting/inventory repositories; no duplicate business logic.
- Add focused tests for each change and run full unit/instrumentation/build checks before APK delivery.
- Confirm signed release with the permanent signing key and verify compatibility before recommending installation.
- This document records approved scope; it does not claim any item is implemented.


## 10. Approved daily-sales screen — exact reference layout (2026-11-28)

The user's generated reference image is the required visual target for the single daily-sales window. Do not replace it with card-based or abbreviated layouts.

- Header: back navigation, date, title `المبيعات اليومية`, home, and menu controls as in the approved reference.
- Ledger columns, in order: `تسلسل` (narrow), `المادة` (wide), `الكمية / الوحدة`, `المبيع`, `الأرباح`, `العميل (دائن / لأجل)`, `مصروفات`, `ملاحظات`. Column widths must be content-aware and proportional; headers and cells must align.
- Main grid: editable rows, visible grid borders, teal header, light icy-blue background, white cells, horizontal and vertical scrolling, Western numerals, Arabic RTL. Keep a clear row-add action.
- Material-cell interaction: popup/picker with barcode scanning as an action, and text search by material name or numeric product code. Do not add a separate barcode-search field.
- Quantity/unit interaction: quantity editor and only units supported for the selected product, including piece, carton, milliliter, and gram as configured. Packaging conversions are product-specific and snapshotted on transaction lines.
- Sale interaction: enter price and choose SYP or USD; preserve currency and exchange-rate snapshot. Never combine currency totals without a transparent conversion.
- Customer interaction: choose from existing customers or add a customer without leaving the sales screen; optional for cash sales, required when posting an outstanding credit balance.
- Profit column: derived from effective cost and transaction snapshots, not a user-editable arbitrary amount. Expense column and notes must be editable with validation and clear accounting treatment.
- Totals strip: separate totals by currency where applicable, including sales, profit, expenses, and count; never sum SYP and USD together silently.
- Calculator: floating, movable/show-hide, placed so it does not obstruct table cells or input; basic arithmetic, percent, clear and copy result.
- Review/confirm safeguards: validate stock, units, price, currency/rate, customer and expenses; prevent duplicate posting. Posting must update inventory, accounting/journals, customer balances, and reports atomically or leave the draft intact.
- Draft safety: persist pending rows and partially entered current row in Room; restore on screen launch; preserve drafts on failures; clear only after successful posting. Idempotent posting must be tested.
- Visual acceptance: compare the implemented screen against the exact approved reference image. Success in CI or functionality alone is not visual acceptance.

## 11. Units and conversion — approved, not yet claimed implemented

Each product uses a base inventory unit and may have at most one configured packaging unit, either `علبة` or `كرتونة` (never both for the same product). Supported units include piece, milliliter, and gram. A product may be sold using its configured base unit or packaging unit; stock deduction is converted to base quantity using the saved conversion factor. Fractional quantities are allowed where appropriate. Save the selected unit and factor as transaction snapshots so future product configuration changes do not rewrite historical transactions. Apply the same conversion model to purchases, inventory, and sales, with focused conversion/precision/stock-validation tests.

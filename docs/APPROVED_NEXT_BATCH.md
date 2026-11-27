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

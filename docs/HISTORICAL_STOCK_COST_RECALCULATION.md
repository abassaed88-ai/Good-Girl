# Historical stock costing and correction design

Status: approved engineering design for the development branch. This document is not a claim that the feature is implemented.

## 1. Accounting invariants

1. Posted invoice snapshots are immutable. Never rewrite an existing `InvoiceLineEntity.unitCostSnapshot`, line price, invoice total, or original journal lines after posting.
2. A backdated purchase or sale can change the effective weighted-average cost of later stock-out movements. Record the difference explicitly and preserve both the previous and recalculated amounts.
3. The product's current on-hand quantity and average cost must equal a replay of its trusted baseline plus every movement recorded after that baseline.
4. Every journal correction is balanced, currency-specific, source-linked, idempotent, and written in the same Room transaction as its audit records and inventory update.
5. SYP and USD are replayed and posted separately. A currency label correction is an explicit boundary; values are not implicitly converted.
6. If the data does not contain a reliable starting point for a requested historical date, reject the posting with a clear Arabic explanation. Do not estimate a cost from today's balance.

## 2. Trusted cost-history baseline

Add an immutable per-product `stock_cost_baselines` row containing product ID, quantity, average unit cost, currency, effective timestamp, and recording timestamp.

- During the v8-to-v9 migration, snapshot each existing product's current quantity, purchase cost, and currency. Existing movements before this timestamp remain available in the movement history screen but are not replayed for new historical costing.
- For a newly created product, create its baseline in the same transaction as the product and its opening-stock movement.
- A backdated invoice line is accepted only when its issue time is at or after that product's baseline effective time.
- The existing `MIGRATION_RECONCILIATION` rows are absorbed into the migration baseline; they must not be replayed as ordinary purchases or sales.
- This boundary is required because the legacy-to-general-ledger migration carried forward balances as opening journals; it did not reconstruct every pre-migration sale and cost as a posted journal. Replaying earlier sales as if that complete ledger existed would produce false historical profit and inventory balances.

## 3. Stable movement ordering

Add a persisted `recordedAt` value to stock movements. Backdated events retain two distinct times:

- `occurredAt`: the business date selected for the invoice.
- `recordedAt`: the time the event was saved.

Replay order is `occurredAt`, then `recordedAt`, then movement ID as a final deterministic tie-breaker. Movement recording timestamps must be monotonically advanced per product where millisecond collisions would otherwise make two separately posted transactions ambiguous. Rows from the same invoice are inserted in a stable order.

For existing v8 rows, initialize `recordedAt` from the linked invoice's `createdAt` where a stock movement references an invoice line; otherwise initialize it from `occurredAt`.

## 4. Replay and event handling

The replay starts with the immutable baseline and processes only movements recorded after it. It uses decimal arithmetic and weighted-average costing.

- Positive purchases and positive manual/stocktake gains add quantity at their saved incoming unit cost.
- Sale movements and negative manual/stocktake adjustments consume quantity at the weighted-average cost at that point in the timeline.
- `PRODUCT_CURRENCY_CORRECTION` is a zero-quantity currency boundary. It preserves the numeric carrying cost while changing the currency label, matching the existing product-correction behavior; it does not silently apply an exchange-rate conversion.
- Unknown post-baseline movement types, negative chronological balances, unsupported currencies, or a movement before the product baseline cause the transaction to fail rather than be skipped.
- The replay returns the effective unit cost for every stock-out movement and the ending quantity, average cost, and currency.

## 5. Audit model

Add two Room tables:

- `stock_cost_recalculation_audits`: one recalculation batch per product and trigger, including trigger source, reason, recorded time, affected range/counts, and old/new ending inventory state.
- `stock_cost_recalculation_items`: one row for each stock-out movement whose effective cost changed, including movement/source identifiers, associated invoice/line when applicable, currency, quantity, old/new unit cost, and old/new extended cost.

For a movement, its effective cost is its original saved snapshot when no audit item exists, otherwise the newest completed audit item's recalculated cost. Each recalculation compares against that effective value, not against the original snapshot, so repeating the same correction cannot post the same difference twice.

Audit rows are append-only. A later correction creates another batch and another item; it does not overwrite the earlier audit.

## 6. Journal and ledger corrections

For each affected posted sale invoice, group changed lines by invoice and currency, then post one source-keyed adjustment journal:

- If effective COGS increases: debit cost of goods sold (SYS-5000), credit inventory (SYS-1200).
- If effective COGS decreases: debit inventory (SYS-1200), credit cost of goods sold (SYS-5000).

The adjustment journal's `occurredAt` is the original invoice's `issuedAt`, so the invoice profitability report and general-ledger income statement describe the same business period. Its `createdAt` is the actual correction time, and the audit record explains when and why it was changed. Formal period locking is not currently implemented; when period closing is added, this policy must be revisited with an explicit closed-period adjustment policy.

For a negative manual/stocktake movement whose carrying cost changes, preserve the original journal lines and add a balanced adjustment against the original stock-loss expense account (SYS-7000) and inventory (SYS-1200). Its source ledger amount may be updated to the newly effective expense only if the old and new values are recorded in the audit item; the journal correction remains a separate immutable entry.

New sale invoices use the replayed effective cost as their initial immutable line snapshot before the original invoice journal is posted. They must not receive a second adjustment for their own initial cost.

All adjustment source IDs include the recalculation batch and affected source document/movement, preventing duplicate posting while permitting later independent corrections.

## 7. Atomicity, reporting, and backups

One `Room.withTransaction` must contain: validation, invoice/movement insertion, replay, product quantity/average-cost update, audit batch/items, ledger correction where applicable, original invoice journal, and all adjustment journals. Any validation or posting failure rolls the entire operation back.

The invoice/product profitability report must use the latest effective cost for each sale line while retaining the original line snapshot. General-ledger statements consume the adjustment journals. Tests must reconcile the two reports by invoice and currency. Cash-flow totals should remain unchanged by non-cash cost adjustments.

Baselines and all audit tables must be included in encrypted backup export, validation, and restore. Restore must preserve the baseline boundary and correction history atomically.

## 8. Required test matrix before adding invoice date pickers

1. Backdated purchase between two sales changes only later effective sale costs and posts the exact balanced difference.
2. Backdated sale changes the cost of later sales and rejects a chronological negative stock balance.
3. Two or more products in one invoice; repeated product lines; same-millisecond events; stable movement ordering.
4. Repeated recalculation with no new changes produces no duplicate amount or journal.
5. Multiple corrections to the same line compare against the latest effective cost and retain all earlier audit rows.
6. Backdated changes before the baseline, before product creation, or across a missing-history boundary are rejected with rollback.
7. Negative manual adjustment and stocktake loss cost changes have matching expense/inventory corrections.
8. Currency correction boundaries keep the numeric value unchanged, separate old/new currency journals, and never combine SYP and USD.
9. Migration v8-to-v9 preserves products, movements, balances, accounts, journals, and creates exactly one baseline per product.
10. Encrypted backup/restore preserves baselines and complete audit history.
11. Failure injection at each transactional stage leaves no partial invoice, movement, audit item, ledger update, or journal.
12. Invoice profitability and general-ledger statements reconcile after one or more recalculations.

The sale and purchase date pickers must be added only after this engine and the test matrix pass.
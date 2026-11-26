# Historical stock costing and correction design

Status: historical weighted-average replay remains available for legacy audit interpretation and selected stock-loss corrections, but **new purchase invoice posting no longer invokes this replay**. New purchases set the current product cost to the latest incoming unit cost, revalue the remaining on-hand stock through a separate source-linked journal dated at recording time, and preserve already-posted sale snapshots and COGS journals. Existing legacy audit rows remain intact for historical continuity; the full legacy-data migration and exceptional backdated/closed-period cases still require validation.

## 1. Accounting invariants

1. Posted invoice snapshots are immutable. Never rewrite an existing `InvoiceLineEntity.unitCostSnapshot`, line price, invoice total, or original journal lines after posting.
2. Legacy historical-replay records may explain cost changes created by older app versions. New purchase invoices must not use this replay to alter an already-posted sale or its cost-of-goods-sold journal. A purchase's current-stock revaluation is instead recorded at the time it is actually entered.
3. The product's current on-hand quantity and average cost must equal a replay of its trusted baseline plus every movement recorded after that baseline.
4. Every journal correction is balanced, currency-specific, source-linked, idempotent, and written in the same Room transaction as its audit records and inventory update.
5. SYP and USD are replayed and posted separately. A currency label correction is an explicit boundary; values are not implicitly converted.
6. If the data does not contain a reliable starting point for a requested historical date, reject the posting with a clear Arabic explanation. Do not estimate a cost from today's balance.

## 2. Trusted cost-history baseline

Persist an immutable per-product `stock_cost_baselines` row containing product ID, quantity, average unit cost, currency, effective timestamp, recording timestamp, and a coverage/source marker.

- The usable accounting-history boundary is the general-ledger opening migration (v5-to-v6), not the new v8-to-v9 schema migration. The v5-to-v6 migration records balanced `MIGRATION_OPENING` journals and snapshots the current inventory value; transactions before that opening are not individually reconstructed in the general ledger.
- During v8-to-v9, attempt to reconstruct each existing product's quantity, weighted-average cost, and currency immediately after that opening boundary by reversing every stock movement recorded after it, in reverse chronological order. A successful reconstruction must pass a forward replay and exactly reconcile to the current product quantity, currency, and carrying cost (with a documented decimal tolerance only where the existing eight-decimal weighted-average rounding requires it).
- Movements recorded before the opening boundary are already represented in the opening balances and must not be replayed or re-journaled. A movement recorded after the boundary but carrying a business date before it makes reconstruction unsafe and triggers fallback for that product. A future-dated movement recorded before the boundary also triggers fallback, because it is already included in the opening snapshot and cannot safely be separated into the post-opening timeline.
- If reverse reconstruction, currency-boundary recovery, or forward reconciliation fails, create a per-product fallback baseline from the current saved product balance at v9 migration time. Such a product accepts only future/new transactions dated at or after that fallback boundary; older backdated transactions are rejected with a clear Arabic explanation. Do not fabricate earlier stock or profit.
- For a product created after the accounting opening boundary, create a zero-quantity baseline immediately before its opening-stock event, then replay that event as the first stock movement. A product created with zero opening quantity still receives a baseline.
- Existing `MIGRATION_RECONCILIATION` movements created by the v4-to-v5 migration are part of the pre-ledger opening position. Keep them for historical display, but do not mistake them for purchases or replay them after the general-ledger opening boundary.
- This coverage rule keeps backdated entries available across the period for which source-based journal accounting exists, while protecting earlier legacy periods represented only by opening balances.

## 3. Stable movement ordering

Persist a separate `recordedAt` value on every stock movement. Backdated events retain two distinct times:

- `occurredAt`: the business date selected for the invoice.
- `recordedAt`: the time the event was saved.

Replay order is `occurredAt`, then `recordedAt`, then movement ID as a final deterministic tie-breaker. New movement recording timestamps must be monotonically advanced per product (and in insertion order for repeated product lines within one invoice), so millisecond collisions cannot make separate transactions ambiguous.

For existing v8 rows, initialize `recordedAt` from the linked invoice's `createdAt` where a stock movement references an invoice line; otherwise initialize it from `occurredAt`. During baseline replay, only events with `recordedAt` after the accounting opening boundary are reversed/replayed. A future-dated legacy event recorded before the boundary is already included in the opening snapshot and must not be counted twice.

## 4. Replay and event handling

The legacy replay starts with the immutable baseline and processes only movements recorded after it. It uses decimal arithmetic and weighted-average costing. This legacy algorithm is no longer called by the new purchase-posting path.

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

For a negative manual/stocktake movement whose carrying cost changes, the implementation preserves the original journal lines, updates the source expense ledger row to the newly effective amount, and adds a balanced source-keyed adjustment against stock-loss expense (SYS-7000) and inventory (SYS-1200). The append-only audit item records both old and new amounts. The correction is transactional and is rejected if the original source expense/journal is missing, the source currency does not match, or the movement date belongs to a closed period. A zero-value legacy loss that never created a source ledger/journal is rejected because its original exchange-rate snapshot is unavailable.

New sale invoices use the replayed effective cost as their initial immutable line snapshot before the original invoice journal is posted. They must not receive a second adjustment for their own initial cost.

All adjustment source IDs include the recalculation batch and affected source document/movement, preventing duplicate posting while permitting later independent corrections.

## 7. Atomicity, reporting, and backups

One `Room.withTransaction` must contain: validation, invoice/movement insertion, replay, product quantity/average-cost update, audit batch/items, ledger correction where applicable, original invoice journal, and all adjustment journals. Any validation or posting failure rolls the entire operation back.

The invoice/product profitability report must use the latest effective cost for each sale line while retaining the original line snapshot. General-ledger statements consume the adjustment journals. Tests must reconcile the two reports by invoice and currency. Cash-flow totals should remain unchanged by non-cash cost adjustments.

Room v9 baselines and movement recording timestamps are included in encrypted backup export, integrity validation, and transactional restore. Older backups receive an explicitly marked `LEGACY_BACKUP_FALLBACK` baseline at a conservative restore boundary. Stock-cost baselines, recalculation audits, and item rows are serialized in encrypted backups; validation checks manual/stocktake loss items against their source ledger, original journal, and balanced adjustment journal.

## 8. Required test matrix before adding invoice date pickers

Already covered by automated tests on the current branch: (1) a backdated purchase changes later sale cost and the profitability report reconciles with the general ledger; (7) negative manual adjustment and stocktake loss changes update the effective expense and post balanced inventory/expense corrections; (10) encrypted backup validation accepts a correctly audited non-invoice stock-loss correction and rejects malformed or unmatched records.

Remaining test matrix before enabling full invoice date pickers:
1. Backdated purchase between two sales changes only the later effective sale costs and posts the exact balanced difference, including multiple products and repeated invoice lines.
2. Backdated sale changes later sale costs and rejects a chronological negative stock balance.
3. Multiple stock-loss correction cycles compare against the latest effective item and retain every prior audit row without duplicate adjustments.
4. Same-millisecond events, stable ordering, and multiple currency boundaries remain deterministic.
5. Backdated changes before the baseline, before product creation, or across a missing-history boundary are rejected with full rollback.
6. Currency correction boundaries preserve numeric value, separate old/new currency journals, and never combine SYP and USD.
7. All supported Room migrations and encrypted backup restore preserve baselines, movements, audit history, ledger rows, and adjustment journals.
8. Failure injection at each transaction stage leaves no partial invoice, movement, audit item, ledger update, or journal.
9. Period-closing guards reject adjustments into closed periods without partial writes.
10. Invoice profitability, daily-sales profit, and general-ledger statements reconcile after one or more correction cycles.

The sale and purchase date pickers must be added only after this engine and the test matrix pass.
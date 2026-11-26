# Current purchase-cost policy (Good Girl)

Status: implemented on the development branch; Android integration validation is pending.

## User-approved behavior
- A newly posted purchase invoice establishes the product's **current purchase cost** from the invoice's incoming unit cost (per base unit, after applicable line/invoice discount allocation).
- Immediately after the purchase posts, reprice the **entire remaining on-hand quantity** of that product to this latest cost, including stock that was received earlier.
- Use this latest cost as the current reference for setting future sale prices and for new sale-line cost snapshots.
- Never rewrite posted historical sale invoices, their original line snapshots, or historical profit/cost results already reported for those sales.
- Subsequent purchase invoices repeat this rule: latest purchase cost replaces the current carrying cost for all remaining stock, rather than blending with the prior cost using weighted average.

## Accounting/reporting safeguards required for implementation
- The repricing of on-hand inventory is recorded in a separate, balanced, currency-specific inventory-revaluation journal in the same Room transaction. It is source-linked to the purchase invoice, and its immutable line memos identify the product, previous stock/cost, incoming quantity/value, latest cost and signed revaluation difference. It remains included in encrypted backups.
- The revaluation journal is dated at the actual recording time, rather than the purchase invoice's potentially backdated issue time. This prevents the act of repricing current stock from rewriting earlier-period inventory valuation results.
- Positive revaluation differences use a dedicated inventory-revaluation-gain account; negative differences use a dedicated inventory-revaluation-loss account. They are mirrored as non-cash income/expense ledger rows so the simplified financial report and general-ledger income statement reconcile. These are separate from sales revenue and cost of goods sold, so invoice-based product gross profit remains based on immutable sale snapshots. They appear separately in reports.
- A cost update must not mutate old sale-line snapshots, stock-out movement cost snapshots, historical sale journals or invoice-based historical product profit. Invoice posting no longer invokes the old weighted-average stock-cost replay that could revise previously posted cost-of-goods-sold adjustments.
- If inventory quantity is zero, update the product's current purchase-cost reference from the purchase without generating a zero-stock carrying-value adjustment.
- If the invoice includes repeated lines for one product, calculate the product's latest incoming cost deterministically and apply the policy once to that product within the transaction.
- Sale unit costs for future posted sales use the latest current product cost, captured as an immutable snapshot at sale posting.
- SYP and USD remain separate. Reject a purchase currency incompatible with the current stock unless a supported, explicit currency correction flow is used.
- Backdated purchase invoices, historical cost replay, stock-loss costing, currency corrections, closed periods and the general-ledger/inventory reconciliation require a dedicated review before implementation; do not assume the old weighted-average replay model remains suitable.
- Tests must cover a prior sale at old cost, remaining stock revaluation on new purchase, a new sale at the latest cost, unchanged prior invoice/report cost and profit, journal balancing, idempotency/rollback, repeated purchase changes, zero stock, multi-line invoice, currency and backup/restore.

## Example
Existing 30 ml at $1/ml, previously sold 20 ml from an initial 50 ml. A new purchase of 20 ml at $1.50/ml is posted:
- Existing posted sale stays at $1/ml historical cost.
- The 30 ml on hand before purchase is revalued to $1.50/ml.
- The incoming 20 ml is recorded at $1.50/ml.
- Current stock is 50 ml, all carried at $1.50/ml.
- New sale snapshots use $1.50/ml (until a later purchase changes current cost).
- Previous sale invoice, profit and reports remain unchanged.

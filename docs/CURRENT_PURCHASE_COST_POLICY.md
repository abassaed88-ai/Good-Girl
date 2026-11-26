# Current purchase-cost policy (Good Girl)

Status: approved product policy; implementation and integration tests are pending.

## User-approved behavior
- A newly posted purchase invoice establishes the product's **current purchase cost** from the invoice's incoming unit cost (per base unit, after applicable line/invoice discount allocation).
- Immediately after the purchase posts, reprice the **entire remaining on-hand quantity** of that product to this latest cost, including stock that was received earlier.
- Use this latest cost as the current reference for setting future sale prices and for new sale-line cost snapshots.
- Never rewrite posted historical sale invoices, their original line snapshots, or historical profit/cost results already reported for those sales.
- Subsequent purchase invoices repeat this rule: latest purchase cost replaces the current carrying cost for all remaining stock, rather than blending with the prior cost using weighted average.

## Accounting/reporting safeguards required for implementation
- The repricing of on-hand inventory must be accompanied by a balanced, source-linked, currency-specific inventory valuation adjustment journal and audit record, in the same Room transaction as purchase posting, inventory update, movement/audit records and invoice journals.
- A cost update must not mutate old sale-line snapshots. Existing effective-cost audit/replay mechanisms must be evaluated and changed carefully so they do not silently rewrite historic sale profit under this policy.
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
